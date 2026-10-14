package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.common.BizException;
import com.easysign.dto.request.SeatSelectRequest;
import com.easysign.dto.response.SeatMatrixResponse;
import com.easysign.dto.response.SeatResponse;
import com.easysign.entity.Event;
import com.easysign.entity.Seat;
import com.easysign.entity.Ticket;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.SeatMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.websocket.SeatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeatService 单元测试
 * <p>
 * 覆盖座位初始化、查询、删除、选座四大能力。
 * 使用 Mockito Mock 所有 Mapper 和 WebSocket Handler 依赖。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SeatService 单元测试")
class SeatServiceImplTest {

    private static final Long EVENT_ID = 100L;
    private static final Long USER_ID = 5L;
    private static final Long SEAT_ID = 42L;
    private static final Long TICKET_ID = 999L;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private SeatWebSocketHandler seatWebSocketHandler;

    @InjectMocks
    private SeatServiceImpl seatService;

    private Event mockEvent;
    private Ticket mockTicket;

    @BeforeEach
    void setUp() {
        mockEvent = new Event();
        mockEvent.setId(EVENT_ID);
        mockEvent.setHasSeats(true);

        mockTicket = new Ticket();
        mockTicket.setId(TICKET_ID);
        mockTicket.setEventId(EVENT_ID);
        mockTicket.setUserId(USER_ID);
        mockTicket.setSeatId(null);       // 尚未选座
        mockTicket.setTicketNo("100_5_1718888888");
    }

    // ======================== 座位初始化 ========================

    @Nested
    @DisplayName("座位初始化")
    class InitSeatsTest {

        @Test
        @DisplayName("为活动初始化 10×10=100 个座位")
        void initSeats_success() {
            seatService.initSeats(EVENT_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
            verify(seatMapper).insertBatch(captor.capture());

            List<Seat> seats = captor.getValue();
            assertEquals(100, seats.size());

            // 验证首尾座位标签
            assertEquals("1-1", seats.get(0).getSeatLabel());
            assertEquals(EVENT_ID, seats.get(0).getEventId());
            assertEquals("available", seats.get(0).getStatus());
        }
    }

    // ======================== 座位删除 ========================

    @Nested
    @DisplayName("座位删除")
    class DeleteSeatsTest {

        @Test
        @DisplayName("删除活动下所有座位")
        void deleteSeats_success() {
            seatService.deleteSeats(EVENT_ID);
            verify(seatMapper).delete(any(LambdaQueryWrapper.class));
        }
    }

    // ======================== 是否有已售座位 ========================

    @Nested
    @DisplayName("是否有已售座位")
    class HasSoldSeatsTest {

        @Test
        @DisplayName("存在 sold 状态座位 → 返回 true")
        void hasSoldSeats_true() {
            when(seatMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertTrue(seatService.hasSoldSeats(EVENT_ID));
        }

        @Test
        @DisplayName("无 sold 状态座位 → 返回 false")
        void hasSoldSeats_false() {
            when(seatMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertFalse(seatService.hasSoldSeats(EVENT_ID));
        }
    }

    // ======================== 座位列表查询 ========================

    @Nested
    @DisplayName("座位列表查询")
    class GetSeatsByEventIdTest {

        @Test
        @DisplayName("正常返回活动下全部座位")
        void getSeatsByEventId_success() {
            List<Seat> seats = Arrays.asList(
                    buildSeat(1L, 1, 1, "1-1", "available"),
                    buildSeat(2L, 1, 2, "1-2", "sold"),
                    buildSeat(3L, 2, 1, "2-1", "available")
            );
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(seats);

            List<SeatResponse> responses = seatService.getSeatsByEventId(EVENT_ID);

            assertEquals(3, responses.size());
            assertEquals("1-1", responses.get(0).getSeatLabel());
            assertEquals("available", responses.get(0).getStatus());
            assertEquals("sold", responses.get(1).getStatus());
        }

        @Test
        @DisplayName("活动无座位 → 返回空列表")
        void getSeatsByEventId_empty() {
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

            List<SeatResponse> responses = seatService.getSeatsByEventId(EVENT_ID);
            assertTrue(responses.isEmpty());
        }
    }

    // ======================== 座位矩阵查询 ========================

    @Nested
    @DisplayName("座位矩阵查询")
    class GetSeatMatrixTest {

        @Test
        @DisplayName("有座位数据 → 返回完整矩阵信息")
        void getSeatMatrix_success() {
            List<Seat> seats = new ArrayList<>();
            for (int row = 1; row <= 10; row++) {
                for (int col = 1; col <= 10; col++) {
                    seats.add(buildSeat((long)(row * 100 + col), row, col,
                            row + "-" + col, "available"));
                }
            }
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(seats);
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);

            SeatMatrixResponse resp = seatService.getSeatMatrix(EVENT_ID);

            assertTrue(resp.getHasSeats());
            assertEquals(10, resp.getRows());
            assertEquals(10, resp.getCols());
            assertEquals(100, resp.getSeats().size());
        }

        @Test
        @DisplayName("无座位数据 → 行列均为 0")
        void getSeatMatrix_empty() {
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);

            SeatMatrixResponse resp = seatService.getSeatMatrix(EVENT_ID);

            assertEquals(0, resp.getRows());
            assertEquals(0, resp.getCols());
            assertTrue(resp.getSeats().isEmpty());
        }

        @Test
        @DisplayName("活动 hasSeats=false → hasSeats 为 false")
        void getSeatMatrix_hasSeatsFalse() {
            mockEvent.setHasSeats(false);
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);

            SeatMatrixResponse resp = seatService.getSeatMatrix(EVENT_ID);
            assertFalse(resp.getHasSeats());
        }

        @Test
        @DisplayName("活动不存在 → hasSeats=false, 行列均为 0")
        void getSeatMatrix_eventNotExists() {
            when(seatMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());
            when(eventMapper.selectById(EVENT_ID)).thenReturn(null);

            SeatMatrixResponse resp = seatService.getSeatMatrix(EVENT_ID);

            assertFalse(resp.getHasSeats());
            assertEquals(0, resp.getRows());
            assertEquals(0, resp.getCols());
        }
    }

    // ======================== 原子选座 ========================

    @Nested
    @DisplayName("原子选座")
    class SelectSeatTest {

        // ======================== 正常选座流程 ========================

        @Test
        @DisplayName("正常选座 → occupySeat 返回 1，ticket 关联 seatId，WebSocket 广播")
        void selectSeat_success() {
            SeatSelectRequest req = buildSelectRequest();

            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockTicket);
            when(seatMapper.occupySeat(SEAT_ID, EVENT_ID)).thenReturn(1);

            seatService.selectSeat(USER_ID, req);

            // 验证 ticket.seat_id 被更新
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketMapper).updateById(ticketCaptor.capture());
            assertEquals(SEAT_ID, ticketCaptor.getValue().getSeatId());
            // 验证 WebSocket 广播
            verify(seatWebSocketHandler).broadcastSeatSold(EVENT_ID, SEAT_ID);
        }

        // ======================== 异常场景 ========================

        @Test
        @DisplayName("活动不存在 → 抛 BizException(4003)")
        void selectSeat_eventNotExists() {
            SeatSelectRequest req = buildSelectRequest();
            when(eventMapper.selectById(EVENT_ID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> seatService.selectSeat(USER_ID, req));
            assertEquals(4003, ex.getCode());
        }

        @Test
        @DisplayName("活动未开启选座 → 抛 BizException(4100)")
        void selectSeat_hasSeatsFalse() {
            mockEvent.setHasSeats(false);
            SeatSelectRequest req = buildSelectRequest();
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);

            BizException ex = assertThrows(BizException.class,
                    () -> seatService.selectSeat(USER_ID, req));
            assertEquals(4100, ex.getCode());
        }

        @Test
        @DisplayName("用户未购票 → 抛 BizException(4100)")
        void selectSeat_noTicket() {
            SeatSelectRequest req = buildSelectRequest();
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> seatService.selectSeat(USER_ID, req));
            assertEquals(4100, ex.getCode());
        }

        @Test
        @DisplayName("已选过座位（ticket.seatId != null）→ 抛 BizException(4004)")
        void selectSeat_alreadySelected() {
            mockTicket.setSeatId(99L);  // 已经选过座位
            SeatSelectRequest req = buildSelectRequest();
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockTicket);

            BizException ex = assertThrows(BizException.class,
                    () -> seatService.selectSeat(USER_ID, req));
            assertEquals(4004, ex.getCode());

            // 不可重复选座，不应调用 occupySeat
            verify(seatMapper, never()).occupySeat(anyLong(), anyLong());
        }

        @Test
        @DisplayName("座位被并发占用（occupySeat 返回 0）→ 抛 BizException(4100)")
        void selectSeat_seatConflict() {
            SeatSelectRequest req = buildSelectRequest();
            when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
            when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockTicket);
            when(seatMapper.occupySeat(SEAT_ID, EVENT_ID)).thenReturn(0);  // 原子更新失败

            BizException ex = assertThrows(BizException.class,
                    () -> seatService.selectSeat(USER_ID, req));
            assertEquals(4100, ex.getCode());

            // 座位冲突时不应更新 ticket
            verify(ticketMapper, never()).updateById(any(Ticket.class));
            // 也不应广播
            verify(seatWebSocketHandler, never()).broadcastSeatSold(anyLong(), anyLong());
        }

        private SeatSelectRequest buildSelectRequest() {
            SeatSelectRequest req = new SeatSelectRequest();
            req.setEventId(EVENT_ID);
            req.setSeatId(SEAT_ID);
            return req;
        }
    }

    // ======================== 工具方法 ========================

    private Seat buildSeat(Long id, int row, int col, String label, String status) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setEventId(EVENT_ID);
        seat.setSeatRow(row);
        seat.setSeatCol(col);
        seat.setSeatLabel(label);
        seat.setStatus(status);
        return seat;
    }
}