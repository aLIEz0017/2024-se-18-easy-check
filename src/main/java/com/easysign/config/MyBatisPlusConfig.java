package com.easysign.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 核心配置
 * <p>
 * 主要职责：
 * <ol>
 *   <li>注册分页插件（{@link PaginationInnerInterceptor}），分页参数自动截断（size 最大 100）</li>
 *   <li>配置 mapper.xml 扫描路径为 {@code classpath:mapper/*.xml}</li>
 *   <li>提供 {@link MyMetaObjectHandler} 自动填充 {@code createdAt} / {@code updatedAt} 字段</li>
 * </ol>
 * </p>
 */
@Configuration
@MapperScan("com.easysign.mapper")
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MybatisPlusPropertiesCustomizer plusPropertiesCustomizer() {
        return props -> props.setMapperLocations(new String[]{"classpath:mapper/*.xml"});
    }

    /**
     * 自动填充处理器：插入时填充 {@code createdAt} + {@code updatedAt}，更新时填充 {@code updatedAt}。
     */
    @Component
    public static class MyMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        }
    }
}