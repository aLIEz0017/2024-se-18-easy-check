package com.easysign.service;

import com.easysign.dto.response.DashboardResponse;
import com.easysign.dto.response.EventDetailResponse;
import com.easysign.dto.response.PredictionResponse;

/**
 * Dashboard 服务接口 —— 举办方仪表盘
 * <p>
 * 对应接口：
 * - GET /api/v1/dashboard/overview          → getOverview()
 * - GET /api/v1/dashboard/event/{eventId}   → getEventDetail()
 * - GET /api/v1/dashboard/arrival-rate/{id} → getArrivalRate()
 * </p>
 */
public interface DashboardService {

    /**
     * 获取举办方仪表盘总览数据
     *
     * @param userId 当前登录用户 ID（举办方）
     * @return 总览响应体，含统计汇总、最近活动列表及图表数据
     */
    DashboardResponse getOverview(Long userId);

    /**
     * 获取单个活动的详细数据指标和图表数据
     *
     * @param userId  当前登录用户 ID（举办方）
     * @param eventId 活动 ID
     * @return 活动数据详情响应体
     */
    EventDetailResponse getEventDetail(Long userId, Long eventId);

    /**
     * 获取指定活动的预测到场率及分析建议
     *
     * @param userId  当前登录用户 ID（举办方）
     * @param eventId 活动 ID
     * @return 预测到场率、四项因子、分析原因和建议
     */
    PredictionResponse getArrivalRate(Long userId, Long eventId);
}


