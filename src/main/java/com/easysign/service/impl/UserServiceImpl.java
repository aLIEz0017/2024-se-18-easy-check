package com.easysign.service.impl;

import com.easysign.common.BizCode;
import com.easysign.common.BizException;
import com.easysign.dto.request.UpdateProfileRequest;
import com.easysign.dto.response.UserProfileResponse;
import com.easysign.entity.User;
import com.easysign.mapper.UserMapper;
import com.easysign.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用户服务实现
 * <p>
 * 提供用户信息的查询与更新功能。
 * tags 字段在数据库中存储为 JSON 字符串（如 ["科技","电竞"]），
 * 通过 Jackson ObjectMapper 与 List<String> 互转。
 * </p>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> VALID_GENDERS = Set.of("male", "female");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizCode.NOT_FOUND);
        }

        UserProfileResponse resp = new UserProfileResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        resp.setGender(user.getGender());
        resp.setTags(parseTags(user.getTags()));
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DTF) : null);
        return resp;
    }

    @Override
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        // 1. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizCode.NOT_FOUND);
        }

        // 2. 校验仅 attendee 可修改
        if (!"attendee".equals(user.getRole())) {
            throw new BizException(BizCode.FORBIDDEN, "仅参会者可修改个人信息");
        }

        // 3. 校验 gender 和 tags 至少传一个
        if (request.getGender() == null && request.getTags() == null) {
            throw new BizException(BizCode.PARAM_ERROR, "性别和偏好标签至少传一个");
        }

        // 4. 校验 gender 值合法
        if (request.getGender() != null && !VALID_GENDERS.contains(request.getGender())) {
            throw new BizException(BizCode.PARAM_ERROR, "性别只能是 male / female");
        }

        // 5. 更新字段
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getTags() != null) {
            user.setTags(serializeTags(request.getTags()));
        }

        userMapper.updateById(user);
    }

    /**
     * 将数据库中的 JSON 字符串解析为 List<String>
     */
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return null;
        }
        try {
            return Arrays.asList(OBJECT_MAPPER.readValue(tagsJson, String[].class));
        } catch (JsonProcessingException e) {
            return Collections.singletonList(tagsJson);
        }
    }

    /**
     * 将 List<String> 序列化为 JSON 字符串存入数据库
     */
    private String serializeTags(List<String> tags) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new BizException(BizCode.INTERNAL_ERROR, "标签序列化失败");
        }
    }
}
