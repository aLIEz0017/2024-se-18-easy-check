package com.easysign.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 4.1 发布活动专用轻量返回
 * <p>仅包含新活动的 ID 和初始状态 {@code draft}，减少不必要的字段传输。</p>
 */
@Data
@AllArgsConstructor
public class CreateEventResponse {

    /** 新创建的活动 ID（主键） */
    private Long eventId;

    /** 活动初始状态，固定为 {@code "draft"} */
    private String status;
}