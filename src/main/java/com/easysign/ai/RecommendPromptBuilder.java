package com.easysign.ai;

/**
 * 推荐理由专用 Prompt 构建器。
 * <p>
 * 独立于 {@link com.easysign.service.impl.DashboardServiceImpl} 中的四维加权模型，
 * 各自关注不同的 AI 场景：
 * <ul>
 *   <li>{@code RecommendPromptBuilder} — 推荐理由文案生成</li>
 *   <li>{@code DashboardServiceImpl} — 特征分析与参会率预测（纯 DB 统计）</li>
 * </ul>
 * </p>
 */
public class RecommendPromptBuilder {

    /**
     * 构建生成推荐理由的 Prompt。
     *
     * @param userTags         用户偏好标签 JSON（可能为 null）
     * @param eventTitle       活动标题
     * @param eventTags        活动标签 JSON（可能为 null）
     * @param eventDescription 活动简介（可能为 null）
     * @param sellRate         售票率（0.0 ~ 1.0）
     * @return 完整的 Prompt 字符串
     */
    public static String buildPrompt(String userTags, String eventTitle,
                                      String eventTags, String eventDescription, double sellRate) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个高校活动推荐助手。请根据以下信息生成一句简洁、自然的推荐理由（30字以内）：\n\n");

        if (userTags != null && !userTags.isBlank()) {
            sb.append("用户偏好标签：").append(userTags).append("\n");
        }

        sb.append("活动标题：").append(eventTitle).append("\n");

        if (eventDescription != null && !eventDescription.isBlank()) {
            sb.append("活动简介：").append(eventDescription).append("\n");
        }

        sb.append("活动标签：").append(eventTags != null && !eventTags.isBlank() ? eventTags : "无").append("\n");
        sb.append(String.format("售票率：%.0f%%\n", sellRate * 100));
        sb.append("\n要求：语气亲切，不要客套话，直接说明推荐原因。");

        return sb.toString();
    }
}