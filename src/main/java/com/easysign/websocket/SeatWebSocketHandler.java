package com.easysign.websocket;

import com.easysign.entity.Seat;
import com.easysign.mapper.SeatMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 座位 WebSocket 处理器
 * <p>
 * 管理按活动ID分组的 WebSocket 连接池，
 * 支持服务端→客户端单向推送座位状态变更。
 * </p>
 *
 * <h3>消息类型</h3>
 * <ul>
 *   <li>{@code seat_sync} — 客户端首次连接时，推送该活动所有座位的全量状态</li>
 *   <li>{@code seat_sold} — 广播某座位已被售出</li>
 * </ul>
 */
@Slf4j
@Component
public class SeatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private SeatMapper seatMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 按 eventId 分组维护 WebSocket 会话
     * key: eventId, value: 该活动下所有已连接的 session
     */
    private final Map<Long, Map<String, WebSocketSession>> eventSessions = new ConcurrentHashMap<>();

    /**
     * 从 WebSocket URL 路径中提取 eventId
     * path 格式: /ws/seat/{eventId}?token=xxx
     */
    private Long extractEventId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        // path: /ws/seat/{eventId}
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            try {
                return Long.valueOf(parts[3]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long eventId = extractEventId(session);
        if (eventId == null) {
            log.warn("[WS] 无法解析 eventId，关闭连接: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 加入分组
        eventSessions.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        log.info("[WS] 连接建立: eventId={}, sessionId={}, 当前活动连接数={}",
                eventId, session.getId(),
                eventSessions.getOrDefault(eventId, Map.of()).size());

        // 发送全量同步
        sendSeatSync(session, eventId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long eventId = extractEventId(session);
        if (eventId != null) {
            Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    eventSessions.remove(eventId);
                }
            }
            log.info("[WS] 连接关闭: eventId={}, sessionId={}, 剩余活动连接数={}",
                    eventId, session.getId(),
                    eventSessions.getOrDefault(eventId, Map.of()).size());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 当前版本客户端不发送消息，仅做日志记录
        log.debug("[WS] 收到客户端消息: sessionId={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[WS] 传输异常: sessionId={}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * 向单个客户端发送当前活动所有座位的全量状态
     */
    private void sendSeatSync(WebSocketSession session, Long eventId) {
        try {
            List<Seat> seats = seatMapper.selectByEventId(eventId);
            List<Map<String, Object>> seatList = seats.stream()
                    .map(seat -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("seatId", seat.getId());
                        m.put("status", seat.getStatus());
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("seats", seatList);

            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "seat_sync");
            msg.put("data", data);

            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
            log.debug("[WS] 发送 seat_sync: eventId={}, sessionId={}, seatCount={}",
                    eventId, session.getId(), seatList.size());
        } catch (IOException e) {
            log.error("[WS] 发送 seat_sync 失败: eventId={}, sessionId={}", eventId, session.getId(), e);
        }
    }

    /**
     * 广播座位已售出 — 推送给指定活动下所有已连接的客户端
     * <p>
     * 由 {@code TicketServiceImpl.purchase()} 和 {@code SeatServiceImpl} 在抢票/选座成功后调用。
     * </p>
     *
     * @param eventId 活动ID
     * @param seatId  已售出的座位ID
     */
    public void broadcastSeatSold(Long eventId, Long seatId) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("[WS] broadcastSeatSold: eventId={} 无在线客户端", eventId);
            return;
        }

        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "seat_sold");
        msg.put("seatId", seatId);

        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("[WS] JSON 序列化失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("[WS] 推送 seat_sold 失败: sessionId={}", session.getId(), e);
                }
            }
        }
        log.info("[WS] broadcastSeatSold: eventId={}, seatId={}, 推送数={}",
                eventId, seatId, sessions.size());
    }

    /**
     * 广播座位变回可用 — 推送给指定活动下所有已连接的客户端
     * <p>
     * 由 {@code TicketServiceImpl.purchase()} 在异步落单队列满补偿时调用，
     * 通知前端将座位状态从"sold"恢复为"available"。
     * </p>
     *
     * @param eventId 活动ID
     * @param seatId  恢复可用的座位ID
     */
    public void broadcastSeatAvailable(Long eventId, Long seatId) {
        Map<String, WebSocketSession> sessions = eventSessions.get(eventId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("[WS] broadcastSeatAvailable: eventId={} 无在线客户端", eventId);
            return;
        }

        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "seat_available");
        msg.put("seatId", seatId);

        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("[WS] JSON 序列化失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.warn("[WS] 推送 seat_available 失败: sessionId={}", session.getId(), e);
                }
            }
        }
        log.info("[WS] broadcastSeatAvailable: eventId={}, seatId={}, 推送数={}",
                eventId, seatId, sessions.size());
    }
}