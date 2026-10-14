package com.easysign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    /**
     * attendee / organizer
     */
    private String role;

    /**
     * male / female
     */
    private String gender;

    /**
     * 偏好标签 JSON 数组，如 ["科技","电竞"]
     */
    private String tags;

    private String avatarUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
