package com.easysign.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.entity.Event;
import com.easysign.mapper.EventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务 — 活动状态自动推进
 * <p>
 * 每分钟（{@code fixedRate = 60_000}）执行一次，扫描所有 {@code end_time <= NOW()}
 * 且状态为 {@code online} 或 {@code offline} 的活动，将其状态自动设为 {@code finished}。
 * 这是活动结束时机的唯一入口，举办方不可手动将活动设为 {@code finished}。
 * </p>
 * <p>
 * 配合 {@link org.springframework.scheduling.annotation.EnableScheduling @EnableScheduling}
 * 在 {@link com.easysign.EasySignApplication} 上启用。
 * </p>
 */
@Slf4j
@Component
public class EventStatusTask {

    private final EventMapper eventMapper;

    public EventStatusTask(EventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Scheduled(fixedRate = 60_000)
    public void autoFinishEvents() {
        try {
            // 查询待结束的活动
            List<Event> events = eventMapper.selectList(
                    new LambdaQueryWrapper<Event>()
                            .isNotNull(Event::getEndTime)
                            .le(Event::getEndTime, LocalDateTime.now())
                            .in(Event::getStatus, "online", "offline")
            );

            if (events.isEmpty()) {
                return;
            }

            // 批量更新状态为 finished
            for (Event event : events) {
                event.setStatus("finished");
                eventMapper.updateById(event);
                log.info("活动自动结束: id={}, title={}", event.getId(), event.getTitle());
            }
        } catch (Exception e) {
            log.error("定时任务 autoFinishEvents 执行异常: ", e);
        }
    }
}