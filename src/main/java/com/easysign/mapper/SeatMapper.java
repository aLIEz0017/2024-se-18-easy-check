package com.easysign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easysign.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位 Mapper
 * <p>
 * 提供座位查询、原子占用、批量插入操作。
 * 继承 MyBatis-Plus BaseMapper 获得通用 CRUD。
 * </p>
 */
@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 原子占用座位：将 available → sold
     * <p>
     * SQL：UPDATE seat SET status = 'sold'
     * WHERE id = #{seatId} AND status = 'available' AND event_id = #{eventId}
     * 返回受影响行数（1=成功，0=座位已被占用或不存在）。
     * </p>
     *
     * @param seatId  座位ID
     * @param eventId 活动ID（防止跨活动操作）
     * @return 受影响行数
     */
    int occupySeat(@Param("seatId") Long seatId, @Param("eventId") Long eventId);

    /**
     * 释放座位：将 sold → available
     * <p>
     * SQL：UPDATE seat SET status = 'available'
     * WHERE id = #{seatId} AND status = 'sold' AND event_id = #{eventId}
     * </p>
     *
     * @param seatId  座位ID
     * @param eventId 活动ID
     * @return 受影响行数
     */
    int releaseSeat(@Param("seatId") Long seatId, @Param("eventId") Long eventId);

    /**
     * 批量插入座位记录。
     *
     * @param list 座位列表
     * @return 插入行数
     */
    int insertBatch(@Param("list") List<Seat> list);

    /**
     * 根据活动ID查询所有座位（按排号、列号升序排列）。
     *
     * @param eventId 活动ID
     * @return 座位列表
     */
    List<Seat> selectByEventId(@Param("eventId") Long eventId);
}
