package com.easysign.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 票务响应 DTO
 * <p>
 * 覆盖以下四个接口的返回值（各接口按需呈现部分字段，未使用的字段为 {@code null}）：
 * <ul>
 *   <li>5.1 抢票：返回 {@link #ticketId}、{@link #ticketNo}、{@link #status}</li>
 *   <li>5.2 票夹：返回 {@link #eventId}、{@link #eventTitle}、{@link #eventLocation}、{@link #eventStartTime}、{@link #eventEndTime}、{@link #eventStatus}、{@link #status}、{@link #createdAt}</li>
 *   <li>5.3 单票：返回全部字段</li>
 *   <li>5.5 签到明细：返回 {@link #ticketId}、{@link #ticketNo}、{@link #eventTitle}、{@link #status}、{@link #username}、{@link #checkedAt}</li>
 * </ul>
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketResponse {

    /** 票 ID（主键） */
    private Long ticketId;

    /** 票号（对外展示的唯一凭证） */
    private String ticketNo;

    /** 关联活动标题 */
    private String eventTitle;

    /** 活动地点 */
    private String eventLocation;

    /** 活动开始时间（字符串，如 "2026-07-08 14:00"） */
    private String eventStartTime;

    /** 活动结束时间（字符串，如 "2026-07-08 18:00"） */
    private String eventEndTime;

    /** 活动状态（draft / online / offline / finished） */
    private String eventStatus;

    /** 票务状态（purchased / checked / refunded） */
    private String status;

    /** 持有者用户名 */
    private String username;

    /** 购票时间（字符串格式） */
    private String createdAt;

    /** 签到时间（字符串格式，未签到时为 null） */
    private String checkedAt;

    /** 关联活动 ID */
    private Long eventId;

    /** 关联座位ID，NULL=无选座 */
    private Long seatId;

    /** 座位标签，如"1-1"（NULL=无选座） */
    private String seatLabel;
}