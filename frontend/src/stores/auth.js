/**
 * 认证 Store
 * 
 * 管理用户登录状态、Token、用户信息。
 * 组员A 实现，组员A 和 组员C 共同使用。
 * 
 * 设计说明：
 * - 使用 Pinia（Vue3 官方推荐）而非 Vuex：Pinia 更轻量（无 mutations）、完整 TypeScript 支持、
 *   Composition API 风格与 <script setup> 天然契合。Vuex 在 Vue3 中虽可用但已被 Pinia 取代。
 * - token 和 userRole 同时存 localStorage 和 Pinia ref：localStorage 用于页面刷新后恢复状态，
 *   Pinia ref 用于运行时响应式访问。两者保持同步。
 * - userInfo 不持久化到 localStorage（仅存 token 和 role）：userInfo 包含较多字段，
 *   且页面刷新后可通过 token 调用 getUserProfileAPI 重新获取，避免数据不一致。
 */

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginAPI, registerAPI, getUserProfileAPI } from '@/api/auth'
import { STORAGE_KEYS } from '@/utils/constants'

export const useAuthStore = defineStore('auth', () => {
  // ========== State ==========
  const token = ref(localStorage.getItem(STORAGE_KEYS.TOKEN) || '')
  const userInfo = ref(null)

  // ========== Getters ==========
  const isLoggedIn = computed(() => !!token.value)
  const userRole = computed(() => userInfo.value?.role || localStorage.getItem(STORAGE_KEYS.USER_ROLE))

  // ========== Actions ==========

  /**
   * 登录
   * @param {string} username
   * @param {string} password
   * @returns {Promise<string>} 用户角色，用于路由跳转
   */
  async function login(username, password) {
    const res = await loginAPI({ username, password })
    const { token: newToken, userId, username: userName, role } = res.data

    // 更新状态
    token.value = newToken
    userInfo.value = { userId, username: userName, role }

    // 持久化到 localStorage
    localStorage.setItem(STORAGE_KEYS.TOKEN, newToken)
    localStorage.setItem(STORAGE_KEYS.USER_ROLE, role)
    localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify({ userId, username: userName, role }))

    return role
  }

  /**
   * 注册
   * @param {string} username
   * @param {string} password
   * @param {string} role - 'attendee' | 'organizer'
   * @returns {Promise<string>} 用户角色，用于判断跳转
   */
  async function register(username, password, role) {
    const res = await registerAPI({ username, password, role })
    const { token: newToken, userId, username: userName, role: userRole } = res.data

    // 保存临时 token，用于注册后跳转偏好设置页
    token.value = newToken
    userInfo.value = { userId, username: userName, role: userRole }

    // 持久化到 localStorage
    localStorage.setItem(STORAGE_KEYS.TOKEN, newToken)
    localStorage.setItem(STORAGE_KEYS.USER_ROLE, userRole)
    localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify({ userId, username: userName, role: userRole }))

    return userRole
  }

  /**
   * 获取当前用户信息（从后端获取最新数据）
   */
  async function fetchUserProfile() {
    try {
      const res = await getUserProfileAPI()
      userInfo.value = res.data
      localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(res.data))
      localStorage.setItem(STORAGE_KEYS.USER_ROLE, res.data.role)
    } catch (e) {
      // 获取失败不阻塞流程，保留已有信息
      console.warn('获取用户信息失败:', e)
    }
  }

  /**
   * 登出
   */
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(STORAGE_KEYS.TOKEN)
    localStorage.removeItem(STORAGE_KEYS.USER_ROLE)
    localStorage.removeItem(STORAGE_KEYS.USER_INFO)
  }

  return {
    // State
    token,
    userInfo,
    // Getters
    isLoggedIn,
    userRole,
    // Actions
    login,
    register,
    fetchUserProfile,
    logout
  }
})
