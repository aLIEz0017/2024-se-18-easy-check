# 秒杀引擎 — MySQL 纯优化方案

> 本文档为「易签活动票务与智能签到系统」中购买票务的防高并发设计方案。
> 对应 PRD 第三章第一节（千人并发秒杀引擎）的轻量实现 —— 不引入 Redis 中间件，仅基于 MySQL + 应用层优化，统一适用于所有活动的抢票场景。
> 关联技术栈：MySQL 8.x + MyBatis-Plus + Spring Boot。

---

## 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|------|------|--------|----------|
| v1.1 | 2026-07-14 | Cline | 修订：事务提交后才入队（修复幽灵票）、消费者增加死信队列 + 重试、选座场景异常恢复优化、库存校正定时任务 |
| v1.0 | 2026-07-14 | Cline | 初版定稿：MySQL 纯优化方案，库存独立表 + 排队 + 令牌桶 + 批量落库 |

---

## 一、设计目标与取舍

### 1.1 目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 并发量 | **1000+ QPS** | 单活动抢票入口，峰值 1000 并发请求 |
| 库存量 | 不限 | 适用于 50~99999 张门票的活动 |
| 不超卖 | **0 容忍** | 绝对不允许出现卖出 > totalTickets 的情况 |
| 响应时间 | **< 1s** | 用户体验可接受 |
| 引入依赖 | **0 新增** | 仅使用 MySQL + Guava（已有依赖），不引入 Redis |

### 1.2 与 PRD Redis 方案的对比

| 对比项 | PRD Redis 方案 | 本方案（MySQL 纯优化） |
|--------|---------------|----------------------|
| 峰值 QPS | 3000+ | 1000+ |
| 中间件依赖 | Redis + Redis Stream | **零新增** |
| 部署复杂度 | 需额外维护 Redis 服务 | 开箱即用 |
| 库存扣减 | Redis Lua 脚本 | MySQL 原子 UPDATE + 库存独立表减少行锁争用 |
| 异步落库 | Redis Stream → 异步线程 | 内存队列 → 批量写入 |
| 适用阶段 | 后续生产级扩展 | MVP / 课程设计阶段 |

---

## 二、现有抢票逻辑的瓶颈分析

当前 `TicketServiceImpl.purchase()` 的已有防超卖实现：

```java
int affected = eventMapper.updateSoldTickets(req.getEventId());
// SQL: UPDATE event SET sold_tickets = sold_tickets + 1
//      WHERE id = #{eventId} AND sold_tickets < total_tickets
if (affected == 0) throw new BizException(4100, "票已售罄");
```

该方案在低并发下（< 100 QPS）表现良好，但在高并发场景下有 3 个关键瓶颈：

| 瓶颈 | 原因 | 影响 |
|------|------|------|
| **行锁争用** | 所有请求更新同一行 `event` 记录的 `sold_tickets` 字段，InnoDB 行锁串行化 | 高并发下请求排队，TPS 上不去 |
| **事务冲突** | `@Transactional` 中同时包含查询 + UPDATE + INSERT，事务周期长 | 死锁概率增加，大量回滚 |
| **数据库连接池耗尽** | 每个请求占用一个连接，1000 并发瞬间打满连接池 | 后续请求排队超时，页面白屏 |

---

## 三、优化方案概览

### 3.1 四层优化

```
┌─────────────────────────────────────────┐
│              客户端（前端）                │
└─────────────────┬───────────────────────┘
                  │ 发送抢票请求
                  ▼
┌─────────────────────────────────────────┐
│         ① 令牌桶限流（接口层）            │
│  同一用户 1次/秒，单活动入口 500次/秒     │
│  → 超出返回 429「请求过于频繁」           │
└─────────────────┬───────────────────────┘
                  │ 通过限流
                  ▼
┌─────────────────────────────────────────┐
│         ② 库存独立表（数据层）            │
│  event_stock 表剥离 sold_tickets，       │
│  减少 event 主表行锁争用                  │
└─────────────────┬───────────────────────┘
                  │ 原子扣减
                  ▼
┌─────────────────────────────────────────┐
│      ③ 内存队列 + 批量写入（应用层）      │
│  抢票成功记录入内存队列，异步线程          │
│  每 200ms 批量刷入 MySQL                  │
└─────────────────┬───────────────────────┘
                  │ 最终写入
                  ▼
┌─────────────────────────────────────────┐
│        ④ MySQL ticket 表最终落库         │
│  批量 INSERT + 批量 UPDATE sold_tickets  │
└─────────────────────────────────────────┘
```

### 3.2 为什么不分秒杀/非秒杀？

本方案**统一适用于所有活动的抢票场景**，原因：

1. **简单性**：不分路径意味着代码分支少、测试覆盖全面、维护成本低
2. **安全性**：统一使用高并发设计，即使非秒杀活动也不怕突发流量
3. **可预测**：用户无需感知"这个活动是不是秒杀"，体验一致
4. **改造成本低**：仅在现有 `purchase()` 流程中增加 4 层安全网，原有业务逻辑不变

---

## 四、方案详细设计

### 4.1 ① 令牌桶限流（接口层）

#### 4.1.1 依赖

项目已有 Guava 依赖（spring-boot-starter 间接携带 `com.google.guava:guava`），可直接使用 `RateLimiter`。

#### 4.1.2 限流器实现

```java
@Component
public class PurchaseRateLimiter {

    /** 用户级别限流器：userId → RateLimiter */
    private final Map<Long, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    /** 活动级别限流器：eventId → RateLimiter */
    private final Map<Long, RateLimiter> eventLimiters = new ConcurrentHashMap<>();

    /**
     * 双重限流检查
     * @return true=通过, false=被限流
     */
    public boolean tryAcquire(Long userId, Long eventId) {
        // 同一用户 1 次/秒
        RateLimiter userLimiter = userLimiters.computeIfAbsent(userId, k -> RateLimiter.create(1.0));
        if (!userLimiter.tryAcquire()) {
            return false;
        }
        // 单活动入口 500 次/秒
        RateLimiter eventLimiter = eventLimiters.computeIfAbsent(eventId, k -> RateLimiter.create(500.0));
        return eventLimiter.tryAcquire();
    }
}
```

#### 4.1.3 在 Controller 中接入

```java
@PostMapping("/purchase")
public Result<TicketResponse> purchase(@Valid @RequestBody PurchaseRequest req) {
    // 限流检查
    if (!purchaseRateLimiter.tryAcquire(UserContext.getUserId(), req.getEventId())) {
        return Result.error(429, "请求过于频繁，请稍后再试");
    }
    TicketResponse resp = ticketService.purchase(UserContext.getUserId(), req);
    return Result.success("抢票成功！请前往「我的票夹」查看", resp);
}
```

#### 4.1.4 限流维度说明

| 维度 | 阈值 | 说明 |
|------|------|------|
| 同一用户 | 1 次/秒 | 防止单个用户频繁点击或脚本刷票 |
| 单活动入口 | 500 次/秒 | 保护后端不被突发流量冲垮，1000 并发时约一半被限流 |
| IP 级别 | 可选 | MVP 阶段暂不实现，后续可通过拦截器加 |

---

### 4.2 ② 库存独立表（数据层）

#### 4.2.1 设计思路

当前 `event.sold_tickets` 与活动主信息放在同一行，高并发时所有请求争用同一行锁。将库存数据剥离为独立表 `event_stock`，减少行锁争用。

#### 4.2.2 新建 `event_stock` 表

```sql
CREATE TABLE `event_stock` (
  `id`             BIGINT    PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `event_id`       BIGINT    NOT NULL                  COMMENT '关联活动ID',
  `total_tickets`  INT       NOT NULL DEFAULT 0        COMMENT '门票总数',
  `sold_tickets`   INT       NOT NULL DEFAULT 0        COMMENT '已售门票数',
  `version`        INT       NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
  UNIQUE INDEX `uk_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动库存表';
```

**设计说明**：
- 使用 `event_id` 唯一索引，每个活动对应一条库存记录
- 保留 `total_tickets` 字段，避免查询库存时 JOIN event 主表
- 预留 `version` 乐观锁字段，当前暂不使用（未来可切换到乐观锁方案）
- 初始数据在活动发布时由后端同步插入

#### 4.2.3 原子扣减 SQL

```sql
UPDATE event_stock
SET sold_tickets = sold_tickets + 1
WHERE event_id = #{eventId}
  AND sold_tickets < total_tickets
```

该 SQL 是**原子操作**——MySQL InnoDB 对 `event_stock` 表的 `uk_event` 唯一索引加行锁，同一时刻只有一个请求能更新成功。

**为什么比直接在 event 表上 UPDATE 好？**

| 对比 | event 表 | event_stock 表 |
|------|---------|---------------|
| 行大小 | 包含 title/description/location 等大字段 | 仅 id/event_id/total/sold/version，非常紧凑 |
| 行锁时长 | UPDATE 后会锁住整行直到事务结束 | 同样行锁，但锁粒度不变 |
| 主要收益 | — | **分离关注点**：event 主表的读操作（详情查询、列表查询）不再受写锁影响 |

**关键澄清**：库存独立表并不能减少行锁争用本身（仍然需要锁这一行），但它的核心收益在于避免读操作（如查看活动详情、浏览活动列表）因为写锁而阻塞。

#### 4.2.4 对应 Mapper

```java
public interface EventStockMapper extends BaseMapper<EventStock> {

    /**
     * 原子扣减库存
     * @return 影响行数（0=库存不足或活动不存在）
     */
    int deductStock(@Param("eventId") Long eventId);
}
```

```xml
<update id="deductStock">
    UPDATE event_stock
    SET sold_tickets = sold_tickets + 1
    WHERE event_id = #{eventId}
      AND sold_tickets < total_tickets
</update>
```

---

### 4.3 ③ 内存队列 + 批量写入（应用层）

#### 4.3.1 设计思路

抢票成功的记录不立即写入 MySQL ticket 表，而是先放入一个内存阻塞队列，由后台异步线程批量消费。这样做的好处：

1. **减少事务时间**：purchase() 方法只需做限流 + 扣减库存两步即可返回，ticket INSERT 异步进行
2. **减少连接占用**：大量 INSERT 操作不占用 Web 请求的连接池资源
3. **批量化**：合并多个 INSERT 为一次 batch，提高吞吐量

**关键设计决策**：入队操作必须在 Spring 事务成功提交之后执行，而非在事务内执行。如果事务提交失败回滚，但记录已入队，会产生「幽灵票」（有 ticket 记录但库存未扣减）。通过 `TransactionSynchronizationManager.afterCommit()` 确保两者一致。

#### 4.3.2 队列 + 消费者实现

```java
@Component
public class PurchaseQueueConsumer {

    /** 抢票成功记录队列 */
    private final BlockingQueue<PurchaseRecord> queue = new LinkedBlockingQueue<>(10000);

    /** 死信队列：记录消费失败的数据，供后续重试或修复 */
    private final BlockingQueue<PurchaseRecord> deadLetterQueue = new LinkedBlockingQueue<>(5000);

    /** 批量大小阈值 */
    private static final int BATCH_SIZE = 50;

    /** 刷入间隔（毫秒） */
    private static final long FLUSH_INTERVAL_MS = 200;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private EventMapper eventMapper;

    /**
     * 由 purchase() 方法调用，将抢票记录放入队列。
     * 注意：调用方应确保仅在 Spring 事务成功提交后才调用此方法。
     */
    public void enqueue(PurchaseRecord record) {
        if (!queue.offer(record)) {
            log.warn("抢票队列已满，丢弃记录: eventId={}, userId={}", record.getEventId(), record.getUserId());
        }
    }

    /**
     * 异步消费者线程：批量刷入 ticket 表 + 更新 event.sold_tickets
     */
    @PostConstruct
    public void startConsumer() {
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "purchase-queue-consumer");
            t.setDaemon(true);
            return t;
        }).submit(this::consumeLoop);

        // 启动死信队列重试线程
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dead-letter-retry");
            t.setDaemon(true);
            return t;
        }).submit(this::deadLetterRetryLoop);
    }

    private void consumeLoop() {
        List<PurchaseRecord> buffer = new ArrayList<>(BATCH_SIZE);
        while (true) {
            try {
                PurchaseRecord first = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    flushBuffer(buffer);
                    continue;
                }
                buffer.add(first);
                queue.drainTo(buffer, BATCH_SIZE - 1);

                if (buffer.size() >= BATCH_SIZE) {
                    flushBuffer(buffer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flushBuffer(buffer);
                break;
            }
        }
    }

    private void flushBuffer(List<PurchaseRecord> buffer) {
        if (buffer.isEmpty()) return;

        // 1. 批量插入 ticket 表
        List<Ticket> tickets = buffer.stream()
            .map(r -> {
                Ticket t = new Ticket();
                t.setEventId(r.getEventId());
                t.setUserId(r.getUserId());
                t.setTicketNo(r.getTicketNo());  // 使用 purchase() 中预生成的票号
                t.setSeatId(r.getSeatId());       // 选座场景传递座位ID
                t.setStatus("pending");
                return t;
            }).collect(Collectors.toList());

        try {
            ticketMapper.insertBatchSomeColumn(tickets);
        } catch (Exception e) {
            log.error("批量插入 ticket 表失败，写入死信队列。batchSize={}", buffer.size(), e);
            deadLetterQueue.addAll(buffer);
            buffer.clear();
            return;
        }

        // 2. 批量更新 event.sold_tickets（按 eventId 分组后各自 +count）
        Map<Long, Long> countByEvent = buffer.stream()
            .collect(Collectors.groupingBy(PurchaseRecord::getEventId, Collectors.counting()));
        countByEvent.forEach((eventId, count) -> {
            try {
                eventMapper.incrementSoldTickets(eventId, count.intValue());
            } catch (Exception e) {
                log.error("更新 event.sold_tickets 失败，eventId={}", eventId, e);
                // 不阻塞消费：下次校正定时任务会修复
            }
        });

        log.info("批量刷入 {} 条抢票记录", buffer.size());
        buffer.clear();
    }

    /**
     * 死信队列重试线程：每 10 秒尝试重试一次失败记录
     */
    private void deadLetterRetryLoop() {
        List<PurchaseRecord> retryBuffer = new ArrayList<>();
        while (true) {
            try {
                Thread.sleep(10_000);
                deadLetterQueue.drainTo(retryBuffer);
                if (!retryBuffer.isEmpty()) {
                    log.info("死信队列重试：{} 条记录", retryBuffer.size());
                    flushBuffer(retryBuffer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

#### 4.3.3 PurchaseRecord 定义（配合选座场景）

```java
@Data
@AllArgsConstructor
public class PurchaseRecord {
    private Long eventId;
    private Long userId;
    private String ticketNo;   // 预生成的票号（由 purchase() 方法生成）
    private Long seatId;       // 选座时为座位ID，非选座时为null
}
```

#### 4.3.4 批量更新 SQL

```xml
<update id="incrementSoldTickets">
    UPDATE event
    SET sold_tickets = sold_tickets + #{count}
    WHERE id = #{eventId}
      AND sold_tickets + #{count} <= total_tickets
</update>
```

> 这里的 `WHERE sold_tickets + #{count} <= total_tickets` 是安全兜底：即使异步消费时库存被扣超，也能被 WHERE 条件拦住。

---

### 4.4 ④ 最终一致性保障

#### 4.4.1 数据流总览

```
用户请求 purchase()
    │
    ▼
① 限流通过？
    ├── 否 → 返回 429
    └── 是
         │
         ▼
② 重复购票检查
    ├── 已购 → 返回 4004
    └── 未购
         │
         ▼
         ┌──────────────────────────────────────────┐
         │        @Transactional 事务边界开始         │
         │                                           │
         │ ③ 原子扣减 event_stock.sold_tickets + 1   │
         │     ├── 失败 → 回滚，返回 4100            │
         │     └── 成功 → 继续                       │
         │                                           │
         │ ④ 注册 TransactionSynchronization         │
         │    (afterCommit 回调)                     │
         │                                           │
         │        @Transactional 事务边界结束         │
         └──────────────────────────────────────────┘
                              │
                    事务成功提交？
                    ├── 否 → 事务回滚，库存自动恢复，不入队
                    └── 是
                         │
                         ▼
        ⑤ afterCommit 回调中放入内存队列
                (PurchaseQueueConsumer.enqueue)
                         │
                         ▼
        ⑥ 立即返回前端「抢票成功！」
```

```
异步线程（独立线程池）
    │
    ▼
⑦ 每 200ms / 满 50 条，批量消费
    │
    ├── 批量 INSERT ticket 表
    │     ├── 成功 → 继续
    │     └── 失败 → 写入死信队列，10 秒后重试
    │
    └── 批量 UPDATE event.sold_tickets
          ├── 成功 → 正常
          └── 失败 → 日志记录，后续定时任务校正
```

**为什么入队操作要移到事务提交后？**

如果 `enqueue()` 在事务内执行，而 Spring 事务提交失败（如网络异常、数据库连接断开），库存扣减已回滚，但记录已入队，最终消费者会创建一张没有对应库存的 ticket，即「幽灵票」。通过 `TransactionSynchronizationManager.afterCommit()` 确保：**库存扣减成功且事务提交后，才将记录入队列**，彻底杜绝此问题。

#### 4.4.2 不一致窗口与应对

| 窗口 | 说明 | 应对 |
|------|------|------|
| 抢票成功 → ticket 落库 | 用户看到成功，但 ticket 记录尚未写入 MySQL（约 200ms） | 前端提示"抢票成功"，票夹列表中稍后显示 |
| 库存已扣 → event.sold_tickets 未同步 | event 主表的 sold_tickets 可能短时低于实际值 | Dashboard 统计存在 ~200ms 延迟，可接受 |
| 用户重复抢票（并发） | 同一用户发两笔请求 | 第①步限流已拦住同一用户 1次/秒 + 第②步重复检查兜底 |

#### 4.4.3 异常恢复

| 场景 | 影响 | 恢复策略 |
|------|------|---------|
| **批量插入 ticket 失败** | 已扣减的库存无法对应到 ticket | flushBuffer 中 try-catch，异常时记录到 **死信队列**（`deadLetterQueue`），10 秒后自动重试。重试持续失败则保留在死信队列中，人工介入核查 |
| **更新 event.sold_tickets 失败** | event 主表 stat 短时偏低 | try-catch 隔离，不阻塞消费流程。后续通过定时校正任务修复 |
| **进程崩溃/宕机** | 内存队列中未消费的记录全部丢失；已扣减库存但未生成 ticket | **兜底——库存校正定时任务**：每 5 分钟执行一次，检查 `event_stock.sold_tickets` 与 `ticket` 表的实际记录数是否一致，不一致则同步（以 ticket 表为准）。详见下方定时任务实现 |
| **队列溢出** | 丢弃最早记录 | LinkedBlockingQueue 容量 10000，正常场景不会满 |
| **选座场景下 ticket 插入失败** | 座位被占用但无对应 ticket | 死信队列重试机制同样适用于此场景；若重试持续失败，人工通过座位管理页面释放座位 |

**库存校正定时任务实现参考**：

```java
@Component
public class StockReconcileTask {

    @Autowired
    private EventStockMapper eventStockMapper;

    @Autowired
    private TicketMapper ticketMapper;

    /**
     * 每 5 分钟校正一次所有活动的库存
     */
    @Scheduled(fixedRate = 300_000)
    public void reconcileAllStocks() {
        List<EventStock> allStocks = eventStockMapper.selectList(null);
        for (EventStock stock : allStocks) {
            Long actualTicketCount = ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getEventId, stock.getEventId()));
            if (!stock.getSoldTickets().equals(actualTicketCount.intValue())) {
                log.warn("库存校正：event_id={}, stock.sold={}, actual_ticket_count={}",
                    stock.getEventId(), stock.getSoldTickets(), actualTicketCount);
                stock.setSoldTickets(actualTicketCount.intValue());
                eventStockMapper.updateById(stock);
            }
        }
    }
}
```

> **重要**：内存队列方案在应用意外宕机时，队列中未消费的记录会丢失，导致部分已扣减库存但未生成 ticket 的"幽灵扣减"。这是纯内存方案无法避免的问题。上述库存校正定时任务可作为兜底修复手段。如果后续需要更强的可靠性，可升级为 Redis Stream 方案（PRD 推荐方案）。

---

## 五、数据库变更

### 5.1 新建 `event_stock` 表

```sql
CREATE TABLE `event_stock` (
  `id`             BIGINT    PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `event_id`       BIGINT    NOT NULL                  COMMENT '关联活动ID',
  `total_tickets`  INT       NOT NULL DEFAULT 0        COMMENT '门票总数',
  `sold_tickets`   INT       NOT NULL DEFAULT 0        COMMENT '已售门票数',
  `version`        INT       NOT NULL DEFAULT 0        COMMENT '乐观锁版本号（预留）',
  UNIQUE INDEX `uk_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动库存表';
```

### 5.2 event 表 `sold_tickets` 保留

`event.sold_tickets` 字段**保留**，但不再作为库存扣减的依据，仅作为 Dashboard 统计和前端展示的"最终一致"数据源。库存扣减全部走 `event_stock` 表。

### 5.3 初始化库存

在 `EventServiceImpl.changeStatus()` 中，活动从 `draft` → `online` 时，同步插入/更新 `event_stock`：

```java
// EventServiceImpl.changeStatus()
if ("online".equals(req.getStatus())) {
    // 初始化或更新库存
    EventStock stock = eventStockMapper.selectOne(
        new LambdaQueryWrapper<EventStock>().eq(EventStock::getEventId, eventId));
    if (stock == null) {
        stock = new EventStock();
        stock.setEventId(eventId);
        stock.setTotalTickets(event.getTotalTickets());
        stock.setSoldTickets(event.getSoldTickets());  // 已有已售票数（如从 offline 重新上线）
        eventStockMapper.insert(stock);
    } else {
        // 重新上线时更新 total（可能举办方修改过）
        stock.setTotalTickets(event.getTotalTickets());
        eventStockMapper.updateById(stock);
    }
}
```

---

## 六、TicketServiceImpl.purchase() 最终实现

### 6.1 关键设计：事务提交后才入队

`purchase()` 方法核心原则是：**仅做库存扣减和重复检查，通过 `TransactionSynchronizationManager.afterCommit()` 确保事务成功提交后才将记录放入队列**。

这样做的好处：
1. 避免「事务回滚但记录已入队」的幽灵票问题
2. 事务范围缩小到状态校验 + 重复检查 + 库存扣减，事务时间极短
3. 入队操作放在 `afterCommit` 中，保证与事务同生命周期

```java
@Autowired
private PurchaseQueueConsumer purchaseQueueConsumer;

@Autowired
private SeatMapper seatMapper;

@Autowired
private SeatWebSocketHandler seatWebSocketHandler;

/**
 * 非选场景人的抢票事务。
 * 注意：本方法上标注 @Transactional 仅管理「查询 + 库存扣减」两步，
 * 入队操作通过 TransactionSynchronizationManager.afterCommit() 确保在事务提交后方执行。
 */
@Transactional(rollbackFor = Exception.class)
public TicketResponse purchase(Long userId, PurchaseRequest req) {
    // 1. 查询活动是否存在
    Event event = eventMapper.selectById(req.getEventId());
    if (event == null) throw new BizException(4003, "活动不存在");

    // 2. 校验活动状态
    if ("offline".equals(event.getStatus()))
        throw new BizException(4100, "活动已下线，暂不可购票");
    if ("finished".equals(event.getStatus()))
        throw new BizException(4100, "活动已结束，暂不可购票");
    if ("draft".equals(event.getStatus()))
        throw new BizException(4100, "活动未上线，暂不可购票");

    // 3. 重复购票检查
    Long count = ticketMapper.selectCount(
        new LambdaQueryWrapper<Ticket>()
            .eq(Ticket::getEventId, req.getEventId())
            .eq(Ticket::getUserId, userId));
    if (count > 0) throw new BizException(4004, "您已购买过该活动的门票，请勿重复购买");

    // 4. 【选座】若活动开启选座，原子占用座位（仍在事务内）
    Long seatId = null;
    String seatLabel = null;
    if (Boolean.TRUE.equals(event.getHasSeats())) {
        if (req.getSeatId() == null) {
            throw new BizException(4000, "该活动需要选座，请选择座位");
        }
        int occupied = seatMapper.occupySeat(req.getSeatId(), req.getEventId());
        if (occupied == 0) {
            throw new BizException(4100, "座位已被占用或不存在");
        }
        seatId = req.getSeatId();
        Seat seat = seatMapper.selectById(seatId);
        if (seat != null) {
            seatLabel = seat.getSeatLabel();
        }
    }

    // 5. 🔒 原子扣减库存（走 event_stock 独立表）
    int affected = eventStockMapper.deductStock(req.getEventId());
    if (affected == 0) {
        // 回滚：释放已占用的座位
        if (seatId != null) {
            seatMapper.releaseSeat(seatId, req.getEventId());
        }
        throw new BizException(4100, "票已售罄");
    }

    // 6. 构建响应元数据（尚未生成 ticket，稍后异步落库）
    String ticketNo = req.getEventId() + "_" + userId + "_" + (System.currentTimeMillis() / 1000);
    PurchaseRecord record = new PurchaseRecord(req.getEventId(), userId, ticketNo, seatId);

    // 7. 注册回调：事务提交后才入队（避免幽灵票）
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                purchaseQueueConsumer.enqueue(record);

                // 选座场景：广播座位状态变更
                if (seatId != null) {
                    seatWebSocketHandler.broadcastSeatSold(req.getEventId(), seatId);
                }
            }
        }
    );

    // 8. 构建响应（立即返回）
    TicketResponse resp = new TicketResponse();
    resp.setTicketNo(ticketNo);
    resp.setEventTitle(event.getTitle());
    resp.setStatus("pending");
    resp.setSeatId(seatId);
    resp.setSeatLabel(seatLabel);
    return resp;
}
```

### 6.2 补充说明：选座回滚与顺序策略

在「票已售罄」场景下，如果已经占用了座位，需要主动释放。由于 seat 占用和库存扣减在同一个事务中，抛出异常后 Spring 会自动回滚这两个操作。但这里代码中显式调用 `releaseSeat()` 是为了在异常抛出前立即释放，避免后续逻辑误判。

**选座占用和库存扣减的顺序**：本方案保留 **先占座、后扣库存** 的顺序以保持与现有代码逻辑一致。两种顺序都是正确的，区别在于异常路径的代码复杂度：

```java
// 可选优化：先扣库存，再占座位
int affected = eventStockMapper.deductStock(req.getEventId());
if (affected == 0) throw new BizException(4100, "票已售罄");

if (event.hasSeats()) {
    int occupied = seatMapper.occupySeat(req.getSeatId(), req.getEventId());
    if (occupied == 0) {
        // 座位被抢 → 回滚库存
        eventStockMapper.incrementStock(req.getEventId());  // +1 恢复
        throw new BizException(4100, "座位已被占用");
    }
}
```

### 6.3 与旧实现的差异

| 步骤 | 旧实现 | 新实现 |
|------|--------|--------|
| 库存扣减 | event.sold_tickets + 1（同步） | event_stock.sold_tickets + 1（同步） |
| ticket 插入 | 同步 INSERT | 异步队列 → 批量 INSERT |
| 入队时机 | 事务内直接入队 | `afterCommit` 回调中入队 |
| 重复购票兜底 | 唯一索引 + 逻辑检查 | 逻辑检查（唯一索引依然保留） |
| 选座广播 | 事务内广播 | `afterCommit` 回调中广播 |
| 响应返回 | 等待全部完成后返回 | 扣减成功后立即返回 |
| 事务范围 | 全程 @Transactional | 仅状态校验 + 重复检查 + 扣减 + 选座占用为事务 |

---

## 七、需要新增/修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `entity/EventStock.java` | 新增 | 库存实体类 |
| `mapper/EventStockMapper.java` | 新增 | 库存 Mapper |
| `resources/mapper/EventStockMapper.xml` | 新增 | 原子扣减 SQL |
| `model/PurchaseRecord.java` | 新增 | 队列记录 DTO（含 ticketNo、seatId） |
| `component/PurchaseRateLimiter.java` | 新增 | 令牌桶限流器 |
| `component/PurchaseQueueConsumer.java` | 新增 | 异步队列消费者 |
| `component/StockReconcileTask.java` | 新增 | 库存校正定时任务（每 5 分钟） |
| `service/impl/TicketServiceImpl.java` | 修改 | purchase() 改用 event_stock + 队列 + afterCommit 。注意：需使用 `TransactionSynchronizationManager.registerSynchronization` 注册 afterCommit 回调 |
| `service/impl/EventServiceImpl.java` | 修改 | changeStatus() 同步初始化库存 |
| `controller/TicketController.java` | 修改 | 增加限流检查 |
| `mapper/EventMapper.java` | 修改 | 新增 incrementSoldTickets 方法 |
| `resources/mapper/EventMapper.xml` | 修改 | 新增批量更新 sold_tickets SQL |
| `resources/db/init.sql` | 修改 | 新增 event_stock 表建表语句 |

---

## 八、实施步骤

| 阶段 | 内容 | 文件数 | 预估时间 | 完成标准 |
|------|------|--------|---------|---------|
| **Step 1** | 数据库：新建 event_stock 表，更新 init.sql | 2 | 0.5h | SQL 执行成功 |
| **Step 2** | Entity + Mapper：EventStock + EventStockMapper + XML | 3 | 0.5h | `mvn compile` 通过 |
| **Step 3** | 限流器：PurchaseRateLimiter | 1 | 0.5h | 双重限流逻辑正确 |
| **Step 4** | 队列：PurchaseRecord + PurchaseQueueConsumer | 2 | 1h | 批量刷入逻辑通过单元测试 |
| **Step 5** | 校正：StockReconcileTask | 1 | 0.5h | 定时任务正确修复库存偏差 |
| **Step 6** | TicketServiceImpl 改造：改用 event_stock 扣减 + 队列 + afterCommit | 1 | 1h | 抢票流程通过 |
| **Step 7** | EventServiceImpl 改造：上线时初始化库存 | 1 | 0.5h | 状态流转时库存自动同步 |
| **Step 8** | EventMapper 新增批量更新方法 | 2 | 0.5h | SQL 执行正确 |
| **Step 9** | TicketController 增加限流 | 1 | 0.5h | 超频返回 429 |
| **Step 10** | 单元测试 + 集成测试 | - | 1.5h | 覆盖正常/异常全分支 |
| **Step 11** | 手动验证 | - | 0.5h | 并发测试不超卖 |

**总预估时间**：约 **7.5 小时**

---

## 九、验证方案

### 9.1 并发测试方法

无高并发测试工具时，可通过以下方式验证：

**方案：信号量模拟并发**

```java
@Test
public void testConcurrentPurchase() throws InterruptedException {
    int threadCount = 50;  // 模拟 50 个用户同时抢
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        final Long userId = (long) (100 + i);  // 每个线程不同 userId
        new Thread(() -> {
            try {
                PurchaseRequest req = new PurchaseRequest();
                req.setEventId(TEST_EVENT_ID);
                ticketService.purchase(userId, req);
                successCount.incrementAndGet();
            } catch (BizException e) {
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        }).start();
    }

    latch.await(10, TimeUnit.SECONDS);

    // 验证：总成功数 ≤ totalTickets（不超卖）
    assertTrue(successCount.get() <= TOTAL_TICKETS);
    // 验证：成功 + 失败 = 总请求数
    assertEquals(threadCount, successCount.get() + failCount.get());

    // 验证：最终 DB 中 ticket 记录数 = event_stock.sold_tickets
    EventStock stock = eventStockMapper.selectOne(
        new LambdaQueryWrapper<EventStock>().eq(EventStock::getEventId, TEST_EVENT_ID));
    Long ticketCount = ticketMapper.selectCount(
        new LambdaQueryWrapper<Ticket>().eq(Ticket::getEventId, TEST_EVENT_ID));
    assertEquals(stock.getSoldTickets(), ticketCount.intValue());
}
```

### 9.2 预期验证结果

| 场景 | 预期 |
|------|------|
| 50 用户抢 30 张票 | 最多 30 人成功，20 人收到"票已售罄" |
| 同一用户连续发 3 次请求（1 秒内） | 后 2 次返回 429 |
| 异步消费前查询 ticket 表 | 无记录（队列待消费） |
| 等待 500ms 后查询 ticket 表 | 记录已写入（消费者已批量刷入） |

---

## 十、风险与注意事项

1. **应用重启丢数据**：内存队列中的未消费记录在应用重启时会丢失，导致部分已扣减库存但未生成 ticket 的"幽灵扣减"。**兜底方案**：库存校正定时任务（`StockReconcileTask`）每 5 分钟自动修复。如果任务执行间隔内发生重启，丢失的 ticket 会在下一次校正时恢复。MVP 阶段可接受此窗口期。
2. **死信队列溢出**：如果批量写入持续失败，`deadLetterQueue` 中的记录会不断累积（上限 5000 条）。应在监控中注意日志告警，人工介入排查 ticket 表写入失败根因。**兜底**：报警后可手动执行库存校正任务。
3. **事务提交后 afterCommit 回调异常**：如果 `afterCommit` 中执行入队操作抛出异常（如队列已满），`purchase()` 方法已返回成功，用户感知不到异常。应对：enqueue 内部使用 try-catch 包裹且记录日志，同时通过库存校正定时任务兜底。
4. **event_stock 和 event 的 sold_tickets 短期不一致**：event_stock.sold_tickets 是实时准确的，event.sold_tickets 是最终一致的（延迟 ~200ms）。Stat 查询应以 event_stock 为准，或容忍短时不一致。
5. **限流仍会漏少量请求到数据库**：令牌桶限流是概率性的，不是严格的计数器。但因为有 event_stock 的原子 UPDATE 兜底，不会超卖。
6. **队列容量上限**：LinkedBlockingQueue 容量 10000，单活动秒杀场景不会超过。如果担心，可增大为 50000。
7. **选座场景的并发竞争**：先占座后扣库存的顺序下，如果库存扣减失败但座位已占用，显式调用 `releaseSeat()` 释放。由于同一事务中，抛出异常后 Spring 自动回滚也会释放座位，两种机制形成双重保障。

---

## 十一、后续升级路径

本方案设计时已预留扩展点，后续如需升级为 Redis 方案（PRD 推荐方案），可平滑过渡：

| 当前组件 | 升级目标 | 改动量 |
|---------|---------|--------|
| LinkedBlockingQueue（内存队列） | Redis Stream | 仅替换 PurchaseQueueConsumer 内部实现 |
| event_stock 表 | Redis String（库存扣减） | 新增 RedisStockService，保留 event_stock 作为兜底 |
| 令牌桶（Guava） | Redis Lua 令牌桶 | 替换 PurchaseRateLimiter 实现 |
| 事务边界不变 | 不变 | purchase() 方法签名不变，前端无感知 |