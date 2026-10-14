package com.easysign.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置类
 * <p>
 * 通过 {@link ConfigurationProperties} 绑定 {@code llm.*} 配置项。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /** 是否启用 LLM 生成推荐理由 */
    private boolean enabled = false;

    /** 提供商名称：mock / deepseek */
    private String provider = "mock";

    /** API 通用配置 */
    private ApiConfig api = new ApiConfig();

    /** 推荐理由相关配置 */
    private RecommendConfig recommend = new RecommendConfig();

    @Data
    public static class ApiConfig {
        /** API 基础地址 */
        private String baseUrl = "https://api.deepseek.com";

        /** API Key */
        private String key;

        /** 模型名称 */
        private String model = "deepseek-chat";

        /** 超时时间（毫秒） */
        private int timeoutMs = 2000;

        /** 最大重试次数 */
        private int maxRetries = 2;
    }

    @Data
    public static class RecommendConfig {
        /** 推荐理由最大 Token 数 */
        private int maxTokens = 100;

        /** 缓存 TTL（分钟） */
        private int cacheTtlMinutes = 5;
    }
}