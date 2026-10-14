package com.easysign.websocket;

import com.easysign.entity.Seat;
import com.easysign.mapper.SeatMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SeatWebSocketHandler 单元测试
 * <p>
 * 覆盖连接建立、关闭、seat_sync 初始同步、broadcastSeatSold 广播逻辑。
 * 手动注入 Mock SeatMapper，不依赖 Spring 容器。
 * </p>
 */
@DisplayName("SeatWebSocketHandler 单元测试")
class SeatWebSocketHandlerTest {

    private static final Long EVENT_ID = 100L;

    private SeatWebSocketHandler handler;
    private SeatMapper mockSeatMapper;

    @BeforeEach
    void setUp() {
        mockSeatMapper = mock(SeatMapper.class);
        handler = new SeatWebSocketHandler();
        // 手动注入 mock SeatMapper（字段注入，需反射）
        try {
            java.lang.reflect.Field field = SeatWebSocketHandler.class.getDeclaredField("seatMapper");
            field.setAccessible(true);
            field.set(handler, mockSeatMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理内部 eventSessions 避免测试间污染
        java.lang.reflect.Field sessionsField = SeatWebSocketHandler.class.getDeclaredField("eventSessions");
        sessionsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<Long, java.util.Map<String, WebSocketSession>> sessions =
                (java.util.Map<Long, java.util.Map<String, WebSocketSession>>) sessionsField.get(handler);
        sessions.clear();
    }

    // ======================== 连接建立 ========================

    @Nested
    @DisplayName("连接建立")
    class ConnectionEstablishedTest {

        @Test
        @DisplayName("有效 eventId → 连接建立，发送 seat_sync 消息")
        void afterConnectionEstablished_success() throws Exception {
            WebSocketSession session = mockSession("session-1", "/ws/seat/" + EVENT_ID + "?token=abc");
            when(session.isOpen()).thenReturn(true);

            List<Seat> seats = Arrays.asList(
                    buildSeat(1L, 1, 1, "1-1", "available"),
                    buildSeat(2L, 1, 2, "1-2", "sold")
            );
            when(mockSeatMapper.selectByEventId(EVENT_ID)).thenReturn(seats);

            handler.afterConnectionEstablished(session);

            // 验证发送了 TextMessage
            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session).sendMessage(captor.capture());

            String payload = captor.getValue().getPayload();
            assertTrue(payload.contains("\"type\":\"seat_sync\""));
            assertTrue(payload.contains("\"seatId\":1"));
            assertTrue(payload.contains("\"seatId\":2"));
        }

        @Test
        @DisplayName("路径不含有效 eventId → 关闭连接，状态码 BAD_DATA")
        void afterConnectionEstablished_invalidEventId() throws Exception {
            WebSocketSession session = mockSession("bad", "/invalid/path");

            handler.afterConnectionEstablished(session);

            verify(session).close(CloseStatus.BAD_DATA);
            verify(session, never()).sendMessage(any());
        }
    }

    // ======================== 连接关闭 ========================

    @Nested
    @DisplayName("连接关闭")
    class ConnectionClosedTest {

        @Test
        @DisplayName("连接关闭后从 eventSessions 移除")
        void afterConnectionClosed_removesSession() throws Exception {
            WebSocketSession session = mockSession("session-1", "/ws/seat/" + EVENT_ID);
            when(session.isOpen()).thenReturn(true);
            when(mockSeatMapper.selectByEventId(EVENT_ID)).thenReturn(java.util.Collections.emptyList());

            // 先建立连接
            handler.afterConnectionEstablished(session);

            // 再关闭连接
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);

            // 此时 eventSessions 应为空（无连接）
            // 通过反射验证
            java.lang.reflect.Field sessionsField = SeatWebSocketHandler.class.getDeclaredField("eventSessions");
            sessionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Long, java.util.Map<String, WebSocketSession>> sessions =
                    (java.util.Map<Long, java.util.Map<String, WebSocketSession>>) sessionsField.get(handler);
            assertFalse(sessions.containsKey(EVENT_ID));
        }
    }

    // ======================== 广播 seat_sold ========================

    @Nested
    @DisplayName("广播 seat_sold")
    class BroadcastSeatSoldTest {

        @Test
        @DisplayName("有在线客户端 → 推送 seat_sold，包含 seatId")
        void broadcastSeatSold_withClient() throws Exception {
            WebSocketSession session = mockSession("session-1", "/ws/seat/" + EVENT_ID);
            when(session.isOpen()).thenReturn(true);
            when(mockSeatMapper.selectByEventId(EVENT_ID)).thenReturn(java.util.Collections.emptyList());

            // 建立连接
            handler.afterConnectionEstablished(session);

            // 广播
            handler.broadcastSeatSold(EVENT_ID, 42L);

            // 验证推送（sendMessage 被调用了 2 次：1 次 seat_sync + 1 次 seat_sold）
            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, times(2)).sendMessage(captor.capture());

            List<TextMessage> messages = captor.getAllValues();
            String soldPayload = messages.get(1).getPayload();
            assertTrue(soldPayload.contains("\"type\":\"seat_sold\""));
            assertTrue(soldPayload.contains("\"seatId\":42"));
        }

        @Test
        @DisplayName("无在线客户端 → 不抛异常，静默返回")
        void broadcastSeatSold_noClient() {
            // 未建立任何连接
            assertDoesNotThrow(() -> handler.broadcastSeatSold(EVENT_ID, 42L));
        }
    }

    // ======================== 工具方法 ========================

    private WebSocketSession mockSession(String sessionId, String uriString) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUri()).thenReturn(URI.create(uriString));
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private Seat buildSeat(Long id, int row, int col, String label, String status) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setEventId(EVENT_ID);
        seat.setSeatRow(row);
        seat.setSeatCol(col);
        seat.setSeatLabel(label);
        seat.setStatus(status);
        return seat;
    }
}