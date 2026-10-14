package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 用户信息响应体 —— GET /api/v1/user/profile
 * <p>
 * 字段说明：
 * - userId / username / role 与 Token 中的信息一致
 * - gender / tags 仅 attendee 有值，organizer 为 null
 * - avatarUrl 当前固定为 "default.png"
 * - createdAt 格式为 yyyy-MM-dd HH:mm:ss
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色：attendee / organizer */
    private String role;

    /** 性别：male / female，举办方为 null */
    private String gender;

    /** 偏好标签数组，举办方为 null */
    private List<String> tags;

    /** 头像 URL（当前固定为 default.png） */
    private String avatarUrl;

    /** 注册时间，格式 yyyy-MM-dd HH:mm:ss */
    private String createdAt;
}
