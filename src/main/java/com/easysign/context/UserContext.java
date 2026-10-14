package com.easysign.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前用户上下文（ThreadLocal）
 * Service 层无需依赖 HttpServletRequest，即可获取当前用户信息
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    public static void set(UserInfo user) {
        CONTEXT.set(user);
    }

    public static UserInfo get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        UserInfo user = CONTEXT.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        UserInfo user = CONTEXT.get();
        return user == null ? null : user.getUsername();
    }

    public static String getRole() {
        UserInfo user = CONTEXT.get();
        return user == null ? null : user.getRole();
    }

    public static boolean isLoggedIn() {
        return CONTEXT.get() != null;
    }

    public static boolean hasRole(String role) {
        UserInfo user = CONTEXT.get();
        return user != null && role.equals(user.getRole());
    }

    /**
     * 请求结束后清理，防止内存泄漏
     */
    public static void clear() {
        CONTEXT.remove();
    }

    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String role;
    }
}