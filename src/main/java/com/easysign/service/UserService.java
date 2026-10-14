package com.easysign.service;

import com.easysign.dto.request.UpdateProfileRequest;
import com.easysign.dto.response.UserProfileResponse;

/**
 * 用户服务接口 —— 用户信息查询与更新
 * <p>
 * 对应接口：
 * - GET /api/v1/user/profile → getProfile()
 * - PUT /api/v1/user/profile → updateProfile()
 */
public interface UserService {

    /**
     * 获取当前用户信息
     *
     * @param userId 当前登录用户 ID
     * @return 用户信息响应体
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 更新用户信息
     * <p>
     * 约束：
     * - 仅 role = attendee 可修改
     * - gender 和 tags 至少传一个
     * - gender 必须是 male / female 之一
     *
     * @param userId  当前登录用户 ID
     * @param request 更新请求体
     */
    void updateProfile(Long userId, UpdateProfileRequest request);
}
