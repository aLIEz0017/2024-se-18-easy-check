package com.easysign.config;

import com.easysign.websocket.SeatWebSocketHandler;
import com.easysign.websocket.SeatWebSocketInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * <p>
 * 注册座位 WebSocket 端点 /ws/seat/{eventId}，
 * 握手阶段由 {@link SeatWebSocketInterceptor} 校验 JWT。
 * </p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private SeatWebSocketHandler seatWebSocketHandler;

    @Autowired
    private SeatWebSocketInterceptor seatWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(seatWebSocketHandler, "/ws/seat/{eventId}")
                .addInterceptors(seatWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}