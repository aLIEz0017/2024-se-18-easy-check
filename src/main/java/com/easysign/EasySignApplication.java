package com.easysign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 「易签活动票务与智能签到系统」应用入口
 * <p>
 * 启用 {@link EnableScheduling @EnableScheduling} 以支持活动自动结束定时任务
 * （{@link com.easysign.task.EventStatusTask}）。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class EasySignApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasySignApplication.class, args);
    }

}