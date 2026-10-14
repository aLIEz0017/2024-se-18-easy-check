package com.easysign.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务码枚举
 */
@Getter
@AllArgsConstructor
public enum BizCode {

    SUCCESS(200, "成功"),

    // 4xxx — 请求/参数/认证/业务错误
    PARAM_ERROR(4000, "请求参数错误"),
    UNAUTHORIZED(4001, "认证失败"),
    FORBIDDEN(4002, "权限不足"),
    NOT_FOUND(4003, "资源不存在"),
    CONFLICT(4004, "资源冲突"),
    BIZ_ERROR(4100, "业务逻辑错误"),

    // 签到专用
    CHECKIN_ALREADY_CHECKED(4101, "已签到，请勿重复签到"),
    CHECKIN_EVENT_NOT_STARTED(4102, "活动尚未开始"),
    CHECKIN_EVENT_FINISHED(4103, "活动已结束"),
    CHECKIN_INVALID_TICKET(4104, "票务信息无效"),
    CHECKIN_NOT_ORGANIZER(4105, "非本活动举办方，无法签到"),
    CHECKIN_EVENT_OFFLINE(4106, "活动已下线，无法签到"),
    CHECKIN_TICKET_REFUNDED(4107, "票已退票，无法签到"),

    // 限流
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 5xxx — 服务器内部错误
    INTERNAL_ERROR(5000, "服务器内部错误");

    private final int code;
    private final String msg;

}