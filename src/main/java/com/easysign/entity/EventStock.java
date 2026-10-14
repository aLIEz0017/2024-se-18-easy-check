package com.easysign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 活动库存独立表实体
 * <p>
 * 用于秒杀场景下将库存操作与 event 主表解耦，
 * 通过原子 UPDATE 实现防超卖，减少行锁竞争。
 * </p>
 */
@Data
@TableName("event_stock")
public class EventStock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Integer totalTickets;

    private Integer soldTickets;

    /** 乐观锁版本号（预留，当前未启用） */
    private Integer version;
}