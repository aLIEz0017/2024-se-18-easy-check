/**
 * Axios 请求封装
 * 
 * 统一管理 baseURL、请求拦截器（Token 注入）、响应拦截器（统一错误处理）。
 * 组员A 实现，组员A 和 组员C 共同使用。
 * 
 * 设计说明：
 * - 使用 Axios 而非 fetch：Axios 提供拦截器机制，便于统一处理 Token 注入和错误响应；
 *   且对请求/响应的转换更灵活（如自动 JSON 解析）。
 * - Token 存 localStorage 而非 sessionStorage：用户关闭标签页后重新打开无需重新登录，
 *   符合"登录一次管 24 小时"的设计。风险是 XSS 攻击可窃取 token，但本项目为校内 MVP，
 *   安全级别可接受。
 * - 响应拦截器中统一处理 4001（认证失败）跳转登录页，避免每个页面单独写重复逻辑。
 */

import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { STORAGE_KEYS } from '@/utils/constants'

const request = axios.create({
  baseURL: '/api/v1',  // 开发环境通过 Vite proxy 转发到后端
  timeout: 10000        // 10 秒超时
})

// 请求拦截器：注入 Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  response => {
    const res = response.data
    // 业务码非 200 视为业务异常
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')

      // Token 过期或无效 → 清除登录状态并跳转登录页
      if (res.code === 4001) {
        localStorage.removeItem(STORAGE_KEYS.TOKEN)
        localStorage.removeItem(STORAGE_KEYS.USER_ROLE)
        localStorage.removeItem(STORAGE_KEYS.USER_INFO)
        router.push('/login')
      }

      return Promise.reject(new Error(res.msg))
    }
    return res
  },
  error => {
    // 网络错误或服务器未响应
    ElMessage.error('网络错误，请检查网络连接')
    return Promise.reject(error)
  }
)

export default request
