package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 预测详情响应体 —— GET /api/v1/dashboard/arrival-rate/{id}
 * <p>
 * 包含指定活动的预测到场率及相关建议。
 * 预测基于四维加权模型（纯 DB 统计），非 LLM 预测。
 * 参见 {@link com.easysign.service.impl.DashboardServiceImpl}。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PredictionResponse {

    /** 活动 ID */
    private Long eventId;

    /** 活动标题 */
    private String eventTitle;

    /** 预测到场率，取值范围 0.0 ~ 1.0 */
    private Double arrivalRate;

    /** 预测到场率（前端兼容字段名，与 arrivalRate 值相同） */
    private Double predictedArrivalRate;

    /** 举办方已结束活动的历史签到率，权重 50% */
    private Double historicalCheckinRate;

    /** 当前活动售票率，权重 20% */
    private Double popularity;

    /** 当前活动购票集中度因子，权重 20% */
    private Double purchaseConcentration;

    /** 当前活动开始时段因子，权重 10% */
    private Double timeFactor;

    /** 根据四项因子生成的预测理由 */
    private String reason;

    /** 根据预测结果和售票情况生成的运营建议 */
    private String suggestion;

}
