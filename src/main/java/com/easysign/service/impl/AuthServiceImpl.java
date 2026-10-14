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
import com.easysign.service.AuthService;
import com.easysign.util.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现 —— 注册与登录的核心业务逻辑
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserMapper userMapper,
                           BCryptPasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        // 1. 检查用户名是否已存在
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (existingUser != null) {
            throw new BizException(BizCode.CONFLICT, "用户名已存在");
        }

        // 2. 创建新用户实体
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setAvatarUrl("default.png");

        // 3. 保存到数据库
        userMapper.insert(user);

        // 4. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 5. 返回认证响应
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. 根据用户名查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new BizException(BizCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 2. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(BizCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 3. 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 4. 返回认证响应
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}