package com.easysign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 密码编码器配置 —— 提供 BCrypt 密码加密与验证的 Bean
 * <p>
 * 使用 jBCrypt 库（org.mindrot.jbcrypt.BCrypt），轻量级无外部依赖。
 * </p>
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public static class BCryptPasswordEncoder {

        private final int logRounds;

        public BCryptPasswordEncoder() {
            this(10);
        }

        public BCryptPasswordEncoder(int logRounds) {
            this.logRounds = logRounds;
        }

        public String encode(CharSequence rawPassword) {
            String salt = org.mindrot.jbcrypt.BCrypt.gensalt(logRounds);
            return org.mindrot.jbcrypt.BCrypt.hashpw(rawPassword.toString(), salt);
        }

        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return org.mindrot.jbcrypt.BCrypt.checkpw(rawPassword.toString(), encodedPassword);
        }
    }
}