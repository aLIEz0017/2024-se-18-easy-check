package com.easysign.controller;

import com.easysign.common.Result;
import com.easysign.dto.request.LoginRequest;
import com.easysign.dto.request.RegisterRequest;
import com.easysign.dto.response.AuthResponse;
import com.easysign.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 —— 处理注册和登录的 HTTP 请求
 * <p>
 * 接口路径：
 * - POST /api/v1/auth/register — 用户注册
 * - POST /api/v1/auth/login    — 用户登录
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return Result.success(response);
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Result.success(response);
    }
}