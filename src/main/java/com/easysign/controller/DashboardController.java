package com.easysign.controller;

import com.easysign.common.Result;
import com.easysign.context.UserContext;
import com.easysign.dto.response.DashboardResponse;
import com.easysign.dto.response.EventDetailResponse;
import com.easysign.dto.response.PredictionResponse;

import com.easysign.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard 控制器（接口编号 6.x）
 * <p>
 * 提供举办方仪表盘的总览数据与 AI 参会率预测能力。
 * 所有接口均需登录（由 {@link com.easysign.config.WebMvcConfig} 拦截器统一处理）。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 6.1 总览
     * <p>
     * 获取当前举办方的仪表盘汇总数据，包括活动统计、最近活动列表及图表数据。
     * </p>
     *
     * @return 总览响应体
     */
    @GetMapping("/overview")
    public Result<DashboardResponse> getOverview() {
        DashboardResponse resp = dashboardService.getOverview(UserContext.getUserId());
        return Result.success(resp);
    }

    /**
     * 6.2 活动数据详情
     * <p>
     * 获取单个活动的详细数据指标和图表数据。
     * </p>
     *
     * @param eventId 活动 ID
     * @return 活动数据详情响应体
     */
    @GetMapping("/event/{eventId}")
    public Result<EventDetailResponse> getEventDetail(@PathVariable Long eventId) {
        EventDetailResponse resp = dashboardService.getEventDetail(UserContext.getUserId(), eventId);
        return Result.success(resp);
    }

    /**
     * 6.3 参会率预测
     * <p>
     * 使用四因子加权模型预测指定活动的实际到场率。
     * </p>
     *
     * @param eventId 活动 ID
     * @return 包含 eventId 和 arrivalRate 的响应体
     */
    @GetMapping("/arrival-rate/{eventId}")
    public Result<PredictionResponse> getArrivalRate(@PathVariable Long eventId) {
        PredictionResponse resp = dashboardService.getArrivalRate(UserContext.getUserId(), eventId);
        return Result.success(resp);
    }
}
