package com.easysign.websocket;

import com.easysign.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * <p>
 * 在 WebSocket 升级握手阶段校验 JWT token（URL 参数 ?token=xxx）。
 * 校验通过后将 userId 存入 WebSocket session attributes，供 Handler 使用。
 * </p>
 */
@Slf4j
@Component
public class SeatWebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 从 URL 参数中提取 token
        String query = request.getURI().getQuery();
        if (query == null || !query.contains("token=")) {
            log.warn("[WS] 握手失败：缺少 token 参数");
            return false;
        }

        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }

        if (token == null || token.isEmpty()) {
            log.warn("[WS] 握手失败：token 为空");
            return false;
        }

        // 校验 JWT
        try {
            if (jwtUtil.isTokenExpired(token)) {
                log.warn("[WS] 握手失败：token 已过期");
                return false;
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            attributes.put("userId", userId);
            log.info("[WS] 握手成功：userId={}", userId);
            return true;
        } catch (Exception e) {
            log.warn("[WS] 握手失败：token 无效 - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}