package com.easysign.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JWT 工具测试")
class JwtUtilTest {

    private static final String SECRET = "my-test-secret-key-that-is-long-enough-for-hmac-sha384!!";
    private static final long EXPIRE_MS = 86400000L; // 1 天

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRE_MS);
    }

    @Test
    @DisplayName("生成 token → 可正常解析")
    void generateToken_parseToken_success() {
        String token = jwtUtil.generateToken(1L, "testuser", "attendee");

        assertNotNull(token);

        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        assertEquals(1L, userId);
        assertEquals("testuser", username);
        assertEquals("attendee", role);
    }

    @Test
    @DisplayName("解析过期 token → isTokenExpired 返回 true")
    void isTokenExpired_expiredToken_returnsTrue() {
        // 使用 0 过期时间创建一个新的实例
        JwtUtil shortLived = new JwtUtil(SECRET, 0L);
        String token = shortLived.generateToken(1L, "testuser", "attendee");

        // token 应该已被标记为过期
        assertTrue(shortLived.isTokenExpired(token));
    }

    @Test
    @DisplayName("解析无效 token → isTokenExpired 返回 true（不抛异常）")
    void isTokenExpired_invalidToken_returnsTrue() {
        assertTrue(jwtUtil.isTokenExpired("invalid.token.string"));
    }

    @Test
    @DisplayName("角色信息正确编码在 token 中")
    void generateToken_roleEncoded() {
        String organizerToken = jwtUtil.generateToken(2L, "organizer1", "organizer");
        String attendeeToken = jwtUtil.generateToken(3L, "attendee1", "attendee");

        assertEquals("organizer", jwtUtil.getRoleFromToken(organizerToken));
        assertEquals("attendee", jwtUtil.getRoleFromToken(attendeeToken));
    }
}