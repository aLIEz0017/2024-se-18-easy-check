/**
 * 路由表定义
 * 
 * 所有路由集中管理，按模块分组。
 * 组员A 负责维护此文件，组员C 如需新增 C端路由需在此文件添加并通知组员A。
 * 
 * 设计说明：
 * - 使用懒加载（() => import(...)）而非静态导入：减少首屏加载体积，
 *   Vue Router 会自动按需加载对应页面组件。
 * - B端路由统一挂在 /b 下作为 BLayout 的子路由：BLayout 提供侧边栏+顶栏布局，
 *   子路由只渲染内容区域，避免每个页面重复写布局代码。
 * - C端路由同理挂在 /c 下作为 CLayout 的子路由。
 * - 兜底路由重定向到 /（开始界面），而非 404 页面：MVP 阶段不单独做 404 页面，
 *   直接回到首页更友好。
 * 
 * 新增路由说明（v1.3）：
 * - /b/dashboard/event/:id → EventDataDetailView：活动数据详情页
 *   从 DashboardView 的活动列表中点击活动卡片跳转至此页，
 *   数据详情页数量与活动数量一致，每个活动都有独立的数据详情页。
 */
const routes = [
  // ========== 公共页面 ==========
  {
    path: '/',
    name: 'Start',
    component: () => import('@/views/start/StartView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false }
  },

  // ========== B端 - 举办方后台（组员A 负责）==========
  {
    path: '/b',
    component: () => import('@/layouts/BLayoutShell.vue'),
    meta: { requiresAuth: true, role: 'organizer' },
    redirect: '/b/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'BDashboard',
        component: () => import('@/views/b-end/DashboardScreen.vue'),
        meta: { title: '数据大屏' }
      },
      {
        // 活动数据详情页：从 Dashboard 活动列表点击跳转
        // 数据详情页数量与活动数量一致，每个活动都有独立的数据详情页
        path: 'dashboard/event/:id',
        name: 'BEventDataDetail',
        component: () => import('@/views/b-end/EventDataDetailView.vue'),
        meta: { title: '活动数据详情' }
      },
      {
        path: 'events',
        name: 'BEventList',
        component: () => import('@/views/b-end/EventManageView.vue'),
        meta: { title: '活动管理' }
      },
      {
        path: 'events/create',
        name: 'BEventCreate',
        component: () => import('@/views/b-end/EventCreateView.vue'),
        meta: { title: '发布活动' }
      },
      {
        path: 'events/:id/edit',
        name: 'BEventEdit',
        component: () => import('@/views/b-end/EventEditView.vue'),
        meta: { title: '编辑活动' }
      },
      {
        path: 'checkin',
        name: 'BCheckin',
        component: () => import('@/views/b-end/CheckinWorkbenchView.vue'),
        meta: { title: '扫码核销' }
      }
    ]
  },

  // ========== C端 - 参会者前台（组员C 负责）==========
  {
    path: '/c',
    component: () => import('@/layouts/CLayout.vue'),
    redirect: '/c/hall',
    children: [
      {
        path: 'hall',
        name: 'CActivityHall',
        component: () => import('@/views/c-end/ActivityHallView.vue'),
        meta: { requiresAuth: false }
      },
      {
        path: 'event/:id',
        name: 'CEventDetail',
        component: () => import('@/views/c-end/EventDetailView.vue'),
        meta: { requiresAuth: true, role: 'attendee' }
      },
      {
        path: 'tickets',
        name: 'CMyTickets',
        component: () => import('@/views/c-end/MyTicketsView.vue'),
        meta: { requiresAuth: true, role: 'attendee' }
      },
      {
        path: 'profile',
        name: 'CProfile',
        component: () => import('@/views/c-end/ProfileView.vue'),
        meta: { requiresAuth: true, role: 'attendee' }
      }
    ]
  },

  // ========== 兜底重定向 ==========
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/'
  }
]

export default routes