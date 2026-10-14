# 系统架构设计 (Architecture)

## 1. 整体架构
本项目为 **活动票务与智能签到系统 (W6)**，采用典型的前后端分离架构 (Monolith 单体后端 + SPA 单页前端)。

## 2. 前端架构 (纯 Web 单系统，双视角路由)
为兼顾开发效率，本项目采用"一库双端"的伪响应式架构：
- **B端 (举办方后台)**：针对 PC 桌面端优化，采用典型的左侧菜单 + 右侧内容布局。核心包含数据大屏 (Dashboard) 和活动管理。
- **C端 (用户前台)**：针对手机屏幕尺寸设计 (宽度固定为 375px - 414px)，在 PC 浏览器中居中带边框显示，模拟移动端操作体验。
- **路由鉴权**：通过前端 Vue Router 的全局前置守卫 (beforeEach)，根据用户角色 (普通用户 / 举办方) 跳转至对应视角的页面。

## 3. 后端架构 (SpringBoot 分层)
采用面向 MVP 的七层架构，在标准 MVC 四层基础上补齐基础设施层，确保安全与可维护性：

```
backend/
├── config/          # 配置类：CORS 跨域、JWT 拦截器注册、MyBatis-Plus 分页插件、Jackson 序列化
├── common/          # 公共组件：Result<T> 统一响应体、GlobalExceptionHandler 全局异常处理、枚举常量
├── controller/      # 控制层：接收 HTTP 请求、参数校验（结合 DTO 的 @Valid）、调用 Service 并返回 Result
├── service/         # 核心业务逻辑层（包含防超卖算法、特征工程预测算法）
├── mapper/          # 数据访问层 (DAO)，通过 MyBatis-Plus 与 MySQL 交互
├── entity/          # 数据库映射实体类
└── dto/             # 数据传输对象（请求/响应体），按需创建，与 entity 解耦
```

各层职责详述：

| 层 | 职责 | 注意 |
|----|------|------|
| `config/` | 注册拦截器、配置 CORS、注入分页插件、配置 JSON 日期格式 | **不引入复杂配置**，仅满足项目基本运行需求 |
| `common/` | 统一响应体 `Result<T>`、全局异常处理 `BusinessException` + `GlobalExceptionHandler`、枚举类 `Enums` | 所有 controller 统一返回 `Result`，业务异常统一抛 `BusinessException` |
| `controller/` | 接收请求、参数校验、调用 service 方法、返回 `Result` | 不做任何业务逻辑，不做数据组装 |
| `service/` | 核心业务逻辑：防超卖、签到状态机、推荐权重计算、AI 调用编排 | **事务管理在此层**，通过 `@Transactional` 控制 |
| `mapper/` | 继承 MyBatis-Plus `BaseMapper`，直接操作数据库 | 复杂 SQL 使用 `@Select` / `@Update` 注解或 XML 映射 |
| `entity/` | ORM 映射到数据库表，字段与表严格对应 | **不直接序列化给前端**，通过 DTO 转换后返回 |
| `dto/` | 请求体（如 `RegisterRequest`）和响应体（如 `UserProfileDTO`），按需按接口创建 | 避免了 entity 暴露敏感字段，同时允许聚合多表数据 |

## 4. 核心系统模块划分
1. **认证与用户模块**: 极简 JWT 或 Session 登录。
2. **活动管理模块**: 举办方发布活动、列表展示。
3. **票务并发模块**: C端抢票防超卖（AI 禁飞区①）。
4. **扫码核销模块**: C端动态二维码生成 + B端摄像头扫码鉴权（防重复防作弊）。
5. **AI 数据与推荐模块**: 特征工程计算到达率、基于权重的个性化推荐（AI 禁飞区②③）、大模型 API 文本润色。
