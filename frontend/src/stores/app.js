/**
 * 全局 Store
 * 
 * 管理应用级别的全局状态，如侧边栏折叠、主题等。
 * 组员A 实现，组员A 和 组员C 共同使用。
 * 
 * 设计说明：
 * - 将侧边栏折叠状态放在全局 Store 而非组件局部状态，是因为 BLayout 的侧边栏
 *   和内容区域都需要响应此状态，且未来可能需要在其他页面控制侧边栏。
 * - sidebarCollapsed 默认 false（展开状态），符合后台管理系统的常见默认行为。
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // ========== State ==========
  /** 侧边栏是否折叠 */
  const sidebarCollapsed = ref(false)

  // ========== Actions ==========
  /** 切换侧边栏折叠状态 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    // State
    sidebarCollapsed,
    // Actions
    toggleSidebar
  }
})
