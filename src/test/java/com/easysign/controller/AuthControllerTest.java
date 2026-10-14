package com.easysign.controller;

import com.easysign.common.Result;
import com.easysign.dto.request.LoginRequest;
import com.easysign.dto.request.RegisterRequest;
import com.easysign.dto.response.AuthResponse;
import com.easysign.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthController} 的单元测试
 * <p>
 * 验证 Controller 层正确调用 AuthService 并包装返回 {@link Result}。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — 认证控制器单元测试")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private RegisterRequest registerReq;
    private LoginRequest loginReq;
    private AuthResponse authResponse;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "test_user";
    private static final String ROLE_ATTENDEE = "attendee";
    private static final String TOKEN = "jwt.token.value";

    @BeforeEach
    void setUp() {
        registerReq = new RegisterRequest();
        registerReq.setUsername(USERNAME);
        registerReq.setPassword("pass123");
        registerReq.setRole(ROLE_ATTENDEE);

        loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setPassword("pass123");

        authResponse = new AuthResponse(TOKEN, USER_ID, USERNAME, ROLE_ATTENDEE);
    }

    // ================================================================
    //  POST /api/v1/auth/register — 注册
    // ================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/register — 注册")
    class RegisterEndpoint {

        @Test
        @DisplayName("注册成功：返回 200 + authResponse 数据")
        void register_success() {
            when(authService.register(registerReq)).thenReturn(authResponse);

            Result<AuthResponse> result = authController.register(registerReq);

            assertNotNull(result);
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            assertEquals(TOKEN, result.getData().getToken());
            assertEquals(USER_ID, result.getData().getUserId());
            assertEquals(USERNAME, result.getData().getUsername());
            assertEquals(ROLE_ATTENDEE, result.getData().getRole());

            verify(authService).register(registerReq);
        }
    }

    // ================================================================
    //  POST /api/v1/auth/login — 登录
    // ================================================================

    @Nested
    @DisplayName("POST /api/v1/auth/login — 登录")
    class LoginEndpoint {

        @Test
        @DisplayName("登录成功：返回 200 + authResponse 数据")
        void login_success() {
            when(authService.login(loginReq)).thenReturn(authResponse);

            Result<AuthResponse> result = authController.login(loginReq);

            assertNotNull(result);
            assertEquals(200, result.getCode());
            assertNotNull(result.getData());
            assertEquals(TOKEN, result.getData().getToken());
            assertEquals(USER_ID, result.getData().getUserId());
            assertEquals(USERNAME, result.getData().getUsername());
            assertEquals(ROLE_ATTENDEE, result.getData().getRole());

            verify(authService).login(loginReq);
        }
    }
}