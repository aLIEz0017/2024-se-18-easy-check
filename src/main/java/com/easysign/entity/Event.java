package com.easysign.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event")
public class Event {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long organizerId;

    private String title;

    private String description;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalTickets;

    private Integer soldTickets;

    /**
     * 活动标签（JSON 数组字符串，如 ["科技","电竞"]）
     */
    private String tags;

    /**
     * draft / online / offline / finished
     */
    private String status;

    /**
     * 是否开启选座：false=不开启，true=开启
     */
    private Boolean hasSeats;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
