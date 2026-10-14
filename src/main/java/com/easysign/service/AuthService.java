package com.easysign.service;

import com.easysign.dto.request.LoginRequest;
import com.easysign.dto.request.RegisterRequest;
import com.easysign.dto.response.AuthResponse;

/**
 * 认证服务接口 —— 定义注册和登录的核心业务逻辑
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param request 注册请求（username, password, role）
     * @return 认证响应（token, userId, username, role）
     * @throws RuntimeException 若用户名已存在则抛出异常
     */
    AuthResponse register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求（username, password）
     * @return 认证响应（token, userId, username, role）
     * @throws RuntimeException 若用户名不存在或密码错误则抛出异常
     */
    AuthResponse login(LoginRequest request);
}