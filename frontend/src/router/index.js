/**
 * 路由主文件
 * 
 * 创建 router 实例并注册守卫。
 * 组员A 负责维护此文件。
 */

import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import { setupRouterGuard } from './guard'

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 注册全局前置守卫
setupRouterGuard(router)

export default router
