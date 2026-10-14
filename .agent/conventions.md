# 编码规范与技术栈约定 (Conventions)

## 1. 技术栈基底
- **前端**: Vue3 (必须使用 `<script setup>` Composition API语法), Vite, Pinia, Vue-Router, Element-Plus, ECharts.
- **后端**: Java 17+, SpringBoot 3.x, MyBatis-Plus, MySQL 8.x.
- **AI/LLM**: DeepSeek HTTP API (用于文本润色和预测报告生成).

## 2. 编码与命名规范
- **Java**: 类名 `PascalCase`，变量与方法名 `camelCase`。数据库实体类必须与数据库表严格对应。
- **Vue**: 组件名使用 `PascalCase` (如 `EventList.vue`)，方法名 `camelCase`。
- **数据库**: 表名与字段名统一使用 `snake_case` (如 `user_id`, `activity_info`)，**不建立物理外键**，纯逻辑关联。
- **注释**: 核心复杂逻辑（特别是防超卖并发、算法权重计算）必须有详细的中/英文注释。

## 3. API 接口规范
- 所有后端接口必须返回统一的 JSON 结构体：
  ```json
  {
    "code": 200,          // 200成功，500系统错误，400业务异常
    "msg": "success",     // 错误提示信息
    "data": {}            // 具体的返回数据
  }
  RESTful 约束: 尽量使用 GET 获取数据，POST 提交数据。API 路径统一带 /api/v1/ 前缀。
## 4. 代码纪律 (极简原则)
拒绝过度设计：这是一个 12 天的 MVP 敏捷项目。请不要引入 Redis、消息队列、微服务等复杂中间件。
专注单机方案：并发控制优先考虑单机锁（ReentrantLock）或数据库防超卖机制。