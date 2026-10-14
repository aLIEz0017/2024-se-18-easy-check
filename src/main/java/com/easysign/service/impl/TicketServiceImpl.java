package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easysign.common.BizException;
import com.easysign.dto.request.CheckinRequest;
import com.easysign.dto.request.PurchaseRequest;
import com.easysign.dto.response.PageResponse;
import com.easysign.dto.response.TicketResponse;
import com.easysign.entity.Event;
import com.easysign.entity.Seat;
import com.easysign.entity.Ticket;
import com.easysign.entity.User;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.EventStockMapper;
import com.easysign.mapper.SeatMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.mapper.UserMapper;
import com.easysign.queue.PurchaseQueueConsumer;
import com.easysign.queue.PurchaseRecord;
import com.easysign.ratelimit.PurchaseRateLimiter;
import com.easysign.service.TicketService;
import com.easysign.util.CheckinCodeParser;
import com.easysign.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 票务 Service 实现
 * <p>
 * 包含抢票（支持防超卖、选座、重复购票检测）、签到凭证解析与校验等功能。
 * 使用 MyBatis-Plus 配合自定义 XML 映射文件完成联表查询。
 * </p>
 */
@Service
public class TicketServiceImpl implements TicketService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PurchaseRateLimiter purchaseRateLimiter;

    @Autowired
    private SeatWebSocketHandler seatWebSocketHandler;

    // ======================== 5.1 抢票 ========================

    @Override
    @Transactional
    public TicketResponse purchase(Long userId, PurchaseRequest req) {
        // 1. 查询活动是否存在
        Event event = eventMapper.selectById(req.getEventId());
        if (event == null) throw new BizException(4003, "活动不存在");

        // 2. 校验活动状态
        if ("offline".equals(event.getStatus()))
            throw new BizException(4100, "活动已下线，暂不可购票");
        if ("finished".equals(event.getStatus()))
            throw new BizException(4100, "活动已结束，暂不可购票");
        if ("draft".equals(event.getStatus()))
            throw new BizException(4100, "活动未上线，暂不可购票");

        // 3. 重复购票检查（逻辑校验）
        Long count = ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getEventId, req.getEventId())
                        .eq(Ticket::getUserId, userId));
        if (count > 0) throw new BizException(4004, "您已购买过该活动的门票，请勿重复购买");

        // 4. 【选座】若活动开启选座，需原子占用座位
        Long seatId = null;
        String seatLabel = null;
        if (Boolean.TRUE.equals(event.getHasSeats())) {
            if (req.getSeatId() == null) {
                throw new BizException(4000, "该活动需要选座，请选择座位");
            }
            // 原子占用座位
            int occupied = seatMapper.occupySeat(req.getSeatId(), req.getEventId());
            if (occupied == 0) {
                throw new BizException(4100, "座位已被占用或不存在");
            }
            seatId = req.getSeatId();
            // 查询座位标签
            Seat seat = seatMapper.selectById(seatId);
            if (seat != null) {
                seatLabel = seat.getSeatLabel();
            }
            // 广播座位状态变更
            seatWebSocketHandler.broadcastSeatSold(req.getEventId(), seatId);
        }

        // 5. 独立库存表原子扣减（防超卖，行锁从 event 主表转移到 event_stock 表）
        int affected = eventStockMapper.decreaseStock(req.getEventId());
        if (affected == 0) {
            // 回滚：释放已占用的座位
            if (seatId != null) {
                seatMapper.releaseSeat(seatId, req.getEventId());
            }
            throw new BizException(4100, "票已售罄");
        }

        // 6. 事务提交后异步落单（将 insert ticket 从主事务中剥离）
        //    注意：afterCommit 中事务已提交，无法自动回滚；
        //    enqueue 失败时立即手动补偿（释放座位、恢复库存、广播座位可用）
        final Long capturedSeatId = seatId;
        final Long capturedEventId = req.getEventId();
        final Long capturedUserId = userId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean queued = PurchaseQueueConsumer.enqueue(new PurchaseRecord(capturedUserId, capturedEventId, capturedSeatId));
                if (!queued) {
                    // 【关键补偿】队列已满，记录落单失败
                    // 手动释放资源：恢复库存 + 释放座位
                    log.error("[补偿] 异步落单队列已满！将补偿恢复：userId={}, eventId={}, seatId={}",
                            capturedUserId, capturedEventId, capturedSeatId);

                    // 恢复库存计数
                    eventStockMapper.increaseStock(capturedEventId);

                    // 释放已占用的座位
                    if (capturedSeatId != null) {
                        seatMapper.releaseSeat(capturedSeatId, capturedEventId);
                        // 广播座位状态变回 available
                        seatWebSocketHandler.broadcastSeatAvailable(capturedEventId, capturedSeatId);
                    }
                }
            }
        });

        // 7. 构建响应（此时 ticket 尚未入库，返回临时信息）
        String ticketNo = req.getEventId() + "_" + userId + "_" + (System.currentTimeMillis() / 1000);
        TicketResponse resp = new TicketResponse();
        resp.setTicketId(null);
        resp.setTicketNo(ticketNo);
        resp.setEventTitle(event.getTitle());
        resp.setStatus("pending");
        resp.setSeatId(seatId);
        resp.setSeatLabel(seatLabel);
        return resp;
    }

    // ======================== 5.2 我的票夹 ========================

    @Override
    public PageResponse<TicketResponse> getMyTickets(Long userId, String status, int page, int size) {
        Page<TicketResponse> pageObj = new Page<>(page, size);
        IPage<TicketResponse> result = ticketMapper.selectMyTicketsWithEvent(pageObj, userId, status);

        PageResponse<TicketResponse> pageResp = new PageResponse<>();
        pageResp.setRecords(result.getRecords());
        pageResp.setTotal(result.getTotal());
        pageResp.setPage(page);
        pageResp.setSize(size);
        return pageResp;
    }

    // ======================== 5.3 单张票详情 ========================

    @Override
    public TicketResponse getTicketById(Long userId, Long ticketId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) throw new BizException(4003, "票务不存在");
        if (!ticket.getUserId().equals(userId)) throw new BizException(4002, "权限不足");

        Event event = eventMapper.selectById(ticket.getEventId());

        TicketResponse resp = new TicketResponse();
        resp.setTicketId(ticket.getId());
        resp.setTicketNo(ticket.getTicketNo());
        resp.setStatus(ticket.getStatus());
        resp.setSeatId(ticket.getSeatId());
        if (event != null) {
            resp.setEventTitle(event.getTitle());
            resp.setEventStatus(event.getStatus());
        }
        // 查询座位标签
        if (ticket.getSeatId() != null) {
            Seat seat = seatMapper.selectById(ticket.getSeatId());
            if (seat != null) {
                resp.setSeatLabel(seat.getSeatLabel());
            }
        }
        return resp;
    }

    // ======================== 5.4 签到 ========================

    @Override
    @Transactional
    public TicketResponse checkin(Long userId, CheckinRequest req) {
        String code = req.getCode();

        // —— Step 1: 解析 code ——
        CheckinCodeParser.ParseResult parsed = CheckinCodeParser.parse(code);
        if (parsed == null) {
            throw new BizException(4101, "无效的签到凭证，格式不合法");
        }

        // —— Step 2: 校验时间戳（完整格式）（60秒内）——
        if (parsed.isFullFormat()) {
            if (!CheckinCodeParser.isTimestampValid(parsed.getTimestamp(), 60)) {
                throw new BizException(4102, "二维码已过期，请刷新");
            }
        }

        String ticketNo = parsed.getTicketNo();

        // —— Step 3: 查询 ticket ——
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getTicketNo, ticketNo));
        if (ticket == null) throw new BizException(4103, "票务不存在");

        // —— Step 3a: 校验 eventId 匹配（仅完整格式）——
        if (parsed.getEventId() != null && !parsed.getEventId().equals(ticket.getEventId())) {
            throw new BizException(4101, "无效的签到凭证，票务与活动不匹配");
        }

        // —— Step 3b: 校验签到者是否为该活动的举办方 ——
        Event event = eventMapper.selectById(ticket.getEventId());
        if (event == null) throw new BizException(4003, "关联活动不存在");
        if (!event.getOrganizerId().equals(userId)) {
            throw new BizException(4002, "权限不足，您不是该活动的举办方");
        }

        // —— Step 3c: 校验活动是否已结束 ——
        if ("finished".equals(event.getStatus())) {
            throw new BizException(4107, "活动已结束，无法签到");
        }

        // —— Step 4: 检查 ticket 状态 ——
        String ticketStatus = ticket.getStatus();
        if ("checked".equals(ticketStatus)) throw new BizException(4104, "请勿重复签到");
        if ("invalid".equals(ticketStatus)) throw new BizException(4105, "票务已失效");
        if ("refunded".equals(ticketStatus)) throw new BizException(4106, "票务已退票");

        // —— Step 5: 原子更新签到 ——
        int affected = ticketMapper.updateCheckin(ticket.getId());
        if (affected == 0) throw new BizException(4104, "请勿重复签到");

        // 构建响应
        User attendee = userMapper.selectById(ticket.getUserId());
        TicketResponse resp = new TicketResponse();
        resp.setTicketId(ticket.getId());
        resp.setTicketNo(ticketNo);
        resp.setEventTitle(event.getTitle());
        resp.setUsername(attendee == null ? null : attendee.getUsername());
        resp.setCheckedAt(LocalDateTime.now().format(DTF));
        return resp;
    }

    // ======================== 5.5 签到明细 ========================

    @Override
    public PageResponse<TicketResponse> getCheckins(Long userId, Long eventId, String status, int page, int size) {
        // 1. 校验 event 存在且当前 user 是发布者
        Event event = eventMapper.selectById(eventId);
        if (event == null) throw new BizException(4003, "活动不存在");
        if (!event.getOrganizerId().equals(userId)) throw new BizException(4002, "权限不足");

        // 2. 分页查询
        Page<TicketResponse> pageObj = new Page<>(page, size);
        IPage<TicketResponse> result = ticketMapper.selectCheckinsWithUser(pageObj, eventId, status);

        PageResponse<TicketResponse> pageResp = new PageResponse<>();
        pageResp.setRecords(result.getRecords());
        pageResp.setTotal(result.getTotal());
        pageResp.setPage(page);
        pageResp.setSize(size);
        return pageResp;
    }
}