package com.easysign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easysign.entity.Event;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 活动 Mapper
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper} 获得通用 CRUD。
 * 自定义方法 {@link #updateSoldTickets} 用于抢票时的防超卖原子更新。
 * </p>
 */
@Mapper
public interface EventMapper extends BaseMapper<Event> {

    /**
     * 防超卖原子操作：sold_tickets + 1（带条件 sold_tickets < total_tickets）
     * <p>
     * SQL：{@code UPDATE event SET sold_tickets = sold_tickets + 1
     * WHERE id = #{eventId} AND sold_tickets < total_tickets}
     * 返回受影响行数，为 0 表示库存不足。
     * </p>
     *
     * @param eventId 活动 ID
     * @return 受影响行数（1=成功，0=库存不足）
     */
    int updateSoldTickets(@Param("eventId") Long eventId);
}
