package com.easysign.controller;

import com.easysign.common.Result;
import com.easysign.context.UserContext;
import com.easysign.dto.request.UpdateProfileRequest;
import com.easysign.dto.response.UserProfileResponse;
import com.easysign.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息 Controller（接口编号 3.x）
 * <p>
 * 提供用户信息的查询与更新能力。
 * 所有接口均需登录（由 {@link com.easysign.config.WebMvcConfig} 拦截器统一处理）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 3.1 获取当前用户信息
     * <p>
     * 从 JWT Token 中解析 userId，返回用户的完整信息。
     * 举办方调用时 gender 和 tags 为 null。
     * </p>
     *
     * @return 用户信息响应体
     */
    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        UserProfileResponse resp = userService.getProfile(UserContext.getUserId());
        return Result.success(resp);
    }

    /**
     * 3.2 更新用户信息
     * <p>
     * 仅 role = attendee 的用户可修改。
     * gender 和 tags 至少传一个。
     * </p>
     *
     * @param request 更新请求体（gender / tags）
     * @return 操作结果
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(UserContext.getUserId(), request);
        return Result.success("更新成功", null);
    }
}