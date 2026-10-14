package com.easysign.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 服务 Mock 实现（MVP 占位）
 * 后续替换为真实的 LLM API 调用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiService implements AiService {

    @Override
    public String generateDescription(String title) {
        log.info("MockAiService.generateDescription: title={}", title);
        return String.format(
                "欢迎参加【%s】！本次活动将为您带来精彩纷呈的体验，不容错过。我们精心准备了丰富的环节，" +
                        "期待与您共同度过一段难忘的时光。名额有限，请尽快报名！",
                title
        );
    }

    /**
     * ⚠️ 废弃：该方法目前无调用方，保留仅用于接口兼容，后续版本可移除。
     */
    @Override
    public double predictArrivalRate(Long eventId) {
        // 返回 0.6 ~ 0.95 之间的随机值
        double rate = ThreadLocalRandom.current().nextDouble(0.6, 0.95);
        log.info("MockAiService.predictArrivalRate: eventId={}, rate={}", eventId, rate);
        return rate;
    }

    @Override
    public String generateRecommendReason(String userTags, String eventTitle, String eventTags,
                                           String eventDescription, double sellRate,
                                           boolean hasTagMatch) {
        log.info("MockAiService.generateRecommendReason: title={}, sellRate={}, hasTagMatch={}",
                eventTitle, sellRate, hasTagMatch);

        // 格式化售票率为百分比
        String sellRatePercent = String.format("%.0f", sellRate * 100);
        // 从 userTags JSON 中提取第一个标签作为显示标签
        String firstTag = extractFirstTag(userTags);

        if (hasTagMatch && sellRate > 0) {
            return String.format("根据你的【%s】偏好，推荐【%s】，售票率已达 %s%%",
                    firstTag, eventTitle, sellRatePercent);
        } else if (hasTagMatch) {
            return String.format("根据你的【%s】偏好推荐【%s】", firstTag, eventTitle);
        } else if (sellRate > 0) {
            return String.format("热门推荐【%s】，售票率 %s%%", eventTitle, sellRatePercent);
        } else {
            return String.format("热门推荐【%s】", eventTitle);
        }
    }

    /**
     * 从用户标签 JSON 字符串中提取第一个标签值。
     * 例如：["科技","电竞"] → "科技"
     */
    private String extractFirstTag(String userTagsJson) {
        if (userTagsJson == null || userTagsJson.isBlank()) {
            return "热门";
        }
        // 简单解析：去掉 [、"、]、逗号，取第一个逗号前的值
        String trimmed = userTagsJson.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        trimmed = trimmed.replace("\"", "").trim();
        if (trimmed.isEmpty()) {
            return "热门";
        }
        int commaIndex = trimmed.indexOf(',');
        if (commaIndex > 0) {
            return trimmed.substring(0, commaIndex).trim();
        }
        return trimmed;
    }
}