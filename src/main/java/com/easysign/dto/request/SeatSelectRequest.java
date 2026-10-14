package com.easysign.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 选座请求体
 * <p>
 * 用户指定活动ID和座位ID，系统以原子操作占用座位。
 * 需要 JWT 鉴权（购票用户）。
 * </p>
 */
@Data
public class SeatSelectRequest {

    /** 活动ID（必填） */
    @NotNull(message = "活动ID不能为空")
    private Long eventId;

    /** 座位ID（必填） */
    @NotNull(message = "座位ID不能为空")
    private Long seatId;
}