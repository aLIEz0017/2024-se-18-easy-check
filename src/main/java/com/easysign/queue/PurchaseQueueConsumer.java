package com.easysign.queue;

import com.easysign.entity.Ticket;
import com.easysign.mapper.TicketMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 抢票异步落单队列消费者
 * <p>
 * 消费 {@link LinkedBlockingQueue} 中的 {@link PurchaseRecord}，
 * 异步写入 ticket 表并落单。通过独立的线程池处理，
 * 避免同步写入阻塞抢票主流程。
 * </p>
 */
@Slf4j
@Component
public class PurchaseQueueConsumer {

    /** 内存队列容量 */
    private static final int QUEUE_CAPACITY = 10000;

    /** 消费线程数 */
    private static final int CONSUMER_THREADS = 2;

    /** 共享内存队列 */
    private static final LinkedBlockingQueue<PurchaseRecord> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final ExecutorService executor = Executors.newFixedThreadPool(CONSUMER_THREADS);

    @Autowired
    private TicketMapper ticketMapper;

    @PostConstruct
    public void init() {
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        PurchaseRecord record = QUEUE.take();
                        process(record);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("[队列] 消费异常", e);
                    }
                }
            });
        }
        log.info("[队列] 抢票异步落单消费者启动, 线程数={}, 队列容量={}", CONSUMER_THREADS, QUEUE_CAPACITY);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[队列] 消费者线程池未在 5s 内终止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 将抢票记录加入内存队列
     *
     * @param record 抢票记录
     * @return true=入队成功, false=队列已满
     */
    public static boolean enqueue(PurchaseRecord record) {
        return QUEUE.offer(record);
    }

    /**
     * 处理单条抢票记录：写入 ticket 表
     */
    private void process(PurchaseRecord record) {
        Ticket ticket = new Ticket();
        ticket.setEventId(record.getEventId());
        ticket.setUserId(record.getUserId());
        ticket.setTicketNo(generateTicketNo(record));
        ticket.setSeatId(record.getSeatId());
        ticket.setStatus("pending");
        ticketMapper.insert(ticket);
        log.info("[队列] 落单成功: userId={}, eventId={}, ticketNo={}",
                record.getUserId(), record.getEventId(), ticket.getTicketNo());
    }

    /**
     * 生成票号: eventId_userId_timestamp
     */
    private String generateTicketNo(PurchaseRecord record) {
        return record.getEventId() + "_" + record.getUserId() + "_" + System.currentTimeMillis();
    }
}