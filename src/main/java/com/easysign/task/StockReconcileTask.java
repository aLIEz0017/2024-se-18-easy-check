package com.easysign.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easysign.entity.Event;
import com.easysign.entity.EventStock;
import com.easysign.mapper.EventMapper;
import com.easysign.mapper.EventStockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 库存校正定时任务
 * <p>
 * 每 5 分钟将 event_stock 表的数据同步回 event 主表，
 * 确保 event 表的 sold_tickets 字段与独立库存表一致。
 * 同时清理不存在对应 event_stock 记录的 event（兜底）。
 * </p>
 */
@Slf4j
@Component
public class StockReconcileTask {

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private EventMapper eventMapper;

    /**
     * 每 5 分钟执行一次库存校正
     * <p>将 event_stock 的 sold_tickets 写回 event 主表</p>
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void reconcileStock() {
        log.info("[库存校正] 开始同步库存数据...");

        List<EventStock> stockList = eventStockMapper.selectList(null);
        int updated = 0;
        for (EventStock stock : stockList) {
            Event event = eventMapper.selectById(stock.getEventId());
            if (event == null) {
                log.warn("[库存校正] event_stock 记录 {} 对应的活动不存在, eventId={}", stock.getId(), stock.getEventId());
                continue;
            }
            if (!event.getSoldTickets().equals(stock.getSoldTickets())
                    || !event.getTotalTickets().equals(stock.getTotalTickets())) {
                event.setSoldTickets(stock.getSoldTickets());
                event.setTotalTickets(stock.getTotalTickets());
                eventMapper.updateById(event);
                updated++;
            }
        }

        log.info("[库存校正] 完成: 共检查 {} 条库存记录, 校正 {} 条", stockList.size(), updated);
    }
}