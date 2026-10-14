package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 总览响应体 —— GET /api/v1/dashboard/overview
 * <p>
 * 包含举办方所有活动的汇总统计、活动列表及图表数据。
 * 对齐接口设计文档 6.1 节规范。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    /** 当前是否有上线活动 */
    private Boolean hasOnlineEvent;

    /** 所有活动累计售票量（含 finished 活动） */
    private Long totalSoldTickets;

    /** 所有活动累计签到人数（含 finished 活动） */
    private Long totalCheckedIn;

    /** 活动总数（含 finished 活动） */
    private Long eventCount;

    /** 各活动简要数据列表 */
    private List<EventBrief> events;

    /** 图表数据 Map，key 为图表标识，value 包含 type 和 data */
    private Map<String, Object> charts;

    /**
     * 活动简要信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventBrief {

        /** 活动 ID */
        private Long eventId;

        /** 活动标题 */
        private String title;

        /** 总票数 */
        private Integer totalTickets;

        /** 已售票数 */
        private Integer soldTickets;

        /** 已签到人数 */
        private Integer checkedIn;

        /** 状态：draft / online / offline / finished */
        private String status;

        /** 开始时间，格式 yyyy-MM-dd HH:mm:ss */
        private String startTime;

        /** 结束时间，格式 yyyy-MM-dd HH:mm:ss */
        private String endTime;

        /** 活动地点 */
        private String location;

        /**
         * 预测到场率
         * <p>
         * 由 {@link com.easysign.service.impl.DashboardServiceImpl#calculateArrivalRate} 中的
         * 四维加权模型（历史签到率×0.50 + 热度×0.20 + 购票集中度×0.20 + 时段因子×0.10）计算得出，
         * 取值范围 0.0 ~ 1.0。
         * </p>
         */
        private Double arrivalRate;

        /**
         * 预测到场率（前端兼容字段名）
         * <p>
         * 与 arrivalRate 值相同，仅字段名不同以兼容前端旧版调用。
         * </p>
         */
        private Double predictedArrivalRate;

    }
}
