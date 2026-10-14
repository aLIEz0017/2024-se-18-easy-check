package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.common.BizException;
import com.easysign.dto.request.SeatSelectRequest;
import com.easysign.dto.response.SeatMatrixResponse;
import com.easysign.dto.response.SeatResponse;
import java.util.List;
import java.util.stream.Collectors;
import com.easysign.entity.Event;
import com.easysign.entity.Seat;
import com.easysign.entity.Ticket;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.SeatMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.service.SeatService;
import com.easysign.websocket.SeatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * 座位管理 Service 实现
 * <p>
 * 提供座位初始化、查询、删除、选座四大能力。
 * 固定模板：10 行 × 10 列 = 100 座。
 * 选座操作的原子性由 {@link SeatMapper#occupySeat}
 * （UPDATE + WHERE status='available'）保证，无需分布式锁。
 * </p>
 */
@Service
public class SeatServiceImpl implements SeatService {

    /** 默认座位排数 */
    private static final int DEFAULT_ROWS = 10;

    /** 默认座位列数 */
    private static final int DEFAULT_COLS = 10;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private SeatWebSocketHandler seatWebSocketHandler;

    // ======================== 座位初始化 ========================

    @Override
    @Transactional
    public void initSeats(Long eventId) {
        List<Seat> seats = new ArrayList<>(DEFAULT_ROWS * DEFAULT_COLS);
        for (int row = 1; row <= DEFAULT_ROWS; row++) {
            for (int col = 1; col <= DEFAULT_COLS; col++) {
                Seat seat = new Seat();
                seat.setEventId(eventId);
                seat.setSeatRow(row);
                seat.setSeatCol(col);
                seat.setSeatLabel(row + "-" + col);
                seat.setStatus("available");
                seats.add(seat);
            }
        }
        // 批量插入
        seatMapper.insertBatch(seats);
    }

    // ======================== 座位删除 ========================

    @Override
    @Transactional
    public void deleteSeats(Long eventId) {
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getEventId, eventId));
    }

    // ======================== 判断是否有已售座位 ========================

    @Override
    @Transactional(readOnly = true)
    public SeatMatrixResponse getSeatMatrix(Long eventId) {
        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getEventId, eventId)
                        .orderByAsc(Seat::getSeatRow)
                        .orderByAsc(Seat::getSeatCol));

        // 计算行列范围
        int maxRow = 0, maxCol = 0;
        for (Seat s : seats) {
            if (s.getSeatRow() > maxRow) maxRow = s.getSeatRow();
            if (s.getSeatCol() > maxCol) maxCol = s.getSeatCol();
        }

        // 构造 SeatResponse 列表
        List<SeatResponse> seatResponses = seats.stream()
                .map(s -> new SeatResponse(
                        s.getId(),
                        s.getSeatRow(),
                        s.getSeatCol(),
                        s.getSeatLabel(),
                        s.getStatus()))
                .collect(Collectors.toList());

        // 从活动表判断 hasSeats
        Event event = eventMapper.selectById(eventId);
        boolean hasSeats = event != null && Boolean.TRUE.equals(event.getHasSeats());

        return new SeatMatrixResponse(hasSeats, maxRow, maxCol, seatResponses);
    }

    @Override
    public boolean hasSoldSeats(Long eventId) {
        Long count = seatMapper.selectCount(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getEventId, eventId)
                        .eq(Seat::getStatus, "sold"));
        return count != null && count > 0;
    }

    // ======================== 座位查询 ========================

    @Override
    public List<SeatResponse> getSeatsByEventId(Long eventId) {
        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>()
                        .eq(Seat::getEventId, eventId)
                        .orderByAsc(Seat::getSeatRow)
                        .orderByAsc(Seat::getSeatCol));

        return seats.stream()
                .map(s -> new SeatResponse(
                        s.getId(),
                        s.getSeatRow(),
                        s.getSeatCol(),
                        s.getSeatLabel(),
                        s.getStatus()))
                .collect(Collectors.toList());
    }

    // ======================== 原子选座 ========================

    @Override
    @Transactional
    public void selectSeat(Long userId, SeatSelectRequest req) {
        Long eventId = req.getEventId();
        Long seatId = req.getSeatId();

        // 1. 校验活动存在且开启了选座
        Event event = eventMapper.selectById(eventId);
        if (event == null) throw new BizException(4003, "活动不存在");
        if (!Boolean.TRUE.equals(event.getHasSeats()))
            throw new BizException(4100, "该活动未开启选座功能");

        // 2. 校验用户已持有本活动门票
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getEventId, eventId)
                        .eq(Ticket::getUserId, userId));
        if (ticket == null) throw new BizException(4100, "您尚未购买该活动的门票，无法选座");

        // 3. 校验用户尚未选座（一票一座，不可重复选座）
        if (ticket.getSeatId() != null)
            throw new BizException(4004, "您已选择过座位，不可重复选座");

        // 4. 原子占用座位（UPDATE seat SET status='sold' WHERE id=? AND status='available' AND event_id=?）
        int affected = seatMapper.occupySeat(seatId, eventId);
        if (affected == 0) throw new BizException(4100, "该座位已被其他人选走，请刷新后重试");

        // 5. 更新 ticket.seat_id，关联选座结果
        ticket.setSeatId(seatId);
        ticketMapper.updateById(ticket);

        // 6. WebSocket 广播座位状态变更
        seatWebSocketHandler.broadcastSeatSold(eventId, seatId);
    }
}