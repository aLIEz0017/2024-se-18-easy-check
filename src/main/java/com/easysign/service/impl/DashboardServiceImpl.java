package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easysign.common.BizCode;
import com.easysign.common.BizException;
import com.easysign.dto.response.DashboardResponse;
import com.easysign.dto.response.DashboardResponse.EventBrief;
import com.easysign.dto.response.EventDetailResponse;
import com.easysign.dto.response.PredictionResponse;
import com.easysign.dto.response.TicketResponse;

import com.easysign.entity.Event;
import com.easysign.entity.Ticket;
import com.easysign.entity.User;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.mapper.UserMapper;
import com.easysign.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**

 * Dashboard 服务实现
 * <p>
 * 提供举办方仪表盘的总览统计与参会率预测功能。
 * 统计部分通过 MyBatis-Plus 条件查询实现，无需手写 SQL。
 * 预测率基于数据库统计计算（历史签到率、热度、购票集中度、时段因子）。
 * </p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 活动状态常量
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_ONLINE = "online";
    private static final String STATUS_OFFLINE = "offline";
    private static final String STATUS_FINISHED = "finished";

    // 票务状态常量
    private static final String TICKET_CHECKED = "checked";

    // 时段常量
    private static final String PERIOD_A = "甲";
    private static final String PERIOD_B = "乙";
    private static final String PERIOD_C = "丙";
    private static final String PERIOD_D = "丁";

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public DashboardResponse getOverview(Long userId) {

        // 1. 查询该举办方的所有活动
        List<Event> events = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .eq(Event::getOrganizerId, userId)
        );

        // 2. 汇总统计
        long totalEvents = events.size();
        long onlineCount = events.stream().filter(e -> STATUS_ONLINE.equals(e.getStatus())).count();
        long totalTickets = events.stream().mapToLong(Event::getTotalTickets).sum();
        long soldTickets = events.stream().mapToLong(Event::getSoldTickets).sum();

        // 3. 查询该举办方所有活动的总签到数
        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        long totalCheckins = 0;
        if (!eventIds.isEmpty()) {
            totalCheckins = ticketMapper.selectCount(
                    new LambdaQueryWrapper<Ticket>()
                            .in(Ticket::getEventId, eventIds)
                            .eq(Ticket::getStatus, TICKET_CHECKED)
            );
        }

        // 4. 查询每个活动的签到数（用于 events 列表中的 checkedIn 字段）
        final Map<Long, Long> checkedInMap;
        if (!eventIds.isEmpty()) {
            // 按 event_id 分组统计签到数
            List<Ticket> allTickets = ticketMapper.selectList(
                    new LambdaQueryWrapper<Ticket>()
                            .in(Ticket::getEventId, eventIds)
            );
            checkedInMap = allTickets.stream()
                    .filter(t -> TICKET_CHECKED.equals(t.getStatus()))
                    .collect(Collectors.groupingBy(
                            Ticket::getEventId,
                            Collectors.counting()
                    ));
        } else {
            checkedInMap = new HashMap<>();
        }


        // 5. 将所有活动转换为 EventBrief（不再限制 5 个，按 updatedAt 降序）
        List<EventBrief> briefs = events.stream()
                .sorted((a, b) -> {
                    if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
                    if (a.getUpdatedAt() == null) return 1;
                    if (b.getUpdatedAt() == null) return -1;
                    return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                })
                .map(event -> {
                    EventBrief brief = new EventBrief();
                    brief.setEventId(event.getId());
                    brief.setTitle(event.getTitle());
                    brief.setStartTime(event.getStartTime() != null ? event.getStartTime().format(DTF) : null);
                    brief.setEndTime(event.getEndTime() != null ? event.getEndTime().format(DTF) : null);
                    brief.setLocation(event.getLocation());
                    brief.setStatus(event.getStatus());
                    brief.setSoldTickets(event.getSoldTickets());
                    brief.setTotalTickets(event.getTotalTickets());
                    brief.setCheckedIn(checkedInMap.getOrDefault(event.getId(), 0L).intValue());

                    // 基于数据库统计计算预测到场率（仅对 online/finished 状态的活动计算）
                    if (STATUS_ONLINE.equals(event.getStatus()) || STATUS_FINISHED.equals(event.getStatus())) {
                        double rate = calculateArrivalRate(event);
                        brief.setArrivalRate(rate);
                        brief.setPredictedArrivalRate(rate);
                    }


                    return brief;
                }).collect(Collectors.toList());

        // 6. 图表数据 — 对齐接口设计文档 6.1 节规范
        Map<String, Object> charts = new LinkedHashMap<>();

        // 6a. saleRatio: 各活动售票比例饼图
        List<Map<String, Object>> saleRatioData = briefs.stream()
                .filter(b -> b.getSoldTickets() != null && b.getSoldTickets() > 0)
                .map(b -> Map.<String, Object>of(
                        "name", b.getTitle(),
                        "value", b.getSoldTickets()
                ))
                .collect(Collectors.toList());
        charts.put("saleRatio", Map.of("type", "pie", "data", saleRatioData));

        // 6b. weeklyTrend: 近7天签到趋势折线图
        Map<String, Object> weeklyTrendData = buildWeeklyTrend(eventIds);
        charts.put("weeklyTrend", Map.of("type", "line", "data", weeklyTrendData));

        // 6b-2. weeklyOperations: 近7天活动创建、售票、签到趋势
        Map<String, Object> weeklyOperationsData = buildWeeklyOperations(events, eventIds);
        charts.put("weeklyOperations", Map.of("type", "line", "data", weeklyOperationsData));

        // 6c. genderRatio: 男女比例饼图
        List<Map<String, Object>> genderRatioData = buildGenderRatio(eventIds);
        charts.put("genderRatio", Map.of("type", "pie", "data", genderRatioData));

        // 7. 组装响应 — 对齐接口设计文档 6.1 节规范
        DashboardResponse resp = new DashboardResponse();
        resp.setHasOnlineEvent(onlineCount > 0);
        resp.setTotalSoldTickets(soldTickets);
        resp.setTotalCheckedIn(totalCheckins);
        resp.setEventCount(totalEvents);
        resp.setEvents(briefs);
        resp.setCharts(charts);

        return resp;
    }

    /**
     * 构建最近七天运营趋势：活动数按活动开始时间、售票数按购票时间、签到数按签到时间统计。
     */
    private Map<String, Object> buildWeeklyOperations(List<Event> events, List<Long> eventIds) {
        List<Ticket> tickets = eventIds == null || eventIds.isEmpty()
                ? Collections.emptyList()
                : ticketMapper.selectList(new LambdaQueryWrapper<Ticket>().in(Ticket::getEventId, eventIds));

        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        List<String> dates = new ArrayList<>();
        List<Integer> eventValues = new ArrayList<>();
        List<Integer> saleValues = new ArrayList<>();
        List<Integer> checkinValues = new ArrayList<>();

        for (int offset = 6; offset >= 0; offset--) {
            LocalDateTime dayStart = todayStart.minusDays(offset);
            LocalDateTime dayEnd = dayStart.plusDays(1);
            dates.add(dayStart.format(DateTimeFormatter.ofPattern("MM-dd")));

            eventValues.add((int) events.stream()
                    .filter(event -> event.getStartTime() != null
                            && !event.getStartTime().isBefore(dayStart)
                            && event.getStartTime().isBefore(dayEnd))
                    .count());
            saleValues.add((int) tickets.stream()
                    .filter(ticket -> ticket.getCreatedAt() != null
                            && !ticket.getCreatedAt().isBefore(dayStart)
                            && ticket.getCreatedAt().isBefore(dayEnd)
                            && !"refunded".equals(ticket.getStatus()))
                    .count());
            checkinValues.add((int) tickets.stream()
                    .filter(ticket -> ticket.getCheckedAt() != null
                            && !ticket.getCheckedAt().isBefore(dayStart)
                            && ticket.getCheckedAt().isBefore(dayEnd))
                    .count());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("events", eventValues);
        result.put("sales", saleValues);
        result.put("checkins", checkinValues);
        return result;
    }

    @Override
    public PredictionResponse getArrivalRate(Long userId, Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(BizCode.NOT_FOUND);
        }
        if (!Objects.equals(event.getOrganizerId(), userId)) {
            throw new BizException(BizCode.FORBIDDEN);
        }

        PredictionFactors factors = calculatePredictionFactors(event);
        double rate = roundRate(factors.predictedRate());
        PredictionResponse resp = new PredictionResponse();
        resp.setEventId(eventId);
        resp.setEventTitle(event.getTitle());
        resp.setArrivalRate(rate);
        resp.setPredictedArrivalRate(rate);
        resp.setHistoricalCheckinRate(roundRate(factors.historicalCheckinRate()));
        resp.setPopularity(roundRate(factors.popularity()));
        resp.setPurchaseConcentration(roundRate(factors.purchaseConcentration()));
        resp.setTimeFactor(roundRate(factors.timeFactor()));
        resp.setReason(buildPredictionReason(event, factors));
        resp.setSuggestion(buildPredictionSuggestion(event, factors));
        return resp;

    }

    @Override
    public EventDetailResponse getEventDetail(Long userId, Long eventId) {
        // 1. 查询活动并校验存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(BizCode.NOT_FOUND);
        }

        // 2. 校验当前用户是该活动的发布者
        if (!event.getOrganizerId().equals(userId)) {
            throw new BizException(BizCode.FORBIDDEN);
        }

        // 3. 查询该活动的签到数
        long checkedIn = ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getEventId, eventId)
                        .eq(Ticket::getStatus, TICKET_CHECKED)
        );

        // 4. 构建图表数据
        Map<String, Object> charts = new LinkedHashMap<>();

        int totalTickets = event.getTotalTickets() != null ? event.getTotalTickets() : 0;
        int soldTickets = event.getSoldTickets() != null ? event.getSoldTickets() : 0;

        List<Map<String, Object>> saleRatioData = new ArrayList<>();
        saleRatioData.add(Map.of("name", "已售票", "value", soldTickets));
        saleRatioData.add(Map.of("name", "未售票", "value", Math.max(totalTickets - soldTickets, 0)));
        charts.put("saleRatio", Map.of("type", "pie", "data", saleRatioData));

        List<Map<String, Object>> checkinRatioData = new ArrayList<>();
        checkinRatioData.add(Map.of("name", "已签到", "value", checkedIn));
        checkinRatioData.add(Map.of("name", "未签到", "value", Math.max(soldTickets - checkedIn, 0)));
        charts.put("checkinRatio", Map.of("type", "pie", "data", checkinRatioData));

        // 4a. weeklyTrend: 近7天签到趋势折线图
        Map<String, Object> weeklyTrendData = buildWeeklyTrend(List.of(eventId));
        charts.put("weeklyTrend", Map.of("type", "line", "data", weeklyTrendData));

        // 4b. genderRatio: 男女比例饼图
        List<Map<String, Object>> genderRatioData = buildGenderRatio(List.of(eventId));
        charts.put("genderRatio", Map.of("type", "pie", "data", genderRatioData));

        IPage<TicketResponse> checkinPage = ticketMapper.selectCheckinsWithUser(
                new Page<>(1, Math.max(checkedIn, 1)),
                eventId,
                TICKET_CHECKED
        );
        List<Map<String, Object>> checkinDetails = checkinPage.getRecords().stream()
                .sorted(Comparator.comparing(TicketResponse::getTicketNo, Comparator.nullsLast(String::compareTo)))
                .map(ticket -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("ticketNo", ticket.getTicketNo());
                    detail.put("username", ticket.getUsername());
                    detail.put("checkedAt", ticket.getCheckedAt());
                    return detail;
                })
                .collect(Collectors.toList());

        // 5. 组装响应
        EventDetailResponse resp = new EventDetailResponse();
        resp.setEventId(event.getId());
        resp.setTitle(event.getTitle());
        resp.setStartTime(event.getStartTime() != null ? event.getStartTime().format(DTF) : null);
        resp.setEndTime(event.getEndTime() != null ? event.getEndTime().format(DTF) : null);
        resp.setLocation(event.getLocation());
        resp.setStatus(event.getStatus());
        resp.setTotalTickets(event.getTotalTickets());
        resp.setSoldTickets(event.getSoldTickets());
        resp.setCheckedIn((int) checkedIn);
        resp.setCharts(charts);
        resp.setCheckinDetails(checkinDetails);

        return resp;
    }


    // ========================================================================
    // 预测率计算方法
    // 预测率 = 历史签到率 x 0.50 + 热度 x 0.20 + 购票集中度 x 0.20 + 时段因子 x 0.10
    // ========================================================================

    private double calculateArrivalRate(Event event) {
        return calculatePredictionFactors(event).predictedRate();
    }

    private PredictionFactors calculatePredictionFactors(Event event) {
        Long eventId = event.getId();
        int totalTickets = event.getTotalTickets() != null ? event.getTotalTickets() : 0;
        int soldTickets = event.getSoldTickets() != null ? event.getSoldTickets() : 0;

        double historicalCheckinRate = calculateHistoricalCheckinRate(event.getOrganizerId());
        double popularity = totalTickets > 0 ? (double) soldTickets / totalTickets : 0.0;
        double concentration = calculateConcentration(eventId, soldTickets);
        double timeFactor = getTimeFactor(event.getStartTime());

        double rate = historicalCheckinRate * 0.50
                + popularity * 0.20
                + concentration * 0.20
                + timeFactor * 0.10;

        return new PredictionFactors(
                historicalCheckinRate,
                popularity,
                concentration,
                timeFactor,
                Math.max(0.0, Math.min(1.0, rate))
        );
    }

    /**
     * 计算历史签到率 = 该举办方所有已结束活动的总签到数 / 总已售票数
     */
    private double calculateHistoricalCheckinRate(Long organizerId) {
        List<Event> finishedEvents = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .eq(Event::getOrganizerId, organizerId)
                        .eq(Event::getStatus, STATUS_FINISHED)
        );

        if (finishedEvents.isEmpty()) {
            return 0.0;
        }

        long totalFinishedSoldTickets = finishedEvents.stream()
                .mapToLong(e -> e.getSoldTickets() != null ? e.getSoldTickets() : 0)
                .sum();

        if (totalFinishedSoldTickets == 0) {
            return 0.0;
        }

        List<Long> finishedEventIds = finishedEvents.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        long checkedCount = ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .in(Ticket::getEventId, finishedEventIds)
                        .eq(Ticket::getStatus, TICKET_CHECKED)
        );

        return Math.min(1.0, (double) checkedCount / totalFinishedSoldTickets);
    }

    private String buildPredictionReason(Event event, PredictionFactors factors) {
        String period = classifyPeriod(event.getStartTime());
        return String.format(Locale.ROOT,
                "历史活动签到率 %.0f%%、当前售票率 %.0f%%、购票集中度因子 %.0f%%、活动开始时段为%s（时段因子 %.0f%%）。按 50%%、20%%、20%%、10%% 的权重综合计算，预测到达率为 %.0f%%。",
                factors.historicalCheckinRate() * 100,
                factors.popularity() * 100,
                factors.purchaseConcentration() * 100,
                describePeriod(period),
                factors.timeFactor() * 100,
                factors.predictedRate() * 100);
    }

    private String buildPredictionSuggestion(Event event, PredictionFactors factors) {
        int soldTickets = event.getSoldTickets() != null ? event.getSoldTickets() : 0;
        int expectedArrivals = (int) Math.round(soldTickets * factors.predictedRate());
        List<String> suggestions = new ArrayList<>();

        if (soldTickets == 0) {
            suggestions.add("当前还没有售票数据，建议先进行活动推广，产生购票记录后再观察预测变化");
        } else if (factors.predictedRate() >= 0.80) {
            suggestions.add("预计约有 " + expectedArrivals + " 人到场，建议提前 30 分钟开放入场并准备充足的签到通道");
        } else if (factors.predictedRate() >= 0.60) {
            suggestions.add("预计约有 " + expectedArrivals + " 人到场，建议在活动前一天和开始前 2 小时分别发送提醒");
        } else {
            suggestions.add("预计约有 " + expectedArrivals + " 人到场，建议加强活动提醒，并向已购票用户再次确认行程");
        }

        if (factors.popularity() < 0.30) {
            suggestions.add("当前售票率偏低，可增加站内曝光或定向邀请");
        }
        if (factors.timeFactor() <= 0.30) {
            suggestions.add("活动位于工作日白天，建议更早发送日程提醒，降低时间冲突带来的缺席风险");
        }
        return String.join("；", suggestions) + "。";
    }

    private String describePeriod(String period) {
        switch (period) {
            case PERIOD_A: return "甲时段（工作日白天）";
            case PERIOD_B: return "乙时段（周一至周四晚上）";
            case PERIOD_C: return "丙时段（周末上午）";
            case PERIOD_D: return "丁时段（周五晚或周末下午、晚上）";
            default: return "未知时段";
        }
    }

    private double roundRate(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record PredictionFactors(
            double historicalCheckinRate,
            double popularity,
            double purchaseConcentration,
            double timeFactor,
            double predictedRate
    ) {}

    /**
     * 计算购票集中度
     * <p>
     * 按甲乙丙丁四个时段分别统计，加权合成总集中度后映射为 0~1 因子。
     * </p>
     */
    private double calculateConcentration(Long eventId, int totalSold) {
        List<Ticket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getEventId, eventId)
                        .isNotNull(Ticket::getCreatedAt)
        );

        if (tickets.isEmpty() || totalSold == 0) {
            return 0.1;
        }

        // 按时段分组
        Map<String, List<Ticket>> periodTickets = new HashMap<>();
        periodTickets.put(PERIOD_A, new ArrayList<>());
        periodTickets.put(PERIOD_B, new ArrayList<>());
        periodTickets.put(PERIOD_C, new ArrayList<>());
        periodTickets.put(PERIOD_D, new ArrayList<>());

        for (Ticket ticket : tickets) {
            String period = classifyPeriod(ticket.getCreatedAt());
            periodTickets.get(period).add(ticket);
        }

        // 甲：覆盖度 = count / days
        int countA = periodTickets.get(PERIOD_A).size();
        long daysA = periodTickets.get(PERIOD_A).stream()
                .map(t -> t.getCreatedAt().toLocalDate())
                .distinct()
                .count();
        double coverageA = daysA > 0 ? (double) countA / daysA : 0.0;

        // 乙：覆盖度 + 峰值集中度
        int countB = periodTickets.get(PERIOD_B).size();
        long daysB = periodTickets.get(PERIOD_B).stream()
                .map(t -> t.getCreatedAt().toLocalDate())
                .distinct()
                .count();
        int peakHourB = calculatePeakHour(periodTickets.get(PERIOD_B));
        double coverageB = daysB > 0 ? (double) countB / daysB : 0.0;
        double peakConcentrationB = totalSold > 0 ? (double) peakHourB / totalSold : 0.0;
        double concentrationB = (coverageB + peakConcentrationB) / 2.0;

        // 丙：覆盖度 + 峰值集中度
        int countC = periodTickets.get(PERIOD_C).size();
        long daysC = periodTickets.get(PERIOD_C).stream()
                .map(t -> t.getCreatedAt().toLocalDate())
                .distinct()
                .count();
        int peakHourC = calculatePeakHour(periodTickets.get(PERIOD_C));
        double coverageC = daysC > 0 ? (double) countC / daysC : 0.0;
        double peakConcentrationC = totalSold > 0 ? (double) peakHourC / totalSold : 0.0;
        double concentrationC = (coverageC + peakConcentrationC) / 2.0;

        // 丁：仅峰值集中度
        int peakHourD = calculatePeakHour(periodTickets.get(PERIOD_D));
        double peakConcentrationD = totalSold > 0 ? (double) peakHourD / totalSold : 0.0;

        // 加权合成总集中度
        double totalConcentration = coverageA * 0.40
                + concentrationB * 0.25
                + concentrationC * 0.20
                + peakConcentrationD * 0.15;

        return mapConcentration(totalConcentration);
    }

    /**
     * 将购票时间分类到甲乙丙丁四个时段
     */
    private String classifyPeriod(LocalDateTime dateTime) {
        if (dateTime == null) {
            return PERIOD_D;
        }

        DayOfWeek dayOfWeek = dateTime.getDayOfWeek();
        LocalTime time = dateTime.toLocalTime();

        // 甲：工作日白天（周一至周五 00:00-17:59）
        if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
                && time.isBefore(LocalTime.of(18, 0))) {
            return PERIOD_A;
        }

        // 乙：工作日晚上不含周五（周一至周四 18:00-23:59）
        if (dayOfWeek.getValue() >= DayOfWeek.MONDAY.getValue()
                && dayOfWeek.getValue() <= DayOfWeek.THURSDAY.getValue()
                && !time.isBefore(LocalTime.of(18, 0))) {
            return PERIOD_B;
        }

        // 丙：周末早上（周六、周日 00:00-12:59）
        if ((dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)
                && time.isBefore(LocalTime.of(13, 0))) {
            return PERIOD_C;
        }

        // 丁：周五 18:00-23:59；周六、周日 13:00-23:59
        return PERIOD_D;
    }

    /**
     * 计算单小时最大售出票数
     */
    private int calculatePeakHour(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return 0;
        }

        Map<Integer, Long> hourCount = tickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().getHour(),
                        Collectors.counting()
                ));

        return hourCount.values().stream()
                .mapToInt(Long::intValue)
                .max()
                .orElse(0);
    }

    /**
     * 将总集中度映射为 0~1 因子（分段映射表）
     */
    private double mapConcentration(double value) {
        if (value >= 0.50) return 1.0;
        if (value >= 0.30) return 0.8;
        if (value >= 0.15) return 0.5;
        if (value >= 0.05) return 0.3;
        return 0.1;
    }

    /**
     * 根据活动开始时间获取时段因子
     */
    private double getTimeFactor(LocalDateTime startTime) {
        if (startTime == null) {
            return 0.3;
        }
        String period = classifyPeriod(startTime);
        switch (period) {
            case PERIOD_A: return 0.3;
            case PERIOD_B: return 0.5;
            case PERIOD_C: return 0.6;
            case PERIOD_D: return 0.9;
            default: return 0.3;
        }
    }

    // ========================================================================
    // 图表数据构建方法
    // ========================================================================

    /**
     * 构建近7天签到趋势数据
     * <p>
     * 统计指定活动集合在最近7天（含今天）内每天的签到总数。
     * 按自然日聚合，返回日期列表和对应的签到数值列表。
     * </p>
     *
     * @param eventIds 活动 ID 列表
     * @return 包含 dates 和 values 的 Map
     */
    private Map<String, Object> buildWeeklyTrend(List<Long> eventIds) {
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        if (eventIds == null || eventIds.isEmpty()) {
            // 无活动时返回空数据
            for (int i = 6; i >= 0; i--) {
                dates.add(LocalDateTime.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd")));
                values.add(0);
            }
            return Map.of("dates", dates, "values", values);
        }

        // 查询最近7天的签到记录
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(6).with(LocalTime.MIN);
        List<Ticket> recentCheckins = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .in(Ticket::getEventId, eventIds)
                        .eq(Ticket::getStatus, TICKET_CHECKED)
                        .ge(Ticket::getCheckedAt, sevenDaysAgo)
        );

        // 按日期分组统计
        Map<String, Long> dailyCount = recentCheckins.stream()
                .filter(t -> t.getCheckedAt() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getCheckedAt().format(DateTimeFormatter.ofPattern("MM-dd")),
                        Collectors.counting()
                ));

        // 填充最近7天数据（含今天）
        for (int i = 6; i >= 0; i--) {
            String dateStr = LocalDateTime.now().minusDays(i).format(DateTimeFormatter.ofPattern("MM-dd"));
            dates.add(dateStr);
            values.add(dailyCount.getOrDefault(dateStr, 0L).intValue());
        }

        return Map.of("dates", dates, "values", values);
    }

    /**
     * 构建男女比例饼图数据
     * <p>
     * 统计所有购票参会者的性别比例（基于 ticket + user 关联）。
     * 仅统计 gender 为 male 或 female 的参会者；历史遗留或空值不进入图表。
     * </p>
     *
     * @param eventIds 活动 ID 列表
     * @return 饼图数据列表，每项包含 name 和 value
     */
    private List<Map<String, Object>> buildGenderRatio(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of(
                    Map.<String, Object>of("name", "男", "value", 0),
                    Map.<String, Object>of("name", "女", "value", 0)
            );
        }

        // 查询这些活动的所有 ticket，关联 user 获取 gender
        List<Ticket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .in(Ticket::getEventId, eventIds)
        );

        if (tickets.isEmpty()) {
            return List.of(
                    Map.<String, Object>of("name", "男", "value", 0),
                    Map.<String, Object>of("name", "女", "value", 0)
            );
        }

        // 获取所有购票用户的 ID
        List<Long> userIds = tickets.stream()
                .map(Ticket::getUserId)
                .distinct()
                .collect(Collectors.toList());

        if (userIds.isEmpty()) {
            return List.of(
                    Map.<String, Object>of("name", "男", "value", 0),
                    Map.<String, Object>of("name", "女", "value", 0)
            );
        }

        // 查询用户信息获取 gender（使用注入的 userMapper）
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, userIds)
        );

        // 统计性别

        long maleCount = users.stream()
                .filter(u -> "male".equals(u.getGender()))
                .count();
        long femaleCount = users.stream()
                .filter(u -> "female".equals(u.getGender()))
                .count();
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Map.<String, Object>of("name", "男", "value", (int) maleCount));
        result.add(Map.<String, Object>of("name", "女", "value", (int) femaleCount));

        return result;
    }
}
