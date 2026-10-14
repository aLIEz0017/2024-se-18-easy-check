package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.common.BizException;
import com.easysign.dto.request.CreateEventRequest;
import com.easysign.dto.request.UpdateEventRequest;
import com.easysign.dto.response.CreateEventResponse;
import com.easysign.entity.Event;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EventService 单元测试（活动与座位联动）
 * <p>
 * 验证发布活动时座位初始化、编辑活动时 hasSeats 开关的变更处理。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventService 活动与座位联动测试")
class EventServiceImplTest {

    private static final Long USER_ID = 5L;
    private static final Long EVENT_ID = 100L;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private SeatService seatService;

    @InjectMocks
    private EventServiceImpl eventService;

    private Event mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = new Event();
        mockEvent.setId(EVENT_ID);
        mockEvent.setOrganizerId(USER_ID);
        mockEvent.setTitle("Java Day");
        mockEvent.setStatus("draft");
        mockEvent.setTotalTickets(100);
        mockEvent.setSoldTickets(0);
        mockEvent.setHasSeats(false);
    }

    // ======================== 发布活动：座位初始化 ========================

    @Nested
    @DisplayName("发布活动")
    class CreateEventTest {

        @Test
        @DisplayName("hasSeats=true → 插入活动后调用 seatService.initSeats")
        void createEvent_hasSeats_success() {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("Java Day");
            req.setStartTime("2099-01-01 09:00:00");
            req.setEndTime("2099-01-01 18:00:00");
            req.setLocation("报告厅");
            req.setTotalTickets(100);
            req.setHasSeats(true);

            when(eventMapper.insert(any(Event.class))).thenReturn(1);

            CreateEventResponse resp = eventService.createEvent(USER_ID, req);

            assertNotNull(resp);
            verify(eventMapper).insert(any(Event.class));
            // Mock insert 不会回填自增 ID，initSeats 收到的 eventId 为 null
            // 实际运行中 insert 后 event.getId() 有值
            verify(seatService).initSeats(null);
        }

        @Test
        @DisplayName("hasSeats=false → 不调用 seatService.initSeats")
        void createEvent_noSeats_success() {
            CreateEventRequest req = new CreateEventRequest();
            req.setTitle("Java Day");
            req.setStartTime("2099-01-01 09:00:00");
            req.setEndTime("2099-01-01 18:00:00");
            req.setLocation("报告厅");
            req.setTotalTickets(100);
            req.setHasSeats(false);

            when(eventMapper.insert(any(Event.class))).thenReturn(1);

            CreateEventResponse resp = eventService.createEvent(USER_ID, req);

            assertNotNull(resp);
            verify(seatService, never()).initSeats(anyLong());
        }
    }

    // ======================== 编辑活动：hasSeats 开关变更 ========================

    @Nested
    @DisplayName("编辑活动")
    class UpdateEventTest {

        @Test
        @DisplayName("关闭选座（true→false），无已售座位 → 调用 seatService.deleteSeats")
        void updateEvent_disableHasSeats_success() {
            // 活动当前 hasSeats=true, status=draft
            mockEvent.setHasSeats(true);
            mockEvent.setStatus("draft");
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(seatService.hasSoldSeats(EVENT_ID)).thenReturn(false);

            UpdateEventRequest req = new UpdateEventRequest();
            req.setHasSeats(false);

            eventService.updateEvent(USER_ID, EVENT_ID, req);

            verify(seatService).deleteSeats(EVENT_ID);
            verify(eventMapper).updateById(any(Event.class));
        }

        @Test
        @DisplayName("关闭选座，有已售座位 → 抛 BizException(4100)，不删除座位")
        void updateEvent_disableHasSeats_withSoldSeats_throws() {
            mockEvent.setHasSeats(true);
            mockEvent.setStatus("draft");
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(seatService.hasSoldSeats(EVENT_ID)).thenReturn(true);

            UpdateEventRequest req = new UpdateEventRequest();
            req.setHasSeats(false);

            BizException ex = assertThrows(BizException.class,
                    () -> eventService.updateEvent(USER_ID, EVENT_ID, req));
            assertEquals(4100, ex.getCode());

            verify(seatService, never()).deleteSeats(anyLong());
        }

        @Test
        @DisplayName("开启选座（false→true）→ 调用 seatService.initSeats")
        void updateEvent_enableHasSeats_success() {
            mockEvent.setHasSeats(false);
            mockEvent.setStatus("draft");
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);

            UpdateEventRequest req = new UpdateEventRequest();
            req.setHasSeats(true);

            eventService.updateEvent(USER_ID, EVENT_ID, req);

            verify(seatService).initSeats(EVENT_ID);
            verify(eventMapper).updateById(any(Event.class));
        }
    }
}