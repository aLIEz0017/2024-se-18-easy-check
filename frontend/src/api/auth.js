/**
 * 认证模块 API
 * 
 * 对应后端接口文档第二章「认证模块」：
 * - POST /api/v1/auth/register
 * - POST /api/v1/auth/login
 * - GET  /api/v1/user/profile
 * 
 * 组员A 实现，组员A 和 组员C 共同使用。
 */

import request from './request'

/**
 * 用户注册
 * @param {Object} data - 注册信息
 * @param {string} data.username - 用户名，长度 2-50 字符
 * @param {string} data.password - 密码，长度 6-50 字符
 * @param {string} data.role - 角色：'attendee' | 'organizer'
 * @returns {Promise<{code: number, msg: string, data: {token: string, userId: number, username: string, role: string}}>}
 */
export function registerAPI(data) {
  return request.post('/auth/register', data)
}

/**
 * 用户登录
 * @param {Object} data - 登录信息
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @returns {Promise<{code: number, msg: string, data: {token: string, userId: number, username: string, role: string}}>}
 */
export function loginAPI(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前用户信息
 * @returns {Promise<{code: number, msg: string, data: {userId: number, username: string, role: string, gender: string|null, tags: string[]|null, avatarUrl: string, createdAt: string}}>}
 */
export function getUserProfileAPI() {
  return request.get('/user/profile')
}
