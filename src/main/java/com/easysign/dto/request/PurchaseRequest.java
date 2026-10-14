package com.easysign.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 5.1 抢票请求体
 * <p>
 * 包含活动 ID。当活动开启选座时需传入 seatId。
 * </p>
 */
@Data
public class PurchaseRequest {

    /** 活动 ID（必填） */
    @NotNull(message = "活动ID不能为空")
    private Long eventId;

    /** 座位 ID。仅 hasSeats=true 的活动需传入；不开启选座时忽略该字段 */
    private Long seatId;
}