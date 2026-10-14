package com.easysign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 座位实体
 * <p>
 * 对应 seat 表，记录活动的排、列、标签及销售状态。
 * 每个座位通过 (eventId, seatRow, seatCol) 唯一标识。
 * </p>
 */
@Data
@TableName("seat")
public class Seat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联活动ID */
    private Long eventId;

    /** 排号 */
    private Integer seatRow;

    /** 列号 */
    private Integer seatCol;

    /** 座位标签，如"1-1" */
    private String seatLabel;

    /** 座位状态：available=可选, sold=已售出 */
    private String status;
}