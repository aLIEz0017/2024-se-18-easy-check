# 架构决策记录 (Architecture Decision Records)

## ADR-001: 认证模块使用 jBCrypt 替代 Spring Security

**日期**: 2026-07-08

**问题**: 认证模块需要 BCrypt 密码加密功能，但引入 `spring-boot-starter-security` 会导致：
1. 依赖过大（10+ 个 jar，约 5MB）
2. 自动启用安全过滤链，需要额外编写 SecurityConfig 来禁用
3. 下载和编译速度慢

**决策**: 改用 `org.mindrot:jbcrypt:0.4` 轻量级库

**方案详情**:
- 在 `PasswordEncoderConfig` 中封装 `BCryptPasswordEncoder` 内部类
- 提供 `encode(CharSequence)` 和 `matches(CharSequence, String)` 方法
- 接口风格与 Spring Security 的 `BCryptPasswordEncoder` 保持一致
- 加密强度默认 10 轮迭代

**关联文件**:
- `pom.xml` — 添加 jbcrypt 依赖
- `config/PasswordEncoderConfig.java` — BCrypt 加密 Bean
- `service/impl/AuthServiceImpl.java` — 使用 passwordEncoder 进行加密和验证

---

## ADR-002: 认证模块使用 jjwt 0.12.x 生成 JWT Token

**日期**: 2026-07-08

**问题**: 注册和登录成功后需要返回 JWT Token 用于后续请求的身份验证

**决策**: 使用 `io.jsonwebtoken:jjwt:0.12.6` 库

**方案详情**:
- 密钥配置在 `application.properties` 的 `jwt.secret` 字段
- Token 中存储：userId（subject）、username（claim）、role（claim）
- 过期时间 24 小时，配置在 `jwt.expiration`
- 使用 HMAC-SHA256 算法签名

**关联文件**:
- `pom.xml` — 添加 jjwt-api、jjwt-impl、jjwt-jackson 依赖
- `config/JwtConfig.java` — JWT 生成与解析工具类
- `application.properties` — jwt.secret 和 jwt.expiration 配置

---

## ADR-003: 认证模块使用 MyBatis-Plus LambdaQueryWrapper 查询用户

**日期**: 2026-07-08

**问题**: 注册时需要检查用户名是否存在，登录时需要根据用户名查询用户

**决策**: 使用 MyBatis-Plus 的 `LambdaQueryWrapper` 进行条件查询

**方案详情**:
- 类型安全：字段名使用 `User::getUsername` 方法引用，编译期检查
- 无需手写 SQL，BaseMapper 的 `selectOne` 方法即可满足需求
- 配置 `map-underscore-to-camel-case=true` 自动映射数据库字段

**关联文件**:
- `mapper/UserMapper.java` — 继承 BaseMapper<User>
- `service/impl/AuthServiceImpl.java` — 使用 LambdaQueryWrapper 查询
- `application.properties` — MyBatis-Plus 配置

---

## ADR-004: 参会率预测模型排除距离系数

**日期**: 2026-07-09

**问题**: 在讨论如何预测参会率时，大模型根据文档给出了一个"距离系数"（根据当前日期距离活动开始的天数计算到场概率），但实际上该系数对预测参会率没有实际意义——它只随着时间自然增长，不反映任何用户行为或活动特征。

**决策**: 排除距离系数，改用基于数据库统计的四因子加权模型

**方案详情**:
- 预测率 = 历史签到率 x 0.50 + 热度 x 0.20 + 购票集中度 x 0.20 + 时段因子 x 0.10
- 历史签到率：该举办方所有已结束活动的总签到数 / 总票数
- 热度：已售票数 / 总票数
- 购票集中度：按甲乙丙丁四个时段分别统计购票分布，加权合成后映射为 0~1 因子
- 时段因子：根据活动开始时间所在时段（甲/乙/丙/丁）赋予不同权重
- 距离系数被排除的原因：该系数仅由时间推移决定，不包含任何用户行为或活动属性信息，无法提供有效的预测信号

**关联文件**:
- `service/impl/DashboardServiceImpl.java` — `calculateArrivalRate()` 方法实现四因子加权模型
- `service/DashboardService.java` — 参会率预测接口定义
- `dto/response/PredictionResponse.java` — 预测结果响应体

---

## ADR-005: 创建 application.properties 导致 JWT 配置缺失启动失败

**日期**: 2026-07-13

**问题**: 项目原本没有 `application.properties` 文件，JWT 密钥 `jwt.secret` 和数据库连接配置均未定义。创建 `application.properties` 后，Spring Boot 启动时因缺少 `jwt.secret` 配置而报错 `Could not resolve placeholder 'jwt.secret'`。

**决策**: 在 `application.properties` 中补充 JWT 配置，使用环境变量 + 默认值的方式。

**方案详情**:
- `jwt.secret` 使用 `${JWT_SECRET:默认密钥}` 格式，开发环境用默认值，生产环境通过环境变量 `JWT_SECRET` 覆盖
- `jwt.expire-ms` 设为 86400000（24 小时）
- 数据库连接配置（`spring.datasource.url` 等）尚未添加，需后续补充

**教训**: 创建 `application.properties` 时应先检查项目中所有 `@Value("${...}")` 注解，确保所有占位符都有对应配置，避免启动失败。

**关联文件**:
- `src/main/resources/application.properties` — 新增的配置文件
- `util/JwtUtil.java` — 使用 `@Value("${jwt.secret}")` 注入密钥

---

## ADR-006: EventServiceImpl 注入 AiService 接口而非 MockAiService 具体类

**日期**: 2026-07-13

**问题**: 配置 `llm.provider=deepseek` 后，`MockAiService` 被 `@ConditionalOnProperty` 跳过，但 `EventServiceImpl` 直接注入 `MockAiService` 具体类，导致 Spring 找不到匹配的 Bean，启动失败。

**决策**: 将 `EventServiceImpl` 中的注入类型从 `MockAiService` 改为接口 `AiService`，利用 Spring 的多态注入自动匹配当前可用的实现（DeepSeekAiService）。

**方案详情**:
- import: `com.easysign.ai.MockAiService` → `com.easysign.ai.AiService`
- 字段: `MockAiService mockAiService` → `AiService aiService`
- 调用处: `mockAiService.generateDescription(title)` → `aiService.generateDescription(title)`

**关联文件**:
- `src/main/java/com/easysign/service/impl/EventServiceImpl.java` — 注入类型改为接口
- `src/main/resources/application.properties` — 新增 `llm.provider=deepseek` 配置

---

## ADR-007: 补写 DeepSeekAiService.generateDescription 方法

**日期**: 2026-07-13

**问题**: `DeepSeekAiService.generateDescription()` 方法直接抛出 `UnsupportedOperationException`，导致调用生成文案功能时无法返回大模型结果。

**决策**: 基于已有的 `generateRecommendReason` 方法实现逻辑，补写 `generateDescription` 方法，调用 DeepSeek API 生成宣传文案。

**方案详情**:
- prompt 设计为"活动宣传文案撰写助手"，要求简洁有力、突出亮点、不超过100字
- 复用已有的 HTTP 调用、重试退避、响应解析逻辑
- prompt 集中在方法开头，后续优化只需修改 prompt 字符串，不影响 `generateRecommendReason`

**关联文件**:
- `src/main/java/com/easysign/ai/DeepSeekAiService.java` — 补写 `generateDescription` 方法

---

## ADR-008: 前端 EventEditView.handlePolish 改为调用后端 DeepSeek API

**日期**: 2026-07-13

**问题**: 编辑活动页面的"AI 润色"功能使用前端本地模板生成文案，完全没有调用后端 API，导致 DeepSeek 接入不生效。

**决策**: 将 `handlePolish` 函数从本地模板生成改为调用后端 `generateAIDescriptionAPI`，让文案由 DeepSeek 大模型生成。

**方案详情**:
- import 新增 `generateAIDescriptionAPI`（该 API 封装已存在）
- `handlePolish` 改为 `async` 函数，先调后端 API 获取文案，再复用原有的流式输出效果
- 保留 `copyTemplates` 数组（未删除），避免影响其他可能引用处

**关联文件**:
- `frontend/src/views/b-end/EventEditView.vue` — `handlePolish` 函数改为调后端 API

---

## ADR-009: AI 生成文案接口返回 aiProvider 标识字段

**日期**: 2026-07-13

**问题**: 调用 AI 生成活动文案时，后端和浏览器控制台都无法区分当前使用的是 MockAiService 还是 DeepSeekAiService，导致调试困难。

**决策**: 
1. 后端 `EventController.generateAiDescription` 响应中增加 `aiProvider` 字段，值为 `"mock"` 或 `"deepseek"`
2. 后端 `EventServiceImpl.generateAiDescription` 增加日志，打印当前注入的 AI 实现类名
3. 前端 `handlePolish` 根据 `aiProvider` 在控制台打印明确的标识信息
4. 清理前端 `handlePolish` 中冗余的调试日志（`console.time`、`console.timeEnd`、详细的请求/响应日志等）

**方案详情**:
- `EventController` 注入 `AiService`，通过 `aiService.getClass().getSimpleName()` 获取实现类名，转换为小写后作为 `aiProvider` 返回
- `EventServiceImpl` 添加 `log.info("当前 AI 实现类: {}", aiService.getClass().getSimpleName())`
- 前端控制台输出格式：`[AI 润色] provider=deepseek, 当前使用: DeepSeek 真实模型`

**关联文件**:
- `src/main/java/com/easysign/controller/EventController.java` — 响应增加 `aiProvider` 字段
- `src/main/java/com/easysign/service/impl/EventServiceImpl.java` — 增加 AI 实现类日志
- `frontend/src/views/b-end/EventEditView.vue` — 根据 `aiProvider` 打印标识，清理调试日志

---

## ADR-010: 创建活动页面 EventCreateView.handlePolish 改为调用后端 API

**日期**: 2026-07-13

**问题**: 创建活动页面（`/b/events/create`）的"AI 润色"按钮和"重新生成"按钮使用前端本地 `copyTemplates` 模板数组生成文案，完全没有调用后端 API，导致无法区分是 Mock 还是 DeepSeek。

**决策**: 将 `handlePolish` 函数从本地模板生成改为调用后端 `generateAIDescriptionAPI`，与编辑页面保持一致。

**方案详情**:
- import 新增 `generateAIDescriptionAPI`
- `handlePolish` 改为 `async` 函数，调用后端 API 获取文案
- 传 `eventId=0`（创建页面尚无活动 ID，后端在 title 不为空时不会查数据库）
- 保留 `copyTemplates` 数组（未删除），避免影响其他可能引用处

**关联文件**:
- `frontend/src/views/b-end/EventCreateView.vue` — `handlePolish` 函数改为调后端 API
