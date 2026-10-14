package com.easysign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private Long userId;

    private String ticketNo;

    /**
     * 关联座位ID，NULL=无选座
     */
    private Long seatId;

    /**
     * pending / checked / invalid / refunded
     */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime checkedAt;
}