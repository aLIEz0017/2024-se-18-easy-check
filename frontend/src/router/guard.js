/**
 * 路由守卫逻辑
 * 
 * 全局前置守卫，负责角色鉴权和登录跳转。
 * 组员A 实现，组员A 和 组员C 共同依赖。
 * 
 * 设计说明：
 * - 守卫逻辑写在独立的 guard.js 而非内联在 index.js 中：职责分离，
 *   index.js 只负责创建 router 实例，guard.js 只负责守卫逻辑，
 *   便于维护和测试。
 * - 使用 localStorage 而非 Pinia 判断登录状态：路由守卫执行时 Pinia Store
 *   可能尚未初始化（特别是页面刷新时），localStorage 是同步且可靠的。
 * - 角色不匹配时跳转到对应用户的首页而非登录页：已登录用户角色不匹配说明
 *   走错了页面，直接引导到正确的首页体验更好。
 * 
 * 守卫逻辑对照表（与文档 2.4 节一致）：
 * | 当前状态 | 访问路径 | 跳转结果 |
 * | 未登录 | / 或 /login | ✅ 放行 |
 * | 未登录 | 需登录页面 | 🔀 /login |
 * | organizer | /c/* | 🔀 /b/dashboard |
 * | attendee | /b/* | 🔀 /c/hall |
 */

import { STORAGE_KEYS } from '@/utils/constants'

export function setupRouterGuard(router) {
  router.beforeEach((to, from, next) => {
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN)
    const userRole = (localStorage.getItem(STORAGE_KEYS.USER_ROLE) || '').toLowerCase() // 'attendee' | 'organizer' | null

    // 情况1：访问开始界面或登录页 → 始终放行
    if (to.path === '/' || to.path === '/login') {
      return next()
    }

    // 情况2：访问需要登录的页面但未登录 → 跳转登录页
    if (to.meta.requiresAuth && !token) {
      return next('/login')
    }

    // 情况3：访问需要特定角色的页面但角色不匹配
    if (to.meta.role && to.meta.role !== userRole) {
      if (userRole === 'organizer') return next('/b/dashboard')
      if (userRole === 'attendee') return next('/c/hall')
      return next('/login')
    }

    // 情况4：其他情况放行
    next()
  })
}
