package com.easysign.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.easysign.ai.LlmConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置
 * <p>
 * 使用 Caffeine 实现推荐理由缓存，避免重复调用 LLM。
 * </p>
 */
@Configuration
public class CacheConfig {

    @Autowired
    private LlmConfig llmConfig;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("recommendReason");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(llmConfig.getRecommend().getCacheTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats());
        return cacheManager;
    }
}