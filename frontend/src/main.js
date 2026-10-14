/**
 * 应用入口
 *
 * 创建 Vue 应用实例，注册全局插件。
 * 组员A 负责维护此文件。
 *
 * 注册顺序说明：
 * 1. Element-Plus 先注册：确保全局组件（如 el-button）在路由组件中可用
 * 2. Pinia 先于 router 注册：router 的 guard.js 中不依赖 Pinia（使用 localStorage），
 *    但页面组件中会同时使用 Pinia 和 router，两者注册顺序无严格依赖。
 *    不过 Pinia 先注册是 Vue 官方推荐的最佳实践。
 * 3. router 最后注册：因为 router 的懒加载组件可能在挂载后才需要，
 *    且 app.use(router) 会触发初始路由导航。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)

// 注册 Pinia 状态管理
app.use(createPinia())

// 注册 Element-Plus UI 库
app.use(ElementPlus)

// 全局注册 Element-Plus 图标（方便在模板中直接使用 <el-icon><icon-name /></el-icon>）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 注册路由
app.use(router)

// 挂载应用
app.mount('#app')
