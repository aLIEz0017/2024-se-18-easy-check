package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easysign.ai.AiService;
import com.easysign.ai.LlmConfig;
import com.easysign.dto.response.EventResponse;
import com.easysign.dto.response.PageResponse;
import com.easysign.entity.Event;
import com.easysign.entity.Ticket;
import com.easysign.entity.User;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.mapper.UserMapper;
import com.easysign.service.RecommendService;
import com.easysign.util.TagUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 推荐 Service 实现 🔒 AI 禁飞区
 * <p>
 * 采用手写加权排序算法（三维度）：
 * <pre>
 * score = w₁ × tagMatchScore + w₂ × sellRate + w₃ × historyScore
 * </pre>
 * tagMatchScore       — 用户偏好标签与活动标签的匹配度（交集 / 用户标签总数）
 * sellRate            — 售票率（已售 / 总票数），归一化热度
 * historyScore        — 用户历史参会标签画像与活动标签的匹配度
 * </p>
 * <p>
 * userId 为 null 或用户无标签时，tagMatchScore 和 historyScore 降为 0，
 * 退化为一维热度排序（仅 sellRate）。
 * </p>
 * <p>
 * AI（LLM）仅用于生成个性化推荐理由文案，作为可选增强层。
 * AI 不可用时降级为固定文案，不参与排序决策。
 * </p>
 */
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 标签匹配度权重 */
    private static final double TAG_WEIGHT = 0.4;

    /** 售票率权重 */
    private static final double SELL_RATE_WEIGHT = 0.3;

    /** 历史参会标签匹配度权重 */
    private static final double HISTORY_WEIGHT = 0.3;

    /** 推荐理由最大长度 */
    private static final int REASON_MAX_LENGTH = 50;

    /** 历史票务查询上限（取最近 N 条） */
    private static final int HISTORY_LIMIT = 30;

    /** 未签到但活动已结束的票，其标签计入历史画像的权重 */
    private static final double PENDING_WEIGHT = 0.3;

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired(required = false)
    private AiService aiService;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired(required = false)
    private LlmConfig llmConfig;

    @Override
    public PageResponse<EventResponse> recommendEvents(Long userId, int page, int size) {
        // 1. 查询所有 online 活动
        QueryWrapper<Event> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "online");
        List<Event> allOnlineEvents = eventMapper.selectList(wrapper);
        long totalOnline = allOnlineEvents.size();

        if (allOnlineEvents.isEmpty()) {
            return new PageResponse<>(Collections.emptyList(), 0, page, size);
        }

        // 2. 获取用户偏好标签列表
        List<String> userTags = getUserTags(userId);

        // 3. 查询用户历史票务记录（最近 HISTORY_LIMIT 条已结束的活动）
        List<Ticket> userTickets = getUserHistoryTickets(userId);

        // 4. 为每个活动计算三维加权评分
        List<ScoredEvent> scoredEvents = allOnlineEvents.stream()
                .map(event -> {
                    double tagMatchScore = computeTagMatchScore(userTags, event.getTags());
                    double sellRate = computeSellRate(event);
                    double historyScore = computeHistoryScore(event.getTags(), userTickets);
                    double score = TAG_WEIGHT * tagMatchScore
                                 + SELL_RATE_WEIGHT * sellRate
                                 + HISTORY_WEIGHT * historyScore;
                    return new ScoredEvent(event, score, tagMatchScore > 0);
                })
                .collect(Collectors.toList());

        // 5. 按评分降序排序，取 Top N
        scoredEvents.sort(Comparator.comparingDouble(ScoredEvent::getScore).reversed());
        List<ScoredEvent> topN = scoredEvents.subList(0, Math.min(size, scoredEvents.size()));

        // 6. 转换为 EventResponse（含 AI 推荐理由）
        List<EventResponse> records = topN.stream()
                .map(se -> toEventResponseWithReason(se.event, se.hasTagMatch, userTags,
                        computeSellRate(se.event), userId))
                .collect(Collectors.toList());

        return new PageResponse<>(records, totalOnline, page, size);
    }

    /**
     * 查询用户的历史票务记录（最近 HISTORY_LIMIT 条，仅关联已结束的活动）。
     * <p>
     * userId 为 null 时返回空列表。
     * </p>
     */
    private List<Ticket> getUserHistoryTickets(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        // 联表查询：ticket JOIN event ON ticket.event_id = event.id
        // 条件：用户票 + 活动状态已结束 (finished / offline)，且只取最近 HISTORY_LIMIT 条
        // 此处直接查 ticket 表，在 computeHistoryScore 中通过 event 判断状态
        // 更优方案：只查活动已结束的票，通过子查询或联表
        // 使用 MyBatis-Plus 的 QueryWrapper 无法直接联表过滤 event.status，
        // 因此先查 ticket 按 userId 过滤 + 时间倒序 LIMIT，再在内存中过滤 event 状态
        QueryWrapper<Ticket> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .orderByDesc("created_at")
          .last("LIMIT " + HISTORY_LIMIT);
        return ticketMapper.selectList(qw);
    }

    /**
     * 获取用户的偏好标签列表。
     * <p>
     * 当 userId 为 null、用户不存在、或用户偏好标签为 null/空时返回空列表。
     * </p>
     */
    private List<String> getUserTags(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Collections.emptyList();
        }
        return TagUtils.parseTags(user.getTags());
    }

    /**
     * 计算用户标签与活动标签的匹配度。
     *
     * @param userTags  用户偏好标签列表（可能为空）
     * @param eventTags 活动标签 JSON 字符串（可能为 null）
     * @return 匹配度 [0, 1]，无标签时返回 0
     */
    private double computeTagMatchScore(List<String> userTags, String eventTags) {
        if (userTags == null || userTags.isEmpty()) {
            return 0.0;
        }
        List<String> eventTagList = TagUtils.parseTags(eventTags);
        if (eventTagList.isEmpty()) {
            return 0.0;
        }
        // 计算交集大小
        Set<String> userTagSet = Set.copyOf(userTags);
        long intersectCount = eventTagList.stream()
                .filter(userTagSet::contains)
                .count();
        return (double) intersectCount / userTags.size();
    }

    /**
     * 计算活动售票率（归一化热度）。
     *
     * @param event 活动实体
     * @return 售票率 [0, 1]，totalTickets 为 0 时返回 0
     */
    private double computeSellRate(Event event) {
        Integer total = event.getTotalTickets();
        Integer sold = event.getSoldTickets();
        if (total == null || total == 0) {
            return 0.0;
        }
        if (sold == null) {
            return 0.0;
        }
        return Math.min(sold * 1.0 / total, 1.0);
    }

    /**
     * 计算候选活动标签与用户历史参会标签画像的匹配度。
     * <p>
     * 构建用户历史标签加权画像：
     * <ul>
     *   <li>已签到 (checked) 的票 → 权重 1.0</li>
     *   <li>已结束但未签到 (pending) 的票 → 权重 0.3</li>
     *   <li>refunded / invalid → 不计入</li>
     * </ul>
     * 只统计已结束 (finished / offline) 活动的标签。
     * </p>
     *
     * @param eventTags   候选活动标签 JSON 字符串
     * @param userTickets 用户历史 ticket 列表
     * @return 历史匹配度 [0, 1]，无历史或标签为空时返回 0
     */
    private double computeHistoryScore(String eventTags, List<Ticket> userTickets) {
        if (userTickets == null || userTickets.isEmpty()) {
            return 0.0;
        }
        List<String> eventTagList = TagUtils.parseTags(eventTags);
        if (eventTagList.isEmpty()) {
            return 0.0;
        }

        // 构建用户历史标签加权画像：Map<标签, 累计权重>
        Map<String, Double> tagProfile = new HashMap<>();
        double totalWeight = 0.0;

        for (Ticket ticket : userTickets) {
            // 跳过 refunded / invalid
            if ("refunded".equals(ticket.getStatus()) || "invalid".equals(ticket.getStatus())) {
                continue;
            }

            // 查询关联的活动，只统计已结束的活动
            Event event = eventMapper.selectById(ticket.getEventId());
            if (event == null) {
                continue;
            }
            String eventStatus = event.getStatus();
            if (!"finished".equals(eventStatus) && !"offline".equals(eventStatus)) {
                // 活动尚未结束，不计入历史画像
                continue;
            }

            // 签到状态决定权重
            double weight;
            if ("checked".equals(ticket.getStatus())) {
                weight = 1.0;
            } else if ("pending".equals(ticket.getStatus())) {
                weight = PENDING_WEIGHT;
            } else {
                continue;
            }

            // 将活动标签计入画像
            List<String> tags = TagUtils.parseTags(event.getTags());
            for (String tag : tags) {
                tagProfile.merge(tag, weight, Double::sum);
                totalWeight += weight;
            }
        }

        if (totalWeight == 0.0) {
            return 0.0;
        }

        // 计算候选活动标签命中的历史加权分
        double hitWeight = 0.0;
        for (String tag : eventTagList) {
            hitWeight += tagProfile.getOrDefault(tag, 0.0);
        }

        return Math.min(hitWeight / totalWeight, 1.0);
    }

    /**
     * 将 Event 实体转换为含推荐原因的 EventResponse。
     * <p>
     * 优先使用 AI 生成个性化推荐理由；AI 不可用或失败时降级为固定文案。
     * </p>
     *
     * @param event       活动实体
     * @param hasTagMatch 是否存在标签匹配（用于决定推荐原因文案）
     * @param userTags    用户偏好标签列表
     * @param sellRate    售票率
     * @param userId      用户 ID
     */
    private EventResponse toEventResponseWithReason(Event event, boolean hasTagMatch,
                                                     List<String> userTags, double sellRate,
                                                     Long userId) {
        EventResponse resp = new EventResponse();
        BeanUtils.copyProperties(event, resp);
        resp.setEventId(event.getId());
        resp.setStartTime(event.getStartTime() != null ? event.getStartTime().format(DTF) : null);
        resp.setEndTime(event.getEndTime() != null ? event.getEndTime().format(DTF) : null);
        resp.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt().format(DTF) : null);
        // 推荐原因 — AI 增强或固定文案
        resp.setReason(generateReason(userId, event, hasTagMatch, userTags, sellRate));
        // 标签字段以 List<String> 形式返回
        resp.setTags(TagUtils.parseTags(event.getTags()));
        return resp;
    }

    /**
     * 生成推荐理由。
     * <p>
     * 优先从缓存获取，未命中则调用 AI 生成。
     * AI 返回 null/空/异常时降级为固定文案。
     * userId 为 null 时不调用 AI，直接返回固定文案。
     * </p>
     */
    private String generateReason(Long userId, Event event, boolean hasTagMatch,
                                   List<String> userTags, double sellRate) {
        if (userId == null) {
            return hasTagMatch ? "根据你的偏好标签推荐" : "热门活动推荐";
        }

        // 判断是否启用 AI
        boolean aiEnabled = llmConfig != null && llmConfig.isEnabled() && aiService != null;

        if (!aiEnabled) {
            return hasTagMatch ? "根据你的偏好标签推荐" : "热门活动推荐";
        }

        // 缓存检查
        Cache cache = cacheManager != null ? cacheManager.getCache("recommendReason") : null;
        String cacheKey = userId + ":" + event.getId();
        if (cache != null) {
            String cached = cache.get(cacheKey, String.class);
            if (cached != null) {
                return cached;
            }
        }

        try {
            String userTagsJson = TagUtils.toTagsJson(userTags);
            String aiReason = aiService.generateRecommendReason(
                    userTagsJson, event.getTitle(), event.getTags(),
                    event.getDescription(), sellRate, hasTagMatch);

            if (aiReason != null && !aiReason.isBlank()) {
                // 文案长度校验，超长则截断
                String finalReason = aiReason.length() > REASON_MAX_LENGTH
                        ? aiReason.substring(0, REASON_MAX_LENGTH) + "…"
                        : aiReason;
                if (cache != null) {
                    cache.put(cacheKey, finalReason);
                }
                return finalReason;
            }
        } catch (Exception e) {
            log.warn("AI 推荐理由生成失败，降级为固定文案: eventId={}", event.getId(), e);
        }

        return hasTagMatch ? "根据你的偏好标签推荐" : "热门活动推荐";
    }

    /**
     * 带评分的活动包装类，用于内存中排序。
     */
    private static class ScoredEvent {
        final Event event;
        final double score;
        final boolean hasTagMatch;

        ScoredEvent(Event event, double score, boolean hasTagMatch) {
            this.event = event;
            this.score = score;
            this.hasTagMatch = hasTagMatch;
        }

        double getScore() {
            return score;
        }
    }
}