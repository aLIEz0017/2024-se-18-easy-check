package com.easysign.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 抢票接口限流器
 * <p>
 * 基于 Caffeine 本地缓存的滑动窗口实现用户级别限流。
 * 默认限制：每用户每 eventId 每 3 秒最多 1 次请求。
 * </p>
 */
@Slf4j
@Component
public class PurchaseRateLimiter {

    /** 限流窗口大小（秒） */
    private static final int WINDOW_SECONDS = 3;

    /** 每个窗口允许的最大请求数 */
    private static final int MAX_REQUESTS = 1;

    /**
     * key 格式: "userId:eventId", value: 当前窗口内的请求计数
     */
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW_SECONDS, TimeUnit.SECONDS)
            .build();

    /**
     * 判断指定用户对指定活动的抢票请求是否允许通过
     * <p>
     * 返回 {@link RateLimitResult} 包含详细的限流状态（是否允许、剩余次数、重置时间），
     * 便于 Controller 层将限流信息写入响应头。
     * </p>
     *
     * @param userId  用户ID
     * @param eventId 活动ID
     * @return 限流判定结果
     */
    public RateLimitResult tryAcquire(Long userId, Long eventId) {
        String key = userId + ":" + eventId;
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        int remaining = Math.max(0, MAX_REQUESTS - count);
        long resetAfterSeconds = WINDOW_SECONDS;

        if (count > MAX_REQUESTS) {
            log.warn("[限流] 用户 {} 请求活动 {} 过于频繁, count={}", userId, eventId, count);
            return new RateLimitResult(false, 0, resetAfterSeconds, WINDOW_SECONDS);
        }
        return new RateLimitResult(true, remaining, resetAfterSeconds, WINDOW_SECONDS);
    }
}
