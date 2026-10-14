package com.easysign.ratelimit;

/**
 * 限流判定结果
 * <p>
 * 封装限流检查的详细信息，包括是否允许通过、当前窗口剩余次数、
 * 重置时间（秒）、以及窗口大小（秒），用于在响应头中反馈给客户端。
 * </p>
 */
public class RateLimitResult {

    /** 是否允许请求通过 */
    private final boolean allowed;

    /** 当前窗口剩余可用次数 */
    private final int remaining;

    /** 窗口重置前的剩余秒数（近似值） */
    private final long resetAfterSeconds;

    /** 窗口大小（秒） */
    private final long windowSeconds;

    public RateLimitResult(boolean allowed, int remaining, long resetAfterSeconds, long windowSeconds) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.resetAfterSeconds = resetAfterSeconds;
        this.windowSeconds = windowSeconds;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetAfterSeconds() {
        return resetAfterSeconds;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}