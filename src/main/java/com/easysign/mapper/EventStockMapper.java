package com.easysign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easysign.entity.EventStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动库存 Mapper
 * <p>
 * 维护 event_stock 独立库存表的原子扣减操作。
 * 与 event 主表解耦，减少秒杀场景下的行锁竞争。
 * </p>
 */
@Mapper
public interface EventStockMapper extends BaseMapper<EventStock> {

    /**
     * 原子扣减库存（防超卖）
     * <p>
     * SQL: {@code UPDATE event_stock SET sold_tickets = sold_tickets + 1
     * WHERE event_id = #{eventId} AND sold_tickets < total_tickets}
     * 返回 1 表示扣减成功，0 表示库存不足。
     * </p>
     *
     * @param eventId 活动ID
     * @return 受影响行数
     */
    int decreaseStock(@Param("eventId") Long eventId);

    /**
     * 恢复库存（补偿用）
     * <p>
     * SQL: {@code UPDATE event_stock SET sold_tickets = sold_tickets - 1
     * WHERE event_id = #{eventId} AND sold_tickets > 0}
     * 用于异步落单队列满时，补偿恢复已扣减的库存。
     * </p>
     *
     * @param eventId 活动ID
     * @return 受影响行数
     */
    int increaseStock(@Param("eventId") Long eventId);

    /**
     * 初始化/重置活动库存
     * <p>
     * SQL: {@code INSERT INTO event_stock (event_id, total_tickets, sold_tickets)
     * VALUES (#{eventId}, #{totalTickets}, 0)
     * ON DUPLICATE KEY UPDATE total_tickets = #{totalTickets}, sold_tickets = 0}
     * 用于活动上线或更新总票数时重置库存。
     * </p>
     *
     * @param eventId      活动ID
     * @param totalTickets 总票数
     * @return 受影响行数
     */
    int initStock(@Param("eventId") Long eventId, @Param("totalTickets") Integer totalTickets);
}
