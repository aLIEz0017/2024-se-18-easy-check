package com.easysign.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 5.4 签到请求体
 * <p>
 * 签到凭证支持两种格式：
 * <ul>
 *   <li><b>完整格式（扫码）：</b>{@code ticket_{eventId}_{ticketNo}_{timestamp}} — 含时间戳，限 60 秒有效</li>
 *   <li><b>手动输入格式：</b>{@code ticket_{ticketNo}} — 无时间限制，手动输入场景使用</li>
 * </ul>
 * </p>
 */
@Data
public class CheckinRequest {

    /** 签到凭证（必填，二维码字符串或手动输入的票号形式） */
    @NotBlank(message = "签到凭证不能为空")
    private String code;
}