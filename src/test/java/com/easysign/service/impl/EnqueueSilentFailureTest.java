package com.easysign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.dto.request.PurchaseRequest;
import com.easysign.dto.response.TicketResponse;
import com.easysign.entity.Event;
import com.easysign.entity.Seat;
import com.easysign.entity.Ticket;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.EventStockMapper;
import com.easysign.mapper.SeatMapper;
import com.easysign.mapper.TicketMapper;
import com.easysign.queue.PurchaseQueueConsumer;
import com.easysign.queue.PurchaseRecord;
import com.easysign.ratelimit.PurchaseRateLimiter;
import com.easysign.websocket.SeatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证异步落单队列满时 enqueue 静默丢失的边界场景（已修复）
 * <p>
 * 代码审查中发现，{@code PurchaseQueueConsumer.enqueue()} 的返回值在
 * {@link TicketServiceImpl#purchase} 中曾一度被忽略。当内存队列（容量 10000）
 * 已满时，offer() 返回 false，导致库存已扣减、座位已占用但 ticket 永不会落单 → "幽灵库存"。
 * </p>
 * <p>
 * 现已修复：{@code afterCommit()} 中检查 enqueue 返回值，若入队失败则执行补偿逻辑
 * ——恢复库存、释放座位并广播座位可用状态。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("异步落单队列满时补偿机制验证")
class EnqueueSilentFailureTest {

    private static final Long EVENT_ID = 100L;
    private static final Long USER_ID = 5L;
    private static final Long SEAT_ID = 42L;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private EventStockMapper eventStockMapper;

    @Mock
    private SeatMapper seatMapper;

    @Mock
    private SeatWebSocketHandler seatWebSocketHandler;

    @Mock
    private PurchaseRateLimiter purchaseRateLimiter;

    @InjectMocks
    private TicketServiceImpl ticketService;

    private Event mockEvent;
    private Seat availableSeat;

    @BeforeEach
    void setUp() {
        mockEvent = new Event();
        mockEvent.setId(EVENT_ID);
        mockEvent.setStatus("online");
        mockEvent.setTotalTickets(100);
        mockEvent.setSoldTickets(10);
        mockEvent.setHasSeats(true);
        mockEvent.setTitle("Java Day");
        mockEvent.setOrganizerId(99L);

        availableSeat = new Seat();
        availableSeat.setId(SEAT_ID);
        availableSeat.setEventId(EVENT_ID);
        availableSeat.setSeatRow(1);
        availableSeat.setSeatCol(1);
        availableSeat.setSeatLabel("1-1");
        availableSeat.setStatus("available");
    }

    // ================================================================
    //  边界场景：队列满 → enqueue 返回 false → 库存已扣但 ticket 未落单
    // ================================================================

    @Test
@DisplayName("核心验证：队列满时 enqueue 返回 false，purchase() 触发补偿（恢复库存+释放座位）")
    void test_afterCommitEnqueueSilentlyFails_queueFull() throws Exception {
        // 1. 准备工作：通过反射将 QUEUE 填充到满
        Field queueField = PurchaseQueueConsumer.class.getDeclaredField("QUEUE");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedBlockingQueue<PurchaseRecord> queue = (LinkedBlockingQueue<PurchaseRecord>) queueField.get(null);

        int remainingAtStart = queue.remainingCapacity();
        assertTrue(remainingAtStart > 0, "测试前队列应有剩余容量");

        // 填充队列到满
        while (queue.remainingCapacity() > 0) {
            queue.offer(new PurchaseRecord(-1L, -1L, null));
        }
        assertEquals(0, queue.remainingCapacity(), "队列应已满");

        // 2. Mock purchase() 所需的所有调用
        PurchaseRequest req = new PurchaseRequest();
        req.setEventId(EVENT_ID);
        req.setSeatId(SEAT_ID);

        when(eventMapper.selectById(EVENT_ID)).thenReturn(mockEvent);
        when(ticketMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(seatMapper.occupySeat(SEAT_ID, EVENT_ID)).thenReturn(1);
        when(seatMapper.selectById(SEAT_ID)).thenReturn(availableSeat);
        when(eventStockMapper.decreaseStock(EVENT_ID)).thenReturn(1);

        // 3. 模拟 TransactionSynchronizationManager 让 afterCommit 立即执行
        try (var tm = mockStatic(org.springframework.transaction.support.TransactionSynchronizationManager.class)) {
            tm.when(() -> org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(any(org.springframework.transaction.support.TransactionSynchronization.class)))
                    .thenAnswer(invocation -> {
                        org.springframework.transaction.support.TransactionSynchronization sync =
                                invocation.getArgument(0);
                        sync.afterCommit();
                        return null;
                    });

            // 执行 purchase
            TicketResponse resp = ticketService.purchase(USER_ID, req);

            // 4. ✅ purchase() 本身返回成功（无异常抛出）
            assertNotNull(resp.getTicketNo(), "purchase() 应返回票号，用户认为成功");
            assertEquals(SEAT_ID, resp.getSeatId(), "seatId 应已返回");

            // 5. ✅ 库存已扣减
            verify(eventStockMapper).decreaseStock(EVENT_ID);

            // 6. ✅ 座位已原子占用
            verify(seatMapper).occupySeat(SEAT_ID, EVENT_ID);

            // 7. ✅ 座位已广播
            verify(seatWebSocketHandler).broadcastSeatSold(EVENT_ID, SEAT_ID);

            // 8. ❌ 队列满时 enqueue 失败，ticket 永不会落单
            //    验证 ticketMapper.insert() 没有被调用
            verify(ticketMapper, never()).insert(any(Ticket.class));

            // 9. ✅ 补偿触发：释放座位 + 恢复库存 + 广播座位可用
            verify(seatMapper).releaseSeat(SEAT_ID, EVENT_ID);
            verify(eventStockMapper).increaseStock(EVENT_ID);
            verify(seatWebSocketHandler).broadcastSeatAvailable(EVENT_ID, SEAT_ID);
        }

        // 清理
        queue.clear();
    }

    // ================================================================
    //  辅助验证：正常队列 → enqueue 返回 true
    // ================================================================

    @Test
    @DisplayName("辅助验证：正常队列时 enqueue 返回 true")
    void test_enqueueReturnsTrue_whenQueueNotFull() throws Exception {
        Field queueField = PurchaseQueueConsumer.class.getDeclaredField("QUEUE");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedBlockingQueue<PurchaseRecord> queue = (LinkedBlockingQueue<PurchaseRecord>) queueField.get(null);

        assertTrue(PurchaseQueueConsumer.enqueue(new PurchaseRecord(USER_ID, EVENT_ID, SEAT_ID)),
                "队列未满时 enqueue 应返回 true");

        // 从队列中取出刚入队的记录
        PurchaseRecord polled = queue.poll();
        assertNotNull(polled, "应能取出刚入队的记录");
        assertEquals(USER_ID, polled.getUserId());
        assertEquals(EVENT_ID, polled.getEventId());
        assertEquals(SEAT_ID, polled.getSeatId());
    }

    // ================================================================
    //  边界场景：队列将满时 enqueue 失败
    // ================================================================

    @Test
    @DisplayName("边界验证：队列仅剩1个空位，倒序填充验证 enqueue 返回值")
    void test_enqueueBoundary_capacityAlmostFull() throws Exception {
        Field queueField = PurchaseQueueConsumer.class.getDeclaredField("QUEUE");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedBlockingQueue<PurchaseRecord> queue = (LinkedBlockingQueue<PurchaseRecord>) queueField.get(null);

        // 填充到仅剩 1 个空位
        while (queue.remainingCapacity() > 1) {
            queue.offer(new PurchaseRecord(-1L, -1L, null));
        }

        // 还剩 1 个空位 → 入队应成功
        assertTrue(PurchaseQueueConsumer.enqueue(new PurchaseRecord(1L, 1L, null)),
                "还剩1个空位时应入队成功");

        // 队列已满 → 入队应失败
        assertFalse(PurchaseQueueConsumer.enqueue(new PurchaseRecord(2L, 2L, null)),
                "队列已满时应入队失败");

        queue.clear();
    }

    /**
     * 清理：确保每次测试后队列状态独立。
     * 因为 QUEUE 是 static 的，反射清空避免测试间干扰。
     */
    @BeforeEach
    void cleanQueue() throws Exception {
        Field queueField = PurchaseQueueConsumer.class.getDeclaredField("QUEUE");
        queueField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedBlockingQueue<PurchaseRecord> queue = (LinkedBlockingQueue<PurchaseRecord>) queueField.get(null);
        queue.clear();
    }
}