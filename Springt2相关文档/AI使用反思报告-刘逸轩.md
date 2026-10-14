# AI 使用反思报告


## 1. 认证模块：AI 建议直接引入 Spring Security

**问AI的问题**：怎么在 SpringBoot 里做密码加密和 JWT 登录？

**AI 给了什么**：建议引入 `spring-boot-starter-security`，配 `SecurityConfig` 和 `BCryptPasswordEncoder`。

**最终方案**：AI 方案导致依赖过大（10+ jar）且自动开启安全过滤链，需要额外配置禁用。我决定用 `org.mindrot:jbcrypt:0.4` 轻量库替代，手写拦截器处理 token，不引入 Security。最终依赖从 5MB 降到 50KB，启动快 2 秒。


## 2. 认证模块：AI 生成的拦截器把更新接口也放行了

**问AI的问题**：怎么配置拦截器放行公开接口？

**AI 给了什么**：在 `WebMvcConfig` 里用 `excludePathPatterns("/api/v1/event/*")` 放行活动详情。

**最终方案**：AI 没区分 HTTP 方法，把 `PUT /api/v1/event/{id}`（更新活动）也放行了，导致 `UserContext.getUserId()` 返回 `null`，更新接口一直报 4002。我把拦截器从“路径放行”改成“方法过滤”——在 Controller 里对公开接口手动处理 token，不放行整个路径。


## 3. 签到模块：AI 的三版正则表达式都是错的

**问AI的问题**：签到二维码解析一直返回 4103，怎么修？

**AI 给了什么**：AI 改了三遍 `FULL_PATTERN`，每次都写 `ticket_(\\d+)_(.+)_(\\d{10,})$`，说“这次应该对了”。

**最终方案**：AI 没看清 `ticketNo` 的格式是 `{eventId}_{userId}_{timestamp}`，一直在用 `ticket_{eventId}_{ticketNo}_{timestamp}` 去匹配，结果 `ticketNo` 里已经包含了 `eventId`，解析时 `eventId` 重复，查不到票。我手动改成 `^ticket_(\\d+_\\d+_\\d{10,})_(\\d{10,})$`，把整个 `ticketNo` 作为一个整体提取，改完后一次性通过。


## 4. 接入 DeepSeek：AI 直接删了 MockAiService

**问AI的问题**：怎么把 AI 文案生成从 Mock 切换到 DeepSeek？

**AI 给了什么**：建议直接删除 `MockAiService`，全部替换成 `DeepSeekAiService`。

**最终方案**：AI 没考虑 API Key 没配、DeepSeek 服务不可用、网络不通的情况。如果直接删 Mock，API 一挂整个文案生成功能就废了。我坚持保留 `MockAiService`，在 `EventServiceImpl` 里加入 `aiProvider` 判断，当 `DEEPSEEK_API_KEY` 为空或调用失败时自动降级到 Mock。现在前后端都能看到当前用的是哪个 Provider。


## 5. 二维码扫码：AI 推荐了不兼容的方案

**问AI的问题**：签到扫码页面用什么方案实现二维码解码？

**AI 给了什么**：推荐用浏览器原生 `BarcodeDetector` API。

**最终方案**：AI 没提供降级方案，结果 Chrome 能识别，Edge 有时不行，Firefox 完全不支持。我改用 `jsQR` 纯前端解码库，在所有浏览器上都能稳定工作。同时保留手动输入票号作为兜底方案。


## 6. 配置管理：AI 覆盖了 application.properties

**问AI的问题**：合并分支后启动报错 `Could not resolve placeholder 'jwt.secret'`，怎么解决？

**AI 给了什么**：AI 建议“重新创建一份默认配置”，但没有提醒我备份现有配置。

**最终方案**：AI 没意识到是合并时 `application.properties` 被远程覆盖了。我手动重建配置文件，从之前的备份里找回 JWT 密钥和数据库连接配置。后来把 `.clinerules.md` 加入 `.gitignore`，防止被 Git 操作影响。


## 总结

AI 在代码生成上的效率很高，但会犯三类错误：
1. **方案选型过度**：动不动就引入大框架，需要手压成轻量方案
2. **边界条件缺失**：只给主路径，不提供降级方案和异常处理
3. **上下文中断**：记不住项目里的字段格式和已有约定，经常重新生成错误的版本

应对策略：每次让 AI 写完代码后，强制它列出“什么情况下会失败”，然后自己补降级逻辑。


## 附录：关键决策记录

另见 `decisions.md`，其中记录了以下架构决策：
- ADR-001: 认证模块使用 jBCrypt 替代 Spring Security
- ADR-002: 认证模块使用 jjwt 0.12.x 生成 JWT Token
- ADR-003: 认证模块使用 MyBatis-Plus LambdaQueryWrapper 查询用户
- ADR-004: 参会率预测模型排除距离系数
- ADR-005: 创建 application.properties 导致 JWT 配置缺失启动失败
- ADR-006: EventServiceImpl 注入 AiService 接口而非 MockAiService 具体类
- ADR-007: 补写 DeepSeekAiService.generateDescription 方法
- ADR-008: 前端 EventEditView.handlePolish 改为调用后端 DeepSeek API
- ADR-009: AI 生成文案接口返回 aiProvider 标识字段
- ADR-010: 创建活动页面 EventCreateView.handlePolish 改为调用后端 API

---