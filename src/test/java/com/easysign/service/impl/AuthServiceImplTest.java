package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.common.BizCode;
import com.easysign.common.BizException;
import com.easysign.config.PasswordEncoderConfig.BCryptPasswordEncoder;
import com.easysign.dto.request.LoginRequest;
import com.easysign.dto.request.RegisterRequest;
import com.easysign.dto.response.AuthResponse;
import com.easysign.entity.User;
import com.easysign.mapper.UserMapper;
import com.easysign.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthServiceImpl} 的单元测试
 * <p>
 * 覆盖以下核心业务路径：
 * <ul>
 *   <li>注册成功</li>
 *   <li>注册时用户名已存在</li>
 *   <li>登录成功</li>
 *   <li>登录时用户不存在</li>
 *   <li>登录时密码错误</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — 认证服务单元测试")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerReq;
    private LoginRequest loginReq;
    private User existingUser;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "test_user";
    private static final String PASSWORD = "pass123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final String ROLE_ATTENDEE = "attendee";
    private static final String TOKEN = "jwt.token.value";

    @BeforeEach
    void setUp() {
        registerReq = new RegisterRequest();
        registerReq.setUsername(USERNAME);
        registerReq.setPassword(PASSWORD);
        registerReq.setRole(ROLE_ATTENDEE);

        loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setPassword(PASSWORD);

        existingUser = new User();
        existingUser.setId(USER_ID);
        existingUser.setUsername(USERNAME);
        existingUser.setPassword(ENCODED_PASSWORD);
        existingUser.setRole(ROLE_ATTENDEE);
    }

    // ================================================================
    //  register — 注册
    // ================================================================

    @Nested
    @DisplayName("register() — 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("注册成功：返回 token、userId、username、role")
        void register_success() {
            // 1. 用户名不存在 → selectOne 返回 null
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            // 2. 密码加密
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            // 3. insert 后模拟 MyBatis-Plus 自动回填 userId
            when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(USER_ID);
                return 1;
            });
            // 4. JWT 生成
            when(jwtUtil.generateToken(USER_ID, USERNAME, ROLE_ATTENDEE)).thenReturn(TOKEN);

            // 执行
            AuthResponse response = authService.register(registerReq);

            // 验证
            assertNotNull(response);
            assertEquals(TOKEN, response.getToken());
            assertEquals(USER_ID, response.getUserId());
            assertEquals(USERNAME, response.getUsername());
            assertEquals(ROLE_ATTENDEE, response.getRole());

            // 验证 insert 的参数
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(USERNAME, savedUser.getUsername());
            assertEquals(ENCODED_PASSWORD, savedUser.getPassword());
            assertEquals(ROLE_ATTENDEE, savedUser.getRole());
            assertEquals("default.png", savedUser.getAvatarUrl());

            // 验证交互顺序
            verify(userMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(passwordEncoder).encode(PASSWORD);
            verify(jwtUtil).generateToken(anyLong(), eq(USERNAME), eq(ROLE_ATTENDEE));
            verifyNoMoreInteractions(userMapper, passwordEncoder, jwtUtil);
        }

        @Test
        @DisplayName("注册失败：用户名已存在 → BizException(CONFLICT)")
        void register_usernameAlreadyExists() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

            BizException ex = assertThrows(BizException.class,
                    () -> authService.register(registerReq));
            assertEquals(BizCode.CONFLICT.getCode(), ex.getCode());
            assertEquals("用户名已存在", ex.getMsg());

            // 验证后续操作均未发生
            verify(passwordEncoder, never()).encode(anyString());
            verify(userMapper, never()).insert(any(User.class));
            verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
        }
    }

    // ================================================================
    //  login — 登录
    // ================================================================

    @Nested
    @DisplayName("login() — 用户登录")
    class LoginTests {

        @Test
        @DisplayName("登录成功：用户名和密码匹配 → 返回 token、userId、username、role")
        void login_success() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(USER_ID, USERNAME, ROLE_ATTENDEE)).thenReturn(TOKEN);

            AuthResponse response = authService.login(loginReq);

            assertNotNull(response);
            assertEquals(TOKEN, response.getToken());
            assertEquals(USER_ID, response.getUserId());
            assertEquals(USERNAME, response.getUsername());
            assertEquals(ROLE_ATTENDEE, response.getRole());

            verify(userMapper).selectOne(any(LambdaQueryWrapper.class));
            verify(passwordEncoder).matches(PASSWORD, ENCODED_PASSWORD);
            verify(jwtUtil).generateToken(USER_ID, USERNAME, ROLE_ATTENDEE);
            verifyNoMoreInteractions(userMapper, passwordEncoder, jwtUtil);
        }

        @Test
        @DisplayName("登录失败：用户名不存在 → BizException(UNAUTHORIZED)")
        void login_userNotFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> authService.login(loginReq));
            assertEquals(BizCode.UNAUTHORIZED.getCode(), ex.getCode());
            assertEquals("用户名或密码错误", ex.getMsg());

            verify(passwordEncoder, never()).matches(anyString(), anyString());
            verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("登录失败：密码错误 → BizException(UNAUTHORIZED)")
        void login_wrongPassword() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            BizException ex = assertThrows(BizException.class,
                    () -> authService.login(loginReq));
            assertEquals(BizCode.UNAUTHORIZED.getCode(), ex.getCode());
            assertEquals("用户名或密码错误", ex.getMsg());

            verify(passwordEncoder).matches(PASSWORD, ENCODED_PASSWORD);
            verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyString());
        }
    }
}