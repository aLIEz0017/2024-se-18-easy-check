package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 活动响应 DTO
 * <p>
 * 同时服务于 C 端（公开列表、详情）和 B 端（举办方列表）。
 * C 端不返回 {@link #updatedAt}；推荐接口 7.1 额外返回 {@link #reason} 字段。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {

    /** 活动 ID（主键） */
    private Long eventId;

    /** 活动标题 */
    private String title;

    /** 活动描述 */
    private String description;

    /** 活动地点 */
    private String location;

    /** 活动标签 */
    private List<String> tags;

    /** 活动开始时间（字符串，如 "2026-07-08 14:00"） */
    private String startTime;

    /** 活动结束时间（字符串，如 "2026-07-08 18:00"） */
    private String endTime;

    /** 门票总数 */
    private Integer totalTickets;

    /** 已售门票数 */
    private Integer soldTickets;

    /** 活动状态（draft / online / offline / finished） */
    private String status;

    /** 是否开启选座 */
    private Boolean hasSeats;

    /** 创建时间（字符串格式） */
    private String createdAt;

    /** 最后更新时间（B 端填充、C 端为 null） */
    private String updatedAt;

    /**
     * 推荐原因（推荐接口 7.1 专用）
     * <p>
     * 说明系统推荐该活动的依据，例如 "根据你购买过的活动类型推荐"、"热门活动推荐" 等。
     * 非推荐场景下该字段为 null。
     * </p>
     */
    private String reason;
}
