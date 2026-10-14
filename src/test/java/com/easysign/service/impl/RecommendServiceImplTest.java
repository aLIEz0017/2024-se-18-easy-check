package com.easysign.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecommendServiceImpl 单元测试")
class RecommendServiceImplTest {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Long USER_ID = 1L;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private AiService aiService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private LlmConfig llmConfig;

    @InjectMocks
    private RecommendServiceImpl recommendService;

    private Event eventTech;    // ["科技","电竞"]
    private Event eventMusic;   // ["音乐"]
    private Event eventBoth;    // ["科技","音乐"]
    private Event eventNoTags;  // tags = null

    @BeforeEach
    void setUp() {
        // 默认：历史票务记录为空（大多数测试不关注历史分）
        when(ticketMapper.selectList(any())).thenReturn(Collections.emptyList());
        eventTech = createEvent(1L, "AI 技术沙龙", 200, 150, "[\"科技\",\"电竞\"]");
        eventMusic = createEvent(2L, "音乐会", 100, 80, "[\"音乐\"]");
        eventBoth = createEvent(3L, "科技音乐节", 300, 100, "[\"科技\",\"音乐\"]");
        eventNoTags = createEvent(4L, "无标签活动", 50, 25, null);
    }

    private Event createEvent(Long id, String title, Integer total, Integer sold, String tags) {
        Event event = new Event();
        event.setId(id);
        event.setTitle(title);
        event.setDescription("描述-" + title);
        event.setLocation("地点-" + title);
        event.setTotalTickets(total);
        event.setSoldTickets(sold);
        event.setTags(tags);
        event.setStatus("online");
        event.setStartTime(LocalDateTime.of(2026, 7, 15, 14, 0));
        event.setEndTime(LocalDateTime.of(2026, 7, 15, 18, 0));
        event.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return event;
    }

    // ======================== 场景测试 ========================

    @Nested
    @DisplayName("无 online 活动场景")
    class NoOnlineEvents {

        @Test
        @DisplayName("没有 online 活动时返回空列表")
        void shouldReturnEmptyWhenNoOnlineEvents() {
            when(eventMapper.selectList(any())).thenReturn(Collections.emptyList());

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertTrue(result.getRecords().isEmpty());
            assertEquals(0, result.getTotal());
            verify(eventMapper, times(1)).selectList(any());
            verifyNoInteractions(userMapper);
        }
    }

    @Nested
    @DisplayName("用户未登录 / 无标签场景")
    class NoUserOrNoTags {

        @Test
        @DisplayName("userId 为 null 时按热度排序，不查用户表")
        void shouldSortBySellRateWhenUserIdIsNull() {
            List<Event> onlineEvents = Arrays.asList(eventTech, eventMusic, eventBoth);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 3);

            // 纯热度排序（TAG=0, HISTORY=0）：music (0.8*0.3=0.24) > tech (0.75*0.3=0.225) > both (0.333...*0.3≈0.1)
            List<EventResponse> records = result.getRecords();
            assertEquals(3, records.size());
            assertEquals("音乐会", records.get(0).getTitle());
            assertEquals("AI 技术沙龙", records.get(1).getTitle());
            assertEquals("科技音乐节", records.get(2).getTitle());
            // 所有 reason 都应该是 "热门活动推荐"
            records.forEach(r -> assertEquals("热门活动推荐", r.getReason()));
            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("userId 为 null 时不调用 AI（即使启用）")
        void shouldNotCallAiWhenUserIdIsNull() {
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("热门活动推荐", result.getRecords().get(0).getReason());
            verifyNoInteractions(aiService);
        }

        @Test
        @DisplayName("用户无偏好标签时按热度排序")
        void shouldSortBySellRateWhenUserHasNoTags() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags(null);
            List<Event> onlineEvents = Arrays.asList(eventTech, eventMusic);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            List<EventResponse> records = result.getRecords();
            assertEquals(2, records.size());
            assertEquals("音乐会", records.get(0).getTitle());
            assertEquals("AI 技术沙龙", records.get(1).getTitle());
            records.forEach(r -> assertEquals("热门活动推荐", r.getReason()));
        }

        @Test
        @DisplayName("用户不存在时按热度排序")
        void shouldSortBySellRateWhenUserNotFound() {
            when(eventMapper.selectList(any())).thenReturn(Arrays.asList(eventTech, eventMusic));
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            List<EventResponse> records = result.getRecords();
            assertEquals(2, records.size());
            assertEquals("音乐会", records.get(0).getTitle());
            assertEquals("AI 技术沙龙", records.get(1).getTitle());
            records.forEach(r -> assertEquals("热门活动推荐", r.getReason()));
        }
    }

    @Nested
    @DisplayName("标签匹配场景")
    class TagMatching {

        @Test
        @DisplayName("用户标签与活动标签有交集时返回个性化推荐")
        void shouldReturnPersonalizedWhenTagsMatch() {
            // 用户标签 ["科技","电竞"]
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            List<Event> onlineEvents = Arrays.asList(eventTech, eventMusic, eventBoth);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            List<EventResponse> records = result.getRecords();
            assertEquals(3, records.size());

            // 加权评分（HISTORY=0 因为 mock 了空历史）:
            //   tech:  0.4*(2/2) + 0.3*0.75 + 0.3*0 = 0.4 + 0.225 = 0.625
            //   both:  0.4*(1/2) + 0.3*(100/300) + 0.3*0 = 0.2 + 0.1 = 0.3
            //   music: 0.4*0 + 0.3*0.8 + 0.3*0 = 0.24
            // 排序: tech > both > music
            assertEquals("AI 技术沙龙", records.get(0).getTitle());
            assertEquals("根据你的偏好标签推荐", records.get(0).getReason());
            assertEquals("科技音乐节", records.get(1).getTitle());
            assertEquals("根据你的偏好标签推荐", records.get(1).getReason());
            assertEquals("音乐会", records.get(2).getTitle());
            assertEquals("热门活动推荐", records.get(2).getReason());
        }

        @Test
        @DisplayName("用户标签与活动标签无交集时统一为热门推荐")
        void shouldReturnHotWhenTagsNoMatch() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"电竞\"]");
            List<Event> onlineEvents = Arrays.asList(eventMusic, eventNoTags);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            // music:  0.4*0 + 0.3*0.8 + 0.3*0 = 0.24
            // noTags: 0.4*0 + 0.3*0.5 + 0.3*0 = 0.15
            List<EventResponse> records = result.getRecords();
            records.forEach(r -> assertEquals("热门活动推荐", r.getReason()));
        }

        @Test
        @DisplayName("活动无标签时 tagMatchScore 为 0")
        void shouldHandleEventWithoutTags() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\"]");
            List<Event> onlineEvents = Collections.singletonList(eventNoTags);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("无标签活动", result.getRecords().get(0).getTitle());
            assertEquals("热门活动推荐", result.getRecords().get(0).getReason());
        }

        @Test
        @DisplayName("AI 被禁用（llm.enabled=false）时不调用 AI，使用固定文案")
        void shouldNotCallAiWhenLlmDisabled() {
            // llmConfig.isEnabled() 默认返回 false，不需要额外 mock
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("根据你的偏好标签推荐", result.getRecords().get(0).getReason());
            verifyNoInteractions(aiService);
        }
    }

    @Nested
    @DisplayName("Top N 选取与边界场景")
    class TopNSelection {

        @Test
        @DisplayName("返回条数受 size 参数控制（取 Top N）")
        void shouldRespectSizeParameter() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\"]");
            List<Event> onlineEvents = Arrays.asList(eventTech, eventMusic, eventBoth);
            when(eventMapper.selectList(any())).thenReturn(onlineEvents);
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            // 取 Top 2
            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 2);

            assertEquals(2, result.getRecords().size());
            assertEquals(3, result.getTotal());
        }

        @Test
        @DisplayName("online 活动少于 size 时返回实际数量（不补位）")
        void shouldReturnActualCountWhenLessThanSize() {
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals(1, result.getTotal());
        }

        @Test
        @DisplayName("totalTickets 为 0 时 sellRate 为 0，不抛异常")
        void shouldHandleZeroTotalTickets() {
            Event zeroTicketEvent = createEvent(5L, "免费活动", 0, 0, "[\"科技\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(zeroTicketEvent));

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("免费活动", result.getRecords().get(0).getTitle());
        }

        @Test
        @DisplayName("soldTickets 为 null 时 sellRate 为 0")
        void shouldHandleNullSoldTickets() {
            Event event = createEvent(6L, "未售出活动", 100, null, null);
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(event));

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("未售出活动", result.getRecords().get(0).getTitle());
        }
    }

    @Nested
    @DisplayName("响应字段验证")
    class ResponseFields {

        @Test
        @DisplayName("返回的 EventResponse 字段完整且正确")
        void shouldMapAllFieldsCorrectly() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\"]");
            when(eventMapper.selectList(any())).thenReturn(Arrays.asList(eventTech, eventMusic));
            when(userMapper.selectById(USER_ID)).thenReturn(user);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            EventResponse techResp = result.getRecords().stream()
                    .filter(r -> "AI 技术沙龙".equals(r.getTitle()))
                    .findFirst().orElseThrow();

            assertEquals(1L, techResp.getEventId());
            assertEquals("描述-AI 技术沙龙", techResp.getDescription());
            assertEquals("地点-AI 技术沙龙", techResp.getLocation());
            assertEquals(200, techResp.getTotalTickets());
            assertEquals(150, techResp.getSoldTickets());
            assertEquals("online", techResp.getStatus());
            assertEquals("2026-07-15 14:00:00", techResp.getStartTime());
            assertEquals("2026-07-15 18:00:00", techResp.getEndTime());
            assertEquals("2026-07-01 10:00:00", techResp.getCreatedAt());
            assertNull(techResp.getUpdatedAt());
            // 标签应被解析为 List<String>
            assertEquals(Arrays.asList("科技", "电竞"), techResp.getTags());
            assertEquals("根据你的偏好标签推荐", techResp.getReason());
        }

        @Test
        @DisplayName("PageResponse 页码和总页数正确")
        void shouldHaveCorrectPagination() {
            when(eventMapper.selectList(any())).thenReturn(Arrays.asList(eventTech, eventMusic, eventBoth));

            PageResponse<EventResponse> result = recommendService.recommendEvents(null, 1, 2);

            assertEquals(1, result.getPage());
            assertEquals(2, result.getSize());
            assertEquals(3, result.getTotal());
            // pages = (3 + 2 - 1) / 2 = 2
            assertEquals(2, result.getPages());
        }
    }

    // ======================== AI 推荐理由场景 ========================

    @Nested
    @DisplayName("AI 推荐理由场景（启用 AI）")
    class AiRecommendReasonEnabled {

        @Test
        @DisplayName("AI 调用成功返回非空文案时使用 AI 生成的文案")
        void shouldUseAiReasonWhenAiReturnsNonEmpty() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            when(cache.get(anyString(), eq(String.class))).thenReturn(null);
            when(aiService.generateRecommendReason(anyString(), anyString(), anyString(),
                    anyString(), anyDouble(), anyBoolean())).thenReturn("根据你的【科技】偏好推荐【AI 技术沙龙】");

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("根据你的【科技】偏好推荐【AI 技术沙龙】", result.getRecords().get(0).getReason());
            verify(aiService, times(1)).generateRecommendReason(anyString(), anyString(),
                    anyString(), anyString(), anyDouble(), anyBoolean());
            // 验证缓存被存入
            verify(cache, times(1)).put(anyString(), anyString());
        }

        @Test
        @DisplayName("AI 调用抛出异常时降级为固定文案")
        void shouldFallbackWhenAiThrowsException() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            when(cache.get(anyString(), eq(String.class))).thenReturn(null);
            when(aiService.generateRecommendReason(anyString(), anyString(), anyString(),
                    anyString(), anyDouble(), anyBoolean())).thenThrow(new RuntimeException("API 超时"));

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("根据你的偏好标签推荐", result.getRecords().get(0).getReason());
            // 验证不缓存
            verify(cache, never()).put(anyString(), anyString());
        }

        @Test
        @DisplayName("AI 返回 null 时降级为固定文案")
        void shouldFallbackWhenAiReturnsNull() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            when(cache.get(anyString(), eq(String.class))).thenReturn(null);
            when(aiService.generateRecommendReason(anyString(), anyString(), anyString(),
                    anyString(), anyDouble(), anyBoolean())).thenReturn(null);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("根据你的偏好标签推荐", result.getRecords().get(0).getReason());
            verify(cache, never()).put(anyString(), anyString());
        }

        @Test
        @DisplayName("AI 返回空字符串时降级为固定文案")
        void shouldFallbackWhenAiReturnsEmpty() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            when(cache.get(anyString(), eq(String.class))).thenReturn(null);
            when(aiService.generateRecommendReason(anyString(), anyString(), anyString(),
                    anyString(), anyDouble(), anyBoolean())).thenReturn("");

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals("根据你的偏好标签推荐", result.getRecords().get(0).getReason());
            verify(cache, never()).put(anyString(), anyString());
        }

        @Test
        @DisplayName("AI 返回超长文案时截断")
        void shouldTruncateWhenAiReturnsTooLong() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            when(cache.get(anyString(), eq(String.class))).thenReturn(null);
            // 构造一个超过 50 字的文案
            String longReason = "很喜欢" + "非常".repeat(30) + "推荐";
            when(aiService.generateRecommendReason(anyString(), anyString(), anyString(),
                    anyString(), anyDouble(), anyBoolean())).thenReturn(longReason);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            String reason = result.getRecords().get(0).getReason();
            assertTrue(reason.endsWith("…"));
            assertEquals(51, reason.length()); // 50字 + "…"
            verify(cache, times(1)).put(anyString(), eq(reason));
        }

        @Test
        @DisplayName("缓存命中时直接返回缓存文案，不调 AI")
        void shouldReturnCachedWhenCacheHit() {
            User user = new User();
            user.setId(USER_ID);
            user.setTags("[\"科技\",\"电竞\"]");
            when(eventMapper.selectList(any())).thenReturn(Collections.singletonList(eventTech));
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(llmConfig.isEnabled()).thenReturn(true);
            when(cacheManager.getCache("recommendReason")).thenReturn(cache);
            // 缓存命中
            String cachedReason = "根据你的【科技】偏好推荐【AI 技术沙龙】";
            when(cache.get(anyString(), eq(String.class))).thenReturn(cachedReason);

            PageResponse<EventResponse> result = recommendService.recommendEvents(USER_ID, 1, 3);

            assertEquals(1, result.getRecords().size());
            assertEquals(cachedReason, result.getRecords().get(0).getReason());
            // AI 不应被调用
            verifyNoInteractions(aiService);
        }
    }
}