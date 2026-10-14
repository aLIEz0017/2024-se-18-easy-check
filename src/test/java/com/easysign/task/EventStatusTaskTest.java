package com.easysign.task;

import com.easysign.entity.Event;
import com.easysign.mapper.EventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务测试")
class EventStatusTaskTest {

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventStatusTask eventStatusTask;

    private Event expiredEvent1;
    private Event expiredEvent2;

    @BeforeEach
    void setUp() {
        expiredEvent1 = new Event();
        expiredEvent1.setId(1L);
        expiredEvent1.setTitle("活动1");
        expiredEvent1.setStatus("online");

        expiredEvent2 = new Event();
        expiredEvent2.setId(2L);
        expiredEvent2.setTitle("活动2");
        expiredEvent2.setStatus("offline");
    }

    @Test
    @DisplayName("有过期活动 → 全部设为 finished")
    void autoFinishEvents_withExpiredEvents_success() {
        when(eventMapper.selectList(any())).thenReturn(List.of(expiredEvent1, expiredEvent2));

        eventStatusTask.autoFinishEvents();

        verify(eventMapper, times(2)).updateById(any(Event.class));
        verify(eventMapper, times(2)).updateById(argThat((Event e) -> "finished".equals(e.getStatus())));
    }

    @Test
    @DisplayName("无过期活动 → 不更新")
    void autoFinishEvents_noExpiredEvents_doesNothing() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        eventStatusTask.autoFinishEvents();

        verify(eventMapper, never()).updateById(any(Event.class));
    }

    @Test
    @DisplayName("更新中途抛异常 → 不影响下次执行（catch 捕获）")
    void autoFinishEvents_partialFailure_continuesNextCycle() {
        when(eventMapper.selectList(any())).thenReturn(List.of(expiredEvent1, expiredEvent2));

        // 使用 doAnswer 为每个 updateById 调用设置不同行为
        // 第一个调用（expiredEvent1）成功，第二个调用（expiredEvent2）抛异常
        doAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            if (e.getId().equals(2L)) {
                throw new RuntimeException("DB error");
            }
            return 0;
        }).when(eventMapper).updateById(any(Event.class));

        // 不应抛异常（catch 块捕获）
        eventStatusTask.autoFinishEvents();

        // 活动1 应该已被更新，活动2 因异常跳过
        verify(eventMapper, times(2)).updateById(any(Event.class));
    }
}