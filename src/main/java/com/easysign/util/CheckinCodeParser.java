package com.easysign.util;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 签到凭证解析器
 * <p>
 * 将正则匹配逻辑从 TicketServiceImpl 中提取出来，便于单元测试。
 * 支持两种格式：
 * <ul>
 *   <li><b>完整格式（扫码）：</b>{@code ticket_{eventId}_{ticketNo}_{timestamp}}</li>
 *   <li><b>手动输入格式：</b>{@code ticket_{ticketNo}}</li>
 * </ul>
 * </p>
 */
public class CheckinCodeParser {

    /** 完整格式：ticket_{eventId}_{ticketNo}_{timestamp} */
    private static final Pattern FULL_PATTERN = Pattern.compile("^ticket_(\\d+)_(.+)_(\\d{10,})$");

    /** 手动输入格式：ticket_{ticketNo} */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile("^ticket_(\\d+_\\d+_\\d{10,})$");

    @Data
    public static class ParseResult {
        /** 活动 ID（仅完整格式非 null） */
        private final Long eventId;
        /** 票号 */
        private final String ticketNo;
        /** 时间戳（仅完整格式非 null） */
        private final Long timestamp;
        /** 是否为完整格式 */
        private final boolean fullFormat;
    }

    /**
     * 解析签到凭证
     *
     * @param code 签到凭证字符串
     * @return ParseResult，格式不合法时返回 null
     */
    public static ParseResult parse(String code) {
        if (code == null) return null;

        // 先尝试匹配手动输入格式（SIMPLE），再尝试完整格式（FULL）：
        // 手动格式码 ticket_{ticketNo}（如 ticket_100_5_1718888888）
        // 可能同时被 FULL_PATTERN 匹配（eventId=100, ticketNo=5, timestamp=1718888888），
        // 而完整格式码 ticket_{eventId}_{ticketNo}_{timestamp} 因多一段 _timestamp，
        // 不会被 SIMPLE_PATTERN 匹配，故先匹配 SIMPLE 是安全的。
        Matcher m2 = SIMPLE_PATTERN.matcher(code);
        if (m2.matches()) {
            return new ParseResult(
                    null,      // eventId = null
                    m2.group(1),
                    null,      // timestamp = null
                    false
            );
        }

        Matcher m1 = FULL_PATTERN.matcher(code);
        if (m1.matches()) {
            return new ParseResult(
                    Long.parseLong(m1.group(1)),
                    m1.group(2),
                    Long.parseLong(m1.group(3)),
                    true
            );
        }

        return null; // 格式不合法
    }

    /**
     * 校验时间戳是否在有效期内（完整格式才需要调用）
     *
     * @param timestamp     时间戳（秒）
     * @param maxAgeSeconds 最大有效时长（秒）
     * @return true 表示有效，false 表示已过期
     */
    public static boolean isTimestampValid(Long timestamp, long maxAgeSeconds) {
        if (timestamp == null) return true; // 手动输入跳过
        long now = System.currentTimeMillis() / 1000;
        return now - timestamp <= maxAgeSeconds;
    }
}