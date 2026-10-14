/**
 * 用户模块 API
 * 
 * 对应后端接口文档第三章「用户模块」：
 * - PUT /api/v1/user/profile
 * 
 * 组员A 实现，组员A 和 组员C 共同使用。
 */

import request from './request'

/**
 * 更新用户信息（仅 attendee 角色可调用）
 * @param {Object} data - 要更新的信息
 * @param {string} [data.gender] - 性别：'male' | 'female'
 * @param {string[]} [data.tags] - 偏好标签数组
 * @returns {Promise<{code: number, msg: string, data: null}>}
 */
export function updateUserProfileAPI(data) {
  return request.put('/user/profile', data)
}
