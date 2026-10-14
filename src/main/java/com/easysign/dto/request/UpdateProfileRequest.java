package com.easysign.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 更新用户信息请求体 —— PUT /api/v1/user/profile
 * <p>
 * 约束（由 Service 层校验）：
 * - 仅 role = attendee 的用户可修改
 * - gender 和 tags 至少传一个
 * - gender 必须是 male / female 之一
 * - tags 为字符串数组，如 ["科技", "电竞"]
 */
@Data
public class UpdateProfileRequest {

    /** 性别：male / female */
    private String gender;

    /** 偏好标签数组，如 ["科技", "电竞"] */
    private List<String> tags;
}
