# AI 反思日志

> 开发者：樊昱天 
> 日期：2026-07-07 ~ 2026-07-15  
> 项目：EasySign 易签 — 活动票务管理平台

---

## 一、AI 帮助完成了什么

### 1. 快速搭建完整的前端页面体系

ActivityHallView.vue（活动大厅）
EventDetailView.vue（活动详情）
MyTicketsView.vue（我的票夹）
ProfileView.vue（个人中心）
SeatPicker.vue（选座组件）
CLayout.vue（C端布局）

AI 生成了这 6 个核心页面的骨架和业务逻辑，我只需做少量调整和对接。

### 2. 实现复杂交互

- **GSAP 页面过渡动画**：大厅→详情页的白色展开动画、返回时缩放回卡片
- **SeatPicker 选座组件**：区域选择（A/B/C/D 四区）→ 座位网格 → 选座确认 → 抢票
- **二维码签到**：60 秒自动刷新倒计时 + Code 格式 `ticket_{eventId}_{ticketNo}_{timestamp}`
- **WebSocket 实时座位更新**：详情页连接 WS 接收 seat_sold/seat_sync 事件
- **AI 推荐模块**：基于用户偏好调用推荐接口，展示推荐活动卡片
- **活动大厅搜索/筛选**：搜索防抖 300ms + 标签筛选 + "仅看有余票"开关

---

## 二、踩过的坑

### 🔴 坑 1：命令语法混用

运行 `dir /s /b frontend\src\**\*test*` 时报错参数错误。`dir` 在 cmd.exe 中是内部命令，但在 PowerShell 中是 `Get-ChildItem` 的别名，语法不同。

**教训：** 执行命令前要确认当前 shell 类型，根据它调整语法。

### 🔴 坑 2：不能完全自行扩展阅读其他代码文件

前端设计时，ai只专注于眼前的vue文件，而忽略了css文件，导致前端怎么修改都不成功，最后由经提醒才改过来

**教训：** 给ai尽可能详细的规划和约束，包括但不限于哪个文件等等。

### 🔴 坑 3：sessionStorage 多 Tab 共享

活动大厅使用 `sessionStorage` 保存滚动位置和卡片动画数据。用户同时打开两个浏览器窗口时，数据会互相覆盖，导致滚动位置混乱。

**教训：** 多 Tab 场景下 sessionStorage 不是安全的存储方案，可以考虑加 Tab ID 前缀或改用 URL query 传参。

---

## 三、学到的经验

### ✅ 经验 1：防御性编程

```js
// Bad: 没有防御
function remainingTickets(event) {
  return event.totalTickets - event.soldTickets  // event 可能是 null！
}

// Good: 防御三件套
function remainingTickets(event) {
  if (!event) return 0                          // 1. null 保护
  if (event.totalTickets == null) return 0      // 2. 缺失字段保护
  return Math.max(0, total - sold)              // 3. 非负保护
}
```

### ✅ 经验 2：组件间通信用 emit，避免 prop 链过长

SeatPicker 组件选座后通过 `@purchase` 事件将选座数据传给父组件，父组件调用抢票 API。如果通过多级 prop 传递会导致数据流不清晰。


---

