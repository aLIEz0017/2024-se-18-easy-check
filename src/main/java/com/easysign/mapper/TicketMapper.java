package com.easysign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.easysign.dto.response.TicketResponse;
import com.easysign.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 票务 Mapper
 * <p>
 * 继承 MyBatis-Plus 的 {@link BaseMapper} 获得通用 CRUD。
 * 自定义方法通过 {@code src/main/resources/mapper/TicketMapper.xml} 提供联表查询
 * 和原子更新操作。由另一成员维护，本模块仅引用。
 * </p>
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    /**
     * 5.2 票夹列表联表查询
     * <p>关联 ticket 表与 event 表，按用户 ID 过滤，支持 status 筛选。</p>
     */
    IPage<TicketResponse> selectMyTicketsWithEvent(Page<?> page, @Param("userId") Long userId, @Param("status") String status);

    /**
     * 5.5 签到明细联表查询
     * <p>关联 ticket 表与 user 表，按活动 ID 过滤，支持 status 筛选。</p>
     */
    IPage<TicketResponse> selectCheckinsWithUser(Page<?> page, @Param("eventId") Long eventId, @Param("status") String status);

    /**
     * 签到原子更新
     * <p>
     * SQL：{@code UPDATE ticket SET status = 'checked', checked_at = NOW()
     * WHERE id = #{ticketId} AND status = 'pending'}
     * 返回受影响行数（1=成功，0=重复签到或 ticket 状态异常）。
     * </p>
     */
    int updateCheckin(@Param("ticketId") Long ticketId);

    /**
     * 删除活动时批量置无效
     * <p>将指定活动下的所有 ticket 状态设为 {@code invalid}。</p>
     */
    int batchInvalidateByEventId(@Param("eventId") Long eventId);

    /**
     * 活动下线时批量置无效（仅 pending 状态的票）
     * <p>将指定活动下所有 {@code pending} 状态的 ticket 改为 {@code invalid}。</p>
     */
    int batchInvalidatePendingByEventId(@Param("eventId") Long eventId);

}
