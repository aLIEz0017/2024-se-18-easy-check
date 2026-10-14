package com.easysign.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 4.4 更新活动请求体
 * <p>
 * 全部字段可选，传 {@code null} 表示不修改该字段。
 * 支持部分更新：仅修改传入的非 null 字段。
 * </p>
 */
@Data
public class UpdateEventRequest {

    /** 新标题（传 null 不修改） */
    private String title;

    /** 新描述（传 null 不修改） */
    private String description;

    /** 新地点（传 null 不修改） */
    private String location;

    /** 新开始时间（传 null 不修改，格式：yyyy-MM-dd HH:mm:ss） */
    private String startTime;

    /** 新结束时间（传 null 不修改，格式：yyyy-MM-dd HH:mm:ss） */
    private String endTime;

    /** 新门票总数（传 null 不修改；不能低于已售数量） */
    private Integer totalTickets;

    /** 新标签（传 null 不修改，必须完整的标签列表，不支持增量更新） */
    private List<String> tags;

    /** 是否开启选座（传 null 不修改） */
    private Boolean hasSeats;
}
