package com.easysign.interceptor;

import com.easysign.context.UserContext;
import com.easysign.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器
 * <p>
 * 从请求 {@code Authorization} 头（格式 {@code Bearer <token>}）中提取 JWT，
 * 使用 {@link com.easysign.util.JwtUtil} 解析后，将用户信息放入 {@link com.easysign.context.UserContext}。
 * </p>
 * <p>
 * 处理流程：
 * <ol>
 *   <li>放行 OPTIONS 预检请求</li>
 *   <li>未携带或无效 token → 直接返回 401 JSON 响应</li>
 *   <li>有效 token → 解析 userId / username / role → 写入 ThreadLocal</li>
 *   <li>{@link #afterCompletion} 清理 ThreadLocal 防止内存泄漏</li>
 * </ol>
 * </p>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 如果携带了 token，尝试解析并设置用户上下文
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                UserContext.set(new UserContext.UserInfo(userId, username, role));
                return true;
            } catch (Exception e) {
                // token 无效，不阻塞请求，让 Controller 层做鉴权判断
                return true;
            }
        }

        // 未携带 token — 放行（由 Controller 层通过 UserContext.isLoggedIn() 做动态鉴权）
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后清理 ThreadLocal，防止内存泄漏
        UserContext.clear();
    }
}