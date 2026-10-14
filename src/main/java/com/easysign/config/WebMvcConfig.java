package com.easysign.config;

import com.easysign.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置 — 注册拦截器
 * <p>
 * 将 {@link JwtInterceptor} 注册到所有 {@code /api/v1/**} 路径，
 * 同时放行以下公开路径（无需登录即可访问）：
 * <ul>
 *   <li>{@code /api/v1/auth/login} — 登录</li>
 *   <li>{@code /api/v1/auth/register} — 注册</li>
 *   <li>{@code /api/v1/mock/**} — 前端临时联调用虚拟数据接口</li>
 * </ul>
 * 对于放行的路径，Controller 层通过 {@code @RequestHeader(value = "Authorization", required = false)}
 * 获取 token 并利用 {@link com.easysign.context.UserContext#isLoggedIn()} 做动态鉴权。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/mock/**"
                );
    }
}
