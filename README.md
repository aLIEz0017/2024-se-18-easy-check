# 易签 - 活动票务与智能签到系统

EasySign 是一个前后端分离的活动票务管理与智能签到系统，面向活动举办方（B 端）和参会者（C 端）提供完整的活动生命周期管理服务。

## 技术栈

| 层级       | 技术                                                                  |
| ---------- | --------------------------------------------------------------------- |
| 后端       | Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL + JWT + BCrypt         |
| 前端       | Vue 3 (Composition API) + Vite + Element Plus + Pinia + Vue Router    |
| 数据可视化 | ECharts + Vue-Echarts                                                |
| HTTP 客户端 | Axios                                                                |
| WebSocket  | Spring WebSocket（选座实时广播）                                      |
| 缓存       | Spring Cache + 本地缓存                                               |

## 后端核心亮点

### 秒杀防超卖：独立库存表 + 异步落单

`event_stock` 独立库存表与 `event` 主表解耦，秒杀行锁仅影响库存表，不阻塞主表查询。**原子 SQL**（`UPDATE ... WHERE sold_tickets < total_tickets`）确保天然防超卖，无需分布式锁。事务提交后通过 `afterCommit` + 内存队列 **异步落单**，缩短事务持有时间。另有 `StockReconcileTask` 每 5 分钟校正库存一致性。性能预计可以支持**约百人同时抢票**。

### 推荐引擎：手写排序算法 + AI 文案增强

排序完全由算法驱动（标签匹配 ×0.4 + 售票率 ×0.3 + 历史画像 ×0.3），AI 仅用于生成推荐理由文案。AI 不可用时**降级**为固定文案，通过 `@ConditionalOnProperty` 在 mock / deepseek 间灵活切换。

### WebSocket 实时选座 + 抢票回滚联动

座位以二维矩阵呈现，`occupySeat()` 原子 UPDATE 防重复选座。购票成功后通过 WebSocket（按 `eventId` 分组）**即时广播**座位状态变更。若库存扣减失败，已占座位在同一事务内自动释放，保证数据一致。

## 功能概览

### 认证与用户（Auth & User）
- 注册 / 登录，基于 JWT 的用户身份认证
- 双角色体系：**举办方（organizer）** 和 **参会者（attendee）**
- 参会者可设置性别、偏好标签；举办方可管理活动
- 用户信息查询与更新接口（参见 `/api/v1/user/profile`）

### 活动管理（B 端 — 举办方）
- 活动的**增删改查**，支持标题、描述、地点、时间、票数、标签等字段
- 状态流转：`draft → online ↔ offline`（`finished` 由定时任务自动推进）
- **AI 一键生成**活动描述文案（Mock 实现，可替换为真实 LLM）
- 活动列表分页查询
- **选座位开关**：创建/编辑活动时可开启选座（`hasSeats` 字段）

### 选座系统
- **座位初始化**：举办方可为活动批量初始化 10×10 固定模板座位
- **座位矩阵视图**：以二维网格展示活动所有座位及其状态（available / sold）
- **原子选座**：参会者购票时可选择座位，通过 `seatMapper.occupySeat()` 原子 UPDATE 防重复
- **实时广播**：座位售出后通过 WebSocket（`/ws/seat/{eventId}`）推送给所有在线客户端，其他用户即时可见
- **座位释放**：库存扣减失败时自动释放已占座位

### 票务系统
- **抢票**：
  - 活动状态校验（draft / offline / finished 时拒绝）
  - 重复购票检测（逻辑校验）
  - **独立库存表** `event_stock` 原子扣减（防超卖，减少 `event` 表行锁竞争）
  - **选座集成**：若活动开启选座，先原子占座再扣库存；库存不足时自动释放座位
  - **限流**：`PurchaseRateLimiter` 防止瞬时高并发打垮后端
  - **异步落单**：事务提交后通过 `PurchaseQueueConsumer` 内存队列异步写入 `ticket` 表，避免同步 INSERT 阻塞主流程
- **我的票夹**：参会者查看已购门票列表，支持按状态筛选
- **票务详情**：查看单张票的信息和状态（支持展示座位标签）

### 智能签到
- 签到凭证支持两种格式：
  - **完整格式（扫码）**：`ticket_{eventId}_{ticketNo}_{timestamp}`，含 60 秒有效期校验
  - **手动输入格式**：`ticket_{ticketNo}`
- 签到校验：票务存在性、活动归属权（仅举办方可签到）、活动是否已结束、票务状态（防重复签到、防失效票、防退票）
- 签到明细列表，支持按状态筛选（分页查询）

### 数据仪表盘（B 端）
- **总览统计**：活动总数、已售票数、已签到数
- **图表展示**：各活动售票比例饼图、近 7 天签到趋势折线图、近 7 天运营数据折线图（活动/售票/签到）、购票用户性别比例饼图
- **活动数据详情**：单个活动的完整数据指标（含签到明细列表）
- **参会率预测**：基于四维加权模型（历史签到率 ×0.5 + 热度 ×0.2 + 购票集中度 ×0.2 + 时段因子 ×0.1）预测实际到场率

### 活动推荐（C 端）
- **三维加权排序算法**：
  - `标签匹配度 ×0.4` — 用户偏好标签与活动标签的 Jaccard 相似度
  - `售票率 ×0.3` — 热度归一化
  - `历史参会画像 ×0.3` — 用户已结束活动的标签加权匹配
- **AI 推荐理由**（可选的 LLM 增强层）：生成个性化推荐文案；AI 不可用时降级为固定文案
- 未登录用户退化为一维热度排序

### 定时任务
- 每分钟自动扫描超出 `end_time` 的活动，将状态推进为 `finished`
- 每分钟自动扫描库存不一致记录并输出告警日志（`StockReconcileTask`）

### 虚拟数据联调
- `MockDataController` 提供内存固定的样例数据，覆盖认证、活动、票务、签到、仪表盘、推荐等全流程接口
- 前后端联调时前端可切换至 `/api/v1/mock/**` 路径，联调完成后删除本类及其拦截器白名单

### AI 能力
项目包含两层 AI 功能，采用接口抽象设计，可通过配置切换实现：

| AI 功能         | 大模型              |
| --------------- | ------------------- |
| 活动描述文案生成 | DeepSeek API        |
| 个性化推荐理由   | DeepSeek API        |

各模块均接入真实的 DeepSeek API（OpenAI 兼容格式），支持自动重试与解析失败降级为固定文案。AI 不可用时（如配置为 `mock` 或 API 调用失败）返回占位文案，保证功能不中断。

## 单元测试

### 框架
- JUnit 5 + Mockito（Service 层使用 `@ExtendWith(MockitoExtension.class)`）
- `@Nested` 嵌套组织测试分组，`@DisplayName` 中文描述
- WebSocket 测试使用手工 Mock 注入，不依赖 Spring 容器
- AI 集成测试支持 `@EnabledIfEnvironmentVariable` 条件跳过

### 覆盖范围

| 测试类 | 覆盖模块 | 关键测试点 |
|--------|---------|-----------|
| `EventServiceImplTest` | 活动服务 | 权限校验、状态流转、座位初始化联动 |
| `TicketServiceImplTest` | 票务服务 | 防超卖、重复购票、选座集成、异步落单 |
| `FlashSaleOptimizationTest` | 秒杀引擎 | 高并发竞争、库存扣减原子性 |
| `SeatServiceImplTest` | 座位服务 | 座位初始化、占用、释放 |
| `RecommendServiceImplTest` | 推荐引擎 | 标签匹配、排序算法 |
| `EventStatusTaskTest` | 定时任务 | 活动自动结束、异常容错 |
| `CheckinCodeParserTest` | 签到工具 | 凭证正则解析（三种格式）、时间戳边界校验 |
| `JwtUtilTest` | JWT 工具 | 签发/解析、过期校验、角色编码 |
| `SeatWebSocketHandlerTest` | WebSocket | 连接生命周期、`seat_sync`/`seat_sold` 广播 |
| `DeepSeekAiServiceIntegrationTest` | AI 集成 | 推荐文案生成、Prompt 构建 |

### 运行测试

```bash
.\mvnw.cmd test                           # 运行全部单元测试
.\mvnw.cmd test -Dtest=TicketServiceImplTest  # 运行单个测试类
```

## 数据库设计

五张核心表：

| 表名          | 说明                           | 主要字段                                                                 |
| ------------- | ------------------------------ | ------------------------------------------------------------------------ |
| `user`        | 用户                           | username, password, role, gender, tags, avatar_url                       |
| `event`       | 活动                           | organizer_id, title, description, location, start_time, end_time, total_tickets, sold_tickets, tags, status, has_seats |
| `event_stock` | 活动库存（与 event 主表解耦）    | event_id, total_tickets, sold_tickets, version（乐观锁预留）              |
| `ticket`      | 门票                           | event_id, user_id, ticket_no, seat_id（可空）, status, checked_at        |
| `seat`        | 座位（可选座活动使用）           | event_id, seat_row, seat_col, seat_label, status（available / sold）      |

**关键设计说明：**
- `event_stock` 表将库存操作从 `event` 主表分离，秒杀场景下仅该表发生行锁，减少对主表查询的干扰。
- `ticket` 表通过 `(event_id, user_id)` 唯一索引防重复购票。
- `seat` 表通过 `(event_id, seat_row, seat_col)` 唯一标识一个座位。

## 启动方式

### 前置要求
- JDK 17+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

### 启动顺序（后端 → 前端）

#### 第一步：启动后端服务（终端 1）

```bash
# 在项目根目录下执行，默认监听 8080 端口
mvn spring-boot:run
```

> **数据库说明**：默认连接 `localhost:3306/ticket_system`，启动时自动建表。
> 如需自定义，通过环境变量覆盖：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。

#### 第二步：启动前端开发服务器（终端 2）

```bash
# 进入前端目录
cd frontend

# 首次使用或依赖有更新时需安装依赖
npm install

# 启动开发服务器，默认监听 5173 端口
npm run dev
```

前端已配置 `/api` 代理到后端 `8080` 端口，开发时无需处理跨域。

#### 第三步：打开浏览器

访问 `http://localhost:5173` 即可进入系统。

### 关闭服务

| 服务   | 命令                            |
| ------ | ------------------------------- |
| 后端   | `taskkill /f /im java.exe`      |
| 前端   | `taskkill /f /im node.exe`      |

### 生产构建

```bash
cd frontend
npm run build
```

构建产物输出到 `frontend/dist/` 目录。

## 项目结构

```
├── src/main/java/com/easysign/
│   ├── EasySignApplication.java    # 应用入口
│   ├── ai/                         # AI 服务（AiService 接口、DeepSeekAiService、MockAiService、LlmConfig、FeatureEngine、RecommendPromptBuilder）
│   ├── common/                     # 统一响应、业务异常
│   ├── config/                     # Spring 配置（密码编码、CORS、拦截器、MyBatis-Plus、缓存、WebSocket）
│   ├── context/                    # 用户上下文（ThreadLocal）
│   ├── controller/                 # REST 控制器（Auth / Event / Ticket / Dashboard / Recommend / User / MockData）
│   ├── dto/                        # 请求/响应 DTO
│   ├── entity/                     # 数据库实体（User / Event / EventStock / Ticket / Seat）
│   ├── handler/                    # 全局异常处理器
│   ├── interceptor/                # JWT 拦截器
│   ├── mapper/                     # MyBatis-Plus Mapper（含自定义 XML）
│   ├── queue/                      # 抢票异步落单队列（内存队列 + 消费者）
│   ├── ratelimit/                  # 抢票限流器
│   ├── service/                    # 业务逻辑层（含 impl）
│   ├── task/                       # 定时任务（活动自动结束 / 库存对账）
│   ├── util/                       # 工具类（JWT、签到码解析、标签工具）
│   └── websocket/                  # WebSocket（选座实时广播 Handler + 拦截器）
└── frontend/src/
    ├── api/                        # Axios 接口封装
    ├── layouts/                    # BLayout（举办方）/ CLayout（参会者）布局
    ├── router/                     # 路由配置、导航守卫
    ├── stores/                     # Pinia 状态管理（auth / app）
    ├── utils/                      # 常量、格式化工具
    └── views/                      # 页面组件
        ├── b-end/                  # 举办方页面（仪表盘、活动管理、签到工作台）
        ├── c-end/                  # 参会者页面（活动大厅、活动详情、我的票夹）
        ├── login/                  # 登录页
        └── start/                  # 欢迎/首页