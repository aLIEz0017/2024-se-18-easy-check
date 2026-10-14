package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easysign.ai.AiService;
import com.easysign.common.BizException;
import com.easysign.dto.request.ChangeEventStatusRequest;
import com.easysign.dto.request.CreateEventRequest;
import com.easysign.dto.request.UpdateEventRequest;
import com.easysign.dto.response.CreateEventResponse;
import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;
import com.easysign.entity.Event;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.EventStockMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.service.EventService;
import com.easysign.service.SeatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动管理 Service 实现
 * <p>
 * 包含活动的 CRUD、状态变更校验及 AI 文案生成。使用 MyBatis-Plus 的 {@link Page}
 * 实现分页查询，结合 {@link EventMapper} 和 {@link TicketMapper} 完成数据持久化。
 * </p>
 */
@Service
public class EventServiceImpl implements EventService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 合法的活动状态流转映射表 */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put("draft", Set.of("online"));
        VALID_TRANSITIONS.put("online", Set.of("offline"));
        VALID_TRANSITIONS.put("offline", Set.of("online"));
    }

    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private SeatService seatService;

    @Autowired
    private AiService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    // ======================== 时间工具方法 ========================

    /**
     * 将字符串格式 "yyyy-MM-dd HH:mm:ss" 解析为 {@link LocalDateTime}。
     *
     * @throws BizException(4000) 格式不合法时
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DTF);
        } catch (DateTimeParseException e) {
            throw new BizException(4000, "时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss 格式");
        }
    }

    /** 校验开始时间不能早于当前时间 */
    private void validateStartTime(LocalDateTime startTime) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new BizException(4000, "开始时间不能早于当前时间");
        }
    }

    /** 校验结束时间不能早于（或等于）开始时间（endTime 为 null 时跳过） */
    private void validateEndTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new BizException(4000, "结束时间不能早于开始时间");
        }
    }

    // ======================== Event → EventResponse 转换 ========================

    /** 将数据库实体 {@link Event} 转换为响应 DTO，时间字段格式化为字符串 */
    private EventResponse toEventResponse(Event event) {
        if (event == null) return null;
        EventResponse resp = new EventResponse();
        BeanUtils.copyProperties(event, resp);
        resp.setEventId(event.getId());
        resp.setStartTime(event.getStartTime() != null ? event.getStartTime().format(DTF) : null);
        resp.setEndTime(event.getEndTime() != null ? event.getEndTime().format(DTF) : null);
        resp.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt().format(DTF) : null);
        resp.setUpdatedAt(event.getUpdatedAt() != null ? event.getUpdatedAt().format(DTF) : null);
        // 将 DB 中 JSON 字符串类型的 tags 反序列化为 List<String>
        resp.setTags(parseTags(event.getTags()));
        // 新增：同步 hasSeats 字段
        resp.setHasSeats(event.getHasSeats());
        return resp;
    }

    /** 将 DB 存储的 JSON 字符串转换为 List<String>（null/空字符串返回空列表） */
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /** 将 List<String> 序列化为 JSON 字符串（null 返回 null） */
    private String toTagsJson(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new BizException(4000, "标签格式错误");
        }
    }

    // ======================== 4.1 发布活动 ========================

    @Override
    @Transactional
    public CreateEventResponse createEvent(Long userId, CreateEventRequest req) {
        // 1. startTime > now
        LocalDateTime startTime = parseDateTime(req.getStartTime());
        validateStartTime(startTime);

        // 2. endTime == null || endTime > startTime
        LocalDateTime endTime = req.getEndTime() != null ? parseDateTime(req.getEndTime()) : null;
        validateEndTime(startTime, endTime);

        // 3. totalTickets 在 [1,99999]（@Min / @Max 已校验，此处为双重保障）
        if (req.getTotalTickets() < 1 || req.getTotalTickets() > 99999) {
            throw new BizException(4000, "门票总数必须在 1~99999 之间");
        }

        // 4. 插入 event
        Event event = new Event();
        event.setOrganizerId(userId);
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setLocation(req.getLocation());
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setTotalTickets(req.getTotalTickets());
        event.setSoldTickets(0);
        event.setStatus("draft");
        event.setHasSeats(req.getHasSeats() != null && req.getHasSeats());
        event.setTags(toTagsJson(req.getTags()));
        eventMapper.insert(event);

        // 【新增】若开启选座，批量初始化固定模板座位（10×10）
        if (Boolean.TRUE.equals(event.getHasSeats())) {
            seatService.initSeats(event.getId());
        }

        return new CreateEventResponse(event.getId(), "draft");
    }

    // ======================== 4.2 公开列表 ========================

    @Override
    public PageResponse<EventResponse> listPublicEvents(int page, int size, String keyword) {
        Page<Event> pageObj = new Page<>(page, size);
        QueryWrapper<Event> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "online");

        // 关键词模糊搜索（按活动标题）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("title", keyword.trim());
        }

        wrapper.orderByAsc("start_time");

        Page<Event> eventPage = eventMapper.selectPage(pageObj, wrapper);

        List<EventResponse> records = eventPage.getRecords().stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());

        // 公开列表不需要 updatedAt
        records.forEach(r -> r.setUpdatedAt(null));

        return buildPageResponse(records, eventPage, page, size);
    }

    // ======================== 4.2 举办方列表 ========================

    @Override
    public PageResponse<EventResponse> listOrganizerEvents(Long userId, int page, int size) {
        Page<Event> pageObj = new Page<>(page, size);
        QueryWrapper<Event> wrapper = new QueryWrapper<>();
        wrapper.eq("organizer_id", userId)
               .orderByDesc("updated_at");

        Page<Event> eventPage = eventMapper.selectPage(pageObj, wrapper);

        List<EventResponse> records = eventPage.getRecords().stream()
                .map(this::toEventResponse)
                .collect(Collectors.toList());

        return buildPageResponse(records, eventPage, page, size);
    }

    // ======================== 4.3 活动详情 ========================

    @Override
    public EventResponse getEventById(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(4003, "活动不存在");
        }
        return toEventResponse(event);
    }

    // ======================== 4.4 更新活动 ========================

    @Override
    @Transactional
    public void updateEvent(Long userId, Long eventId, UpdateEventRequest req) {
        // 1. 校验 event 存在且当前用户是发布者
        Event event = eventMapper.selectById(eventId);
        if (event == null) throw new BizException(4003, "活动不存在");
        if (!event.getOrganizerId().equals(userId)) throw new BizException(4002, "权限不足");
        if ("finished".equals(event.getStatus())) throw new BizException(4100, "活动已结束，不可修改");

        // 2. 活动未结束时允许修改 totalTickets，但必须满足接口范围且不能低于已售数量
        if (req.getTotalTickets() != null
                && (req.getTotalTickets() < 1 || req.getTotalTickets() > 99999)) {
            throw new BizException(4000, "门票总数必须在 1~99999 之间");
        }
        if (req.getTotalTickets() != null && req.getTotalTickets() < event.getSoldTickets()) {
            throw new BizException(4000, "门票总数不能小于已售数量");
        }

        // 3. startTime / endTime 跨字段比较
        LocalDateTime newStartTime = event.getStartTime();
        LocalDateTime newEndTime = event.getEndTime();

        if (req.getStartTime() != null) {
            newStartTime = parseDateTime(req.getStartTime());
            validateStartTime(newStartTime);
        }
        if (req.getEndTime() != null) {
            newEndTime = parseDateTime(req.getEndTime());
        }
        validateEndTime(newStartTime, newEndTime);

        // 4. 逐个字段更新（只更新非 null 字段）
        if (req.getTitle() != null) event.setTitle(req.getTitle());
        if (req.getDescription() != null) event.setDescription(req.getDescription());
        if (req.getLocation() != null) event.setLocation(req.getLocation());
        if (req.getStartTime() != null) event.setStartTime(newStartTime);
        if (req.getEndTime() != null) event.setEndTime(newEndTime);
        if (req.getTotalTickets() != null) event.setTotalTickets(req.getTotalTickets());
        if (req.getTags() != null) event.setTags(toTagsJson(req.getTags()));

        // 5. 【新增】hasSeats 开关变更处理
        Boolean oldHasSeats = event.getHasSeats();
        Boolean newHasSeats = req.getHasSeats();

        if (newHasSeats != null && !newHasSeats.equals(oldHasSeats)) {
            // online 状态不可修改 hasSeats
            if ("online".equals(event.getStatus())) {
                throw new BizException(4100, "活动已上线，不可修改选座开关");
            }

            if (Boolean.TRUE.equals(oldHasSeats) && Boolean.FALSE.equals(newHasSeats)) {
                // 关闭选座：校验无已售座位
                if (seatService.hasSoldSeats(eventId)) {
                    throw new BizException(4100, "已有用户选座，不可关闭选座功能");
                }
                seatService.deleteSeats(eventId);
            } else if (Boolean.FALSE.equals(oldHasSeats) && Boolean.TRUE.equals(newHasSeats)) {
                // 开启选座：初始化座位
                seatService.initSeats(eventId);
            }

            event.setHasSeats(newHasSeats);
        }

        eventMapper.updateById(event);
    }

    // ======================== 4.5 变更状态 ========================

    @Override
    @Transactional
    public void changeStatus(Long userId, Long eventId, ChangeEventStatusRequest req) {
        // 1. 校验 event 存在且是发布者
        Event event = eventMapper.selectById(eventId);
        if (event == null) throw new BizException(4003, "活动不存在");
        if (!event.getOrganizerId().equals(userId)) throw new BizException(4002, "权限不足");

        String currentStatus = event.getStatus();
        String targetStatus = req.getStatus();

        // 2. 当前不是 finished
        if ("finished".equals(currentStatus)) {
            throw new BizException(4100, "活动已结束，无法变更状态");
        }

        // 3. 目标不是 finished
        if ("finished".equals(targetStatus)) {
            throw new BizException(4000, "不允许手动变更为 finished 状态");
        }

        // 4. 状态流转表校验
        Set<String> allowedTargets = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            throw new BizException(4100, "当前状态不允许变更为 " + targetStatus);
        }

        // 5. 上线（draft→online 或 offline→online）时初始化库存
        if ("online".equals(targetStatus)) {
            eventStockMapper.initStock(eventId, event.getTotalTickets());
        }

        // 6. offline→online 时额外校验 totalTickets ≥ soldTickets（已有已售票的情况）
        if ("offline".equals(currentStatus) && "online".equals(targetStatus)) {
            if (event.getTotalTickets() < event.getSoldTickets()) {
                throw new BizException(4000, "门票总数小于已售数量，无法上线");
            }
        }

        // 7. online→offline 时，将待签到票置为无效
        if ("online".equals(currentStatus) && "offline".equals(targetStatus)) {
            ticketMapper.batchInvalidatePendingByEventId(eventId);
        }

        event.setStatus(targetStatus);
        eventMapper.updateById(event);
    }

    // ======================== 4.6 删除活动 ========================

    @Override
    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        // 1. 校验 event 存在且是发布者
        Event event = eventMapper.selectById(eventId);
        if (event == null) throw new BizException(4003, "活动不存在");
        if (!event.getOrganizerId().equals(userId)) throw new BizException(4002, "权限不足");

        // 2. 当前状态不是 online
        if ("online".equals(event.getStatus())) {
            throw new BizException(4100, "活动已上线，请先下线再删除");
        }

        // 3. 物理删除 event
        eventMapper.deleteById(eventId);

        // 4. 关联 ticket 置为 invalid
        ticketMapper.batchInvalidateByEventId(eventId);
    }

    // ======================== 4.7 AI 一键生成文案（🔒 AI 禁飞区） ========================

    @Override
    public String generateAiDescription(Long eventId, String title) {
        // 若 title 为 null，从 event 记录中读取
        if (title == null || title.isBlank()) {
            Event event = eventMapper.selectById(eventId);
            if (event == null) throw new BizException(4003, "活动不存在");
            title = event.getTitle();
        }
        log.info("当前 AI 实现类: {}", aiService.getClass().getSimpleName());
        return aiService.generateDescription(title);
    }

    // ======================== 分页工具 ========================

    /** 根据 MyBatis-Plus 分页结果构建统一的 {@link PageResponse} */
    private PageResponse<EventResponse> buildPageResponse(List<EventResponse> records, Page<Event> eventPage, int page, int size) {
        PageResponse<EventResponse> pageResp = new PageResponse<>();
        pageResp.setRecords(records);
        pageResp.setTotal(eventPage.getTotal());
        pageResp.setPage(page);
        pageResp.setSize(size);
        return pageResp;
    }
}
