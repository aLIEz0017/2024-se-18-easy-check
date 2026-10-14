package com.easysign.ai;

import com.easysign.entity.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ⚠️ 废弃：特征工程类，当前项目中无注入方。
 * <p>
 * 该类原设计用于计算活动基准到达率、拼接 LLM Prompt，但实际参会率预测功能
 * 已由 {@link com.easysign.service.impl.DashboardServiceImpl} 中的四维加权模型
 * （历史签到率×0.50 + 热度×0.20 + 购票集中度×0.20 + 时段因子×0.10）完整替代，
 * 推荐理由 Prompt 拼接也由 {@link RecommendPromptBuilder} 独立负责。
 * 保留仅用于参考，后续版本可移除。
 * </p>
 */
@Slf4j
@Component
public class FeatureEngine {

    /**
     * ⚠️ 废弃：计算活动基准到达率，无调用方
     */
    public double calculateBaseRate(Event event) {
        // 1. 查询同类活动历史到达率（按 tags 匹配）— MVP 阶段返回固定值
        double historicalRate = 0.82;

        // 2. 计算当前售票率
        double sellRate = event.getTotalTickets() > 0
                ? (double) event.getSoldTickets() / event.getTotalTickets()
                : 0.0;

        // 3. 综合加权得出基准分
        double baseRate = historicalRate * 0.6 + sellRate * 0.4;
        log.info("FeatureEngine.calculateBaseRate: eventId={}, historicalRate={}, sellRate={}, baseRate={}",
                event.getId(), historicalRate, sellRate, baseRate);
        return baseRate;
    }

    /**
     * 拼接 Prompt（用于最终 LLM 调用）
     */
    public String buildPredictionPrompt(Event event, double baseRate) {
        double sellPercent = event.getTotalTickets() > 0
                ? (double) event.getSoldTickets() / event.getTotalTickets() * 100
                : 0.0;

        return String.format(
                "活动「%s」当前售票率 %.0f%%，历史同类到达率 82%%，基准得分 %.2f，请预测参会到达率并给出建议",
                event.getTitle(),
                sellPercent,
                baseRate
        );
    }
}