package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 活动数据详情响应体 —— GET /api/v1/dashboard/event/{eventId}
 * <p>
 * 包含单个活动的详细数据指标、图表和签到明细。
 * 用于前端 EventDataDetailView 页面展示。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventDetailResponse {

    /** 活动 ID */
    private Long eventId;

    /** 活动标题 */
    private String title;

    /** 开始时间（格式化字符串） */
    private String startTime;

    /** 结束时间（格式化字符串） */
    private String endTime;

    /** 活动地点 */
    private String location;

    /** 活动状态：draft / online / offline / finished */
    private String status;

    /** 门票总数 */
    private Integer totalTickets;

    /** 已售门票数 */
    private Integer soldTickets;

    /** 已签到人数 */
    private Integer checkedIn;

    /** 图表数据 */
    private Map<String, Object> charts;

    /** 签到明细列表 */
    private List<Map<String, Object>> checkinDetails;
}
