package com.easysign.ai;

/**
 * AI 服务接口
 * MVP 阶段使用 MockAiService 占位实现
 */
public interface AiService {

    /**
     * 一键生成文案
     *
     * @param title 活动标题
     * @return 生成的描述文案
     */
    String generateDescription(String title);

    /**
     * 参会率预测
     * <p>
     * ⚠️ 废弃说明：该方法目前无调用方，实际参会率预测由
     * {@link com.easysign.service.impl.DashboardServiceImpl} 中的四维加权模型
     * （历史签到率×0.50 + 热度×0.20 + 购票集中度×0.20 + 时段因子×0.10）提供。
     * 保留接口仅用于接口兼容，后续版本可移除。
     * </p>
     *
     * @param eventId 活动 ID
     * @return 预测的到达率（0.0 ~ 1.0）
     */
    double predictArrivalRate(Long eventId);

    /**
     * 生成个性化推荐理由（可选增强层）
     *
     * @param userTags         用户偏好标签 JSON 字符串（可能为 null）
     * @param eventTitle       活动标题
     * @param eventTags        活动标签 JSON 字符串（可能为 null）
     * @param eventDescription 活动简介（可能为 null）
     * @param sellRate         售票率（0.0 ~ 1.0）
     * @param hasTagMatch      是否存在标签匹配
     * @return 推荐理由文案；失败/异常时返回 null 表示需降级
     */
    String generateRecommendReason(String userTags, String eventTitle, String eventTags,
                                    String eventDescription, double sellRate, boolean hasTagMatch);
}