package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easysign.common.BizException;
import com.easysign.dto.request.CheckinRequest;
import com.easysign.dto.response.PageResponse;
import com.easysign.dto.response.TicketResponse;
import com.easysign.entity.Event;
import com.easysign.entity.Ticket;
import com.easysign.entity.User;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.EventStockMapper;
import com.easysign.mapper.SeatMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.mapper.UserMapper;
import com.easysign.ratelimit.PurchaseRateLimiter;
import com.easysign.websocket.SeatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TicketServiceImpl} 的单元测试
 * <p>
 * 覆盖以下核心业务路径：
 * <ul>
 *   <li>5.2 我的票夹 — {@link TicketServiceImpl#getMyTickets}</li>
 *   <li>5.4 签到 — {@link TicketServiceImpl#checkin}</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketServiceImpl — 票务服务单元测试")
class TicketServiceImplTest {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ======================== 公共常量 ========================

    private static final Long ORGANIZER_ID = 10L;
    private static final Long ATTENDEE_ID = 5L;
    private static final Long EVENT_ID = 1L;
    private static final Long TICKET_ID = 100L;
    private static final Long SEAT_ID = 42L;
    private static final String TICKET_NO = "1_5_1700000000";
    private static final String EVENT_TITLE = "Java Day";

    // ======================== Mock 依赖 ========================

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventStockMapper eventStockMapper;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PurchaseRateLimiter purchaseRateLimiter;

    @Mock
    private SeatWebSocketHandler seatWebSocketHandler;

    @InjectMocks
    private TicketServiceImpl ticketService;

    // ======================== 测试数据 ========================

    private Event onlineEvent;
    private Event finishedEvent;
    private Ticket pendingTicket;
    private Ticket checkedTicket;
    private Ticket invalidTicket;
    private Ticket refundedTicket;
    private User attendee;

    @BeforeEach
    void setUp() {
        onlineEvent = new Event();
        onlineEvent.setId(EVENT_ID);
        onlineEvent.setOrganizerId(ORGANIZER_ID);
        onlineEvent.setTitle(EVENT_TITLE);
        onlineEvent.setStatus("online");
        onlineEvent.setHasSeats(false);

        finishedEvent = new Event();
        finishedEvent.setId(EVENT_ID);
        finishedEvent.setOrganizerId(ORGANIZER_ID);
        finishedEvent.setTitle(EVENT_TITLE);
        finishedEvent.setStatus("finished");

        pendingTicket = new Ticket();
        pendingTicket.setId(TICKET_ID);
        pendingTicket.setEventId(EVENT_ID);
        pendingTicket.setUserId(ATTENDEE_ID);
        pendingTicket.setTicketNo(TICKET_NO);
        pendingTicket.setStatus("pending");

        checkedTicket = new Ticket();
        checkedTicket.setId(TICKET_ID);
        checkedTicket.setEventId(EVENT_ID);
        checkedTicket.setUserId(ATTENDEE_ID);
        checkedTicket.setTicketNo(TICKET_NO);
        checkedTicket.setStatus("checked");
        checkedTicket.setCheckedAt(LocalDateTime.now());

        invalidTicket = new Ticket();
        invalidTicket.setId(TICKET_ID);
        invalidTicket.setEventId(EVENT_ID);
        invalidTicket.setUserId(ATTENDEE_ID);
        invalidTicket.setTicketNo(TICKET_NO);
        invalidTicket.setStatus("invalid");

        refundedTicket = new Ticket();
        refundedTicket.setId(TICKET_ID);
        refundedTicket.setEventId(EVENT_ID);
        refundedTicket.setUserId(ATTENDEE_ID);
        refundedTicket.setTicketNo(TICKET_NO);
        refundedTicket.setStatus("refunded");

        attendee = new User();
        attendee.setId(ATTENDEE_ID);
        attendee.setUsername("test_attendee");
    }

    // ================================================================
    //  5.4 checkin() — 签到
    // ================================================================

    @Nested
    @DisplayName("checkin() — 签到")
    class CheckinTests {

        /** 生成当前时间戳偏移量（秒），用于构建不过期的完整格式签到码 */
        private static long recentTimestamp() {
            return System.currentTimeMillis() / 1000 - 5; // 5 秒前，确保在 60 秒窗口内
        }

        /** 构建完整格式签到码：ticket_{eventId}_{ticketNo}_{timestamp} */
        private static String fullCode(Long eventId, String ticketNo) {
            return "ticket_" + eventId + "_" + ticketNo + "_" + recentTimestamp();
        }

        /** 构建手动输入格式签到码：ticket_{ticketNo} */
        private static String simpleCode(String ticketNo) {
            return "ticket_" + ticketNo;
        }

        // ======================== 成功路径 ========================

        @Test
        @DisplayName("成功签到（完整格式）：返回 ticketNo、eventTitle、username、checkedAt")
        void checkin_fullFormat_success() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);
            when(ticketMapper.updateCheckin(TICKET_ID)).thenReturn(1);
            when(userMapper.selectById(ATTENDEE_ID)).thenReturn(attendee);

            TicketResponse resp = ticketService.checkin(ORGANIZER_ID, req);

            assertNotNull(resp);
            assertEquals(TICKET_ID, resp.getTicketId());
            assertEquals(TICKET_NO, resp.getTicketNo());
            assertEquals(EVENT_TITLE, resp.getEventTitle());
            assertEquals(attendee.getUsername(), resp.getUsername());
            assertNotNull(resp.getCheckedAt(), "签到时间不应为空");

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verify(ticketMapper).updateCheckin(TICKET_ID);
            verify(userMapper).selectById(ATTENDEE_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper, userMapper, seatMapper, eventStockMapper);
        }

        @Test
        @DisplayName("成功签到（手动输入格式）：不校验时间戳和 eventId 匹配")
        void checkin_simpleFormat_success() {
            CheckinRequest req = buildCheckinRequest(simpleCode(TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);
            when(ticketMapper.updateCheckin(TICKET_ID)).thenReturn(1);
            when(userMapper.selectById(ATTENDEE_ID)).thenReturn(attendee);

            TicketResponse resp = ticketService.checkin(ORGANIZER_ID, req);

            assertNotNull(resp);
            assertEquals(TICKET_ID, resp.getTicketId());
            assertEquals(TICKET_NO, resp.getTicketNo());
            assertNotNull(resp.getCheckedAt());

            // 手动格式不校验 timestamp，不应触发过期
            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verify(ticketMapper).updateCheckin(TICKET_ID);
            verify(userMapper).selectById(ATTENDEE_ID);
        }

        // ======================== 凭证格式校验 ========================

        @Test
        @DisplayName("失败：无效的签到凭证格式 → BizException(4101)")
        void checkin_invalidCodeFormat() {
            CheckinRequest req = buildCheckinRequest("invalid_code_xxx");

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4101, ex.getCode());
            assertEquals("无效的签到凭证，格式不合法", ex.getMsg());

            verifyNoInteractions(ticketMapper, eventMapper, userMapper);
        }

        @Test
        @DisplayName("失败：完整格式二维码已过期（超过 60 秒）→ BizException(4102)")
        void checkin_codeExpired() {
            // 2001-09-09 的时间戳（10 位，匹配 FULL_PATTERN，但远超 60 秒窗口）
            String code = "ticket_1_" + TICKET_NO + "_1000000000";
            CheckinRequest req = buildCheckinRequest(code);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4102, ex.getCode());
            assertEquals("二维码已过期，请刷新", ex.getMsg());

            verifyNoInteractions(ticketMapper, eventMapper, userMapper);
        }

        // ======================== 票务查询异常 ========================

        @Test
        @DisplayName("失败：票务不存在 → BizException(4103)")
        void checkin_ticketNotFound() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4103, ex.getCode());
            assertEquals("票务不存在", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verifyNoMoreInteractions(ticketMapper);
            verifyNoInteractions(eventMapper, userMapper);
        }

        @Test
        @DisplayName("失败：完整格式 eventId 与票务不匹配 → BizException(4101)")
        void checkin_eventNotMatch() {
            // code 中的 eventId = 999，但 ticket 的 eventId = 1
            CheckinRequest req = buildCheckinRequest(fullCode(999L, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4101, ex.getCode());
            assertEquals("无效的签到凭证，票务与活动不匹配", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verifyNoMoreInteractions(ticketMapper);
            verifyNoInteractions(eventMapper, userMapper);
        }

        // ======================== 举办方权限校验 ========================

        @Test
        @DisplayName("失败：签到者不是活动举办方 → BizException(4002)")
        void checkin_notOrganizer() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);

            // 签到者 userId = 99 不是举办方 ORGANIZER_ID = 10
            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(99L, req));
            assertEquals(4002, ex.getCode());
            assertEquals("权限不足，您不是该活动的举办方", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        // ======================== 活动状态校验 ========================

        @Test
        @DisplayName("失败：活动已结束 → BizException(4107)")
        void checkin_eventFinished() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(finishedEvent);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4107, ex.getCode());
            assertEquals("活动已结束，无法签到", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        // ======================== 票务状态校验 ========================

        @Test
        @DisplayName("失败：票务已签到 → BizException(4104)")
        void checkin_ticketAlreadyChecked() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(checkedTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4104, ex.getCode());
            assertEquals("请勿重复签到", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("失败：票务已失效 → BizException(4105)")
        void checkin_ticketInvalid() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(invalidTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4105, ex.getCode());
            assertEquals("票务已失效", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("失败：票务已退票 → BizException(4106)")
        void checkin_ticketRefunded() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(refundedTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4106, ex.getCode());
            assertEquals("票务已退票", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        // ======================== 原子更新异常 ========================

        @Test
        @DisplayName("失败：原子签到更新返回 0（并发重复签到）→ BizException(4104)")
        void checkin_updateCheckinReturns_zero() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);
            when(ticketMapper.updateCheckin(TICKET_ID)).thenReturn(0);

            BizException ex = assertThrows(BizException.class,
                    () -> ticketService.checkin(ORGANIZER_ID, req));
            assertEquals(4104, ex.getCode());
            assertEquals("请勿重复签到", ex.getMsg());

            verify(ticketMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(eventMapper).selectById(EVENT_ID);
            verify(ticketMapper).updateCheckin(TICKET_ID);
            verifyNoMoreInteractions(ticketMapper, eventMapper);
            verifyNoInteractions(userMapper);
        }

        // ======================== 边界场景 ========================

        @Test
        @DisplayName("边界：attendee 为 null 时签到成功，username 返回 null")
        void checkin_attendeeIsNull_usernameNull() {
            CheckinRequest req = buildCheckinRequest(fullCode(EVENT_ID, TICKET_NO));

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);
            when(ticketMapper.updateCheckin(TICKET_ID)).thenReturn(1);
            when(userMapper.selectById(ATTENDEE_ID)).thenReturn(null);

            TicketResponse resp = ticketService.checkin(ORGANIZER_ID, req);

            assertNotNull(resp);
            assertEquals(TICKET_NO, resp.getTicketNo());
            assertNull(resp.getUsername(), "用户不存在时 username 应为 null");
            assertNotNull(resp.getCheckedAt());
        }

        @Test
        @DisplayName("边界：完整格式时间戳恰好等于当前时间，不应过期")
        void checkin_timestampExactlyNow_valid() {
            long now = System.currentTimeMillis() / 1000;
            String code = "ticket_1_" + TICKET_NO + "_" + now;
            CheckinRequest req = buildCheckinRequest(code);

            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pendingTicket);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(onlineEvent);
            when(ticketMapper.updateCheckin(TICKET_ID)).thenReturn(1);
            when(userMapper.selectById(ATTENDEE_ID)).thenReturn(attendee);

            TicketResponse resp = ticketService.checkin(ORGANIZER_ID, req);

            assertNotNull(resp);
            assertEquals(TICKET_NO, resp.getTicketNo());
            assertNotNull(resp.getCheckedAt());
        }
    }

    // ================================================================
    //  5.2 getMyTickets() — 我的票夹
    // ================================================================

    @Nested
    @DisplayName("getMyTickets() — 我的票夹")
    class GetMyTicketsTests {

        private static final int PAGE = 1;
        private static final int SIZE = 10;

        @Test
        @DisplayName("成功查询：按 status 过滤，返回分页数据")
        void getMyTickets_withStatus() {
            String status = "purchased";

            TicketResponse ticket1 = buildTicketResponse(1L, "1_5_1700000001", "purchased");
            TicketResponse ticket2 = buildTicketResponse(2L, "1_5_1700000002", "purchased");
            List<TicketResponse> mockList = List.of(ticket1, ticket2);

            Page<TicketResponse> pageResult = new Page<>(PAGE, SIZE);
            pageResult.setRecords(mockList);
            pageResult.setTotal(2);

            when(ticketMapper.selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), eq(status)))
                    .thenReturn(pageResult);

            PageResponse<TicketResponse> resp = ticketService.getMyTickets(ATTENDEE_ID, status, PAGE, SIZE);

            assertNotNull(resp);
            assertEquals(2, resp.getTotal());
            assertEquals(2, resp.getRecords().size());
            assertEquals(PAGE, resp.getPage());
            assertEquals(SIZE, resp.getSize());

            verify(ticketMapper).selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), eq(status));
            verifyNoMoreInteractions(ticketMapper);
        }

        @Test
        @DisplayName("成功查询：不传 status（全量查询），返回分页数据")
        void getMyTickets_withoutStatus() {
            TicketResponse ticket1 = buildTicketResponse(1L, "1_5_1700000001", "purchased");
            TicketResponse ticket2 = buildTicketResponse(2L, "1_5_1700000002", "checked");
            List<TicketResponse> mockList = List.of(ticket1, ticket2);

            Page<TicketResponse> pageResult = new Page<>(PAGE, SIZE);
            pageResult.setRecords(mockList);
            pageResult.setTotal(2);

            when(ticketMapper.selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), isNull()))
                    .thenReturn(pageResult);

            PageResponse<TicketResponse> resp = ticketService.getMyTickets(ATTENDEE_ID, null, PAGE, SIZE);

            assertNotNull(resp);
            assertEquals(2, resp.getTotal());
            assertEquals(2, resp.getRecords().size());

            verify(ticketMapper).selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), isNull());
            verifyNoMoreInteractions(ticketMapper);
        }

        @Test
        @DisplayName("成功查询：空结果，total 为 0")
        void getMyTickets_empty() {
            Page<TicketResponse> pageResult = new Page<>(PAGE, SIZE);
            pageResult.setRecords(List.of());
            pageResult.setTotal(0);

            when(ticketMapper.selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), anyString()))
                    .thenReturn(pageResult);

            PageResponse<TicketResponse> resp = ticketService.getMyTickets(ATTENDEE_ID, "refunded", PAGE, SIZE);

            assertNotNull(resp);
            assertEquals(0, resp.getTotal());
            assertTrue(resp.getRecords().isEmpty());
            assertEquals(PAGE, resp.getPage());
            assertEquals(SIZE, resp.getSize());

            verify(ticketMapper).selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), eq("refunded"));
            verifyNoMoreInteractions(ticketMapper);
        }

        @Test
        @DisplayName("成功查询：分页参数传递正确（第2页，每页5条）")
        void getMyTickets_paginationParams() {
            int page = 2;
            int size = 5;

            Page<TicketResponse> pageResult = new Page<>(page, size);
            pageResult.setRecords(List.of());
            pageResult.setTotal(0);

            when(ticketMapper.selectMyTicketsWithEvent(any(Page.class), eq(ATTENDEE_ID), isNull()))
                    .thenReturn(pageResult);

            PageResponse<TicketResponse> resp = ticketService.getMyTickets(ATTENDEE_ID, null, page, size);

            assertEquals(page, resp.getPage());
            assertEquals(size, resp.getSize());
        }
    }

    // ======================== 辅助方法 ========================

    private static CheckinRequest buildCheckinRequest(String code) {
        CheckinRequest req = new CheckinRequest();
        req.setCode(code);
        return req;
    }

    private static TicketResponse buildTicketResponse(Long ticketId, String ticketNo, String status) {
        TicketResponse r = new TicketResponse();
        r.setTicketId(ticketId);
        r.setTicketNo(ticketNo);
        r.setStatus(status);
        r.setEventTitle(EVENT_TITLE);
        r.setEventId(EVENT_ID);
        r.setCreatedAt(LocalDateTime.now().format(DTF));
        return r;
    }
}