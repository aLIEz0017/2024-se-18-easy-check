package com.easysign.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("签到凭证解析器测试")
class CheckinCodeParserTest {

    @Nested
    @DisplayName("完整格式解析")
    class FullFormatTest {

        @Test
        @DisplayName("合法完整格式 → 正确解析所有字段")
        void parse_fullFormat_success() {
            var result = CheckinCodeParser.parse("ticket_1_5_1718888888_1718888889");

            assertNotNull(result);
            assertTrue(result.isFullFormat());
            assertEquals(1L, result.getEventId());
            assertEquals("5_1718888888", result.getTicketNo());
            assertEquals(1718888889L, result.getTimestamp());
        }

        @Test
        @DisplayName("ticketNo 包含下划线的完整格式 → 正确解析")
        void parse_fullFormat_withUnderscoreInTicketNo() {
            var result = CheckinCodeParser.parse("ticket_100_15_1718888888_1718888899");

            assertNotNull(result);
            assertEquals(100L, result.getEventId());
            assertEquals("15_1718888888", result.getTicketNo());
            assertEquals(1718888899L, result.getTimestamp());
        }
    }

    @Nested
    @DisplayName("手动输入格式解析")
    class SimpleFormatTest {

        @ParameterizedTest
        @CsvSource({
            "ticket_1_5_1718888888, 1_5_1718888888",
            "ticket_100_15_1718888888, 100_15_1718888888"
        })
        @DisplayName("合法手动格式 → 正确提取 ticketNo")
        void parse_simpleFormat_success(String code, String expectedTicketNo) {
            var result = CheckinCodeParser.parse(code);

            assertNotNull(result);
            assertFalse(result.isFullFormat());
            assertNull(result.getEventId());
            assertNull(result.getTimestamp());
            assertEquals(expectedTicketNo, result.getTicketNo());
        }
    }

    @Nested
    @DisplayName("非法格式")
    class InvalidFormatTest {

        @ParameterizedTest
        @ValueSource(strings = {
            "",                     // 空字符串
            "ticket_",              // 不完整
            "ticket_abc",           // 无数字
            "ticket_1_2_3_4_5_6",  // 多余字段
            "ticket_1_2_abc",      // 时间戳非数字
            "random_string"         // 完全不匹配
        })
        @DisplayName("非法格式 → 返回 null")
        void parse_invalidFormat_returnsNull(String code) {
            assertNull(CheckinCodeParser.parse(code));
        }

        @Test
        @DisplayName("null 输入 → 返回 null")
        void parse_null_returnsNull() {
            assertNull(CheckinCodeParser.parse(null));
        }
    }

    @Nested
    @DisplayName("时间戳校验")
    class TimestampValidationTest {

        @Test
        @DisplayName("null 时间戳（手动格式）→ 返回 true")
        void isTimestampValid_null_returnsTrue() {
            assertTrue(CheckinCodeParser.isTimestampValid(null, 60));
        }

        @Test
        @DisplayName("10 秒前的二维码 → 在 60 秒窗口内 → 有效")
        void isTimestampValid_withinWindow_returnsTrue() {
            long timestamp = (System.currentTimeMillis() / 1000) - 10;
            assertTrue(CheckinCodeParser.isTimestampValid(timestamp, 60));
        }

        @Test
        @DisplayName("120 秒前的二维码 → 超过 60 秒窗口 → 无效 🔴 高危")
        void isTimestampValid_expired_returnsFalse() {
            long timestamp = (System.currentTimeMillis() / 1000) - 120;
            assertFalse(CheckinCodeParser.isTimestampValid(timestamp, 60));
        }

        @Test
        @DisplayName("刚好 60 秒 — 边界值 → 有效")
        void isTimestampValid_boundary60_returnsTrue() {
            long timestamp = (System.currentTimeMillis() / 1000) - 60;
            assertTrue(CheckinCodeParser.isTimestampValid(timestamp, 60));
        }

        @Test
        @DisplayName("刚好 61 秒 — 边界值 → 无效")
        void isTimestampValid_boundary61_returnsFalse() {
            long timestamp = (System.currentTimeMillis() / 1000) - 61;
            assertFalse(CheckinCodeParser.isTimestampValid(timestamp, 60));
        }
    }
}