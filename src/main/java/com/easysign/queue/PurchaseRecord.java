package com.easysign.queue;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 抢票记录（内存队列消息体）
 * <p>
 * 在事务提交后（afterCommit）异步写入 ticket 表，
 * 将扣库存与创建订单解耦，减少 DB 事务持有时间。
 * </p>
 */
@Data
@AllArgsConstructor
public class PurchaseRecord {

    /** 用户ID */
    private Long userId;

    /** 活动ID */
    private Long eventId;

    /** 座位ID（可为 null） */
    private Long seatId;
}