package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 活动推荐响应 DTO
 * <p>
 * 推荐接口专用，包含活动基本信息及推荐原因。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendResponse {

    /** 活动 ID */
    private Long eventId;

    /** 活动标题 */
    private String title;

    /** 活动描述 */
    private String description;

    /** 活动地点 */
    private String location;

    /** 活动开始时间（字符串，如 "2026-07-08 14:00"） */
    private String startTime;

    /** 活动结束时间（字符串，如 "2026-07-08 18:00"） */
    private String endTime;

    /** 门票总数 */
    private Integer totalTickets;

    /** 已售门票数 */
    private Integer soldTickets;

    /** 活动状态 */
    private String status;

    /** 创建时间 */
    private String createdAt;

    /** 推荐原因 */
    private String reason;
}