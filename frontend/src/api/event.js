/**
 * 活动模块 API
 * 
 * 对应后端接口文档第四章「活动模块」：
 * - POST   /api/v1/event
 * - GET    /api/v1/event/list
 * - GET    /api/v1/event/{eventId}
 * - PUT    /api/v1/event/{eventId}
 * - PATCH  /api/v1/event/{eventId}/status
 * - DELETE /api/v1/event/{eventId}
 * - POST   /api/v1/event/{eventId}/ai-description（AI 禁飞区）
 * 
 * 组员A 最先使用此模块，负责创建初始版本。
 * 组员C 在 C端活动详情页也会用到 GET /api/v1/event/{eventId}。
 */

import request from './request'

/**
 * 发布活动（仅 organizer 角色）
 * @param {Object} data - 活动信息
 * @param {string} data.title - 活动标题，1-100 字符
 * @param {string} [data.description] - 活动描述
 * @param {string} [data.location] - 活动地点
 * @param {string} data.startTime - 开始时间，格式 yyyy-MM-dd HH:mm:ss
 * @param {number} data.totalTickets - 门票总数，必须 > 0
 * @returns {Promise<{code: number, msg: string, data: {eventId: number, status: string}}>}
 */
export function createEventAPI(data) {
  return request.post('/event', data)
}

/**
 * 获取活动列表
 * @param {Object} params - 查询参数
 * @param {string} [params.scope] - 列表范围：'public' | 'organizer'
 * @param {number} [params.page] - 页码，默认 1
 * @param {number} [params.size] - 每页条数，默认 10
 * @returns {Promise<{code: number, msg: string, data: {records: Array, total: number, page: number, size: number, pages: number}}>}
 */
export function getEventListAPI(params) {
  return request.get('/event/list', { params })
}

/**
 * 获取活动详情
 * @param {number} eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: Object}>}
 */
export function getEventDetailAPI(eventId) {
  return request.get(`/event/${eventId}`)
}

/**
<<<<<<< HEAD
 * 获取活动座位矩阵
=======
 * 获取活动座位矩阵（B 端和 C 端共用）
>>>>>>> 653307e3c379897e2e3bfbaf08725c2d31eda1af
 * @param {number} eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: {hasSeats: boolean, rows: number, cols: number, seats: Array}}>}
 */
export function getEventSeatsAPI(eventId) {
  return request.get(`/event/${eventId}/seats`)
}

/**
 * 更新活动信息（仅 organizer 角色，且必须是发布者）
 * @param {number} eventId - 活动 ID
 * @param {Object} data - 要更新的字段（全部可选）
 * @returns {Promise<{code: number, msg: string, data: null}>}
 */
export function updateEventAPI(eventId, data) {
  return request.put(`/event/${eventId}`, data)
}

/**
 * 上线/下线活动（仅 organizer 角色）
 * @param {number} eventId - 活动 ID
 * @param {string} status - 目标状态：'online' | 'offline'
 * @returns {Promise<{code: number, msg: string, data: null}>}
 */
export function updateEventStatusAPI(eventId, status) {
  return request.patch(`/event/${eventId}/status`, { status })
}

/**
 * 删除活动（仅 organizer 角色，且必须是发布者）
 * @param {number} eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: null}>}
 */
export function deleteEventAPI(eventId) {
  return request.delete(`/event/${eventId}`)
}

/**
 * 【AI 禁飞区】AI 一键生成活动文案
 * @param {number} eventId - 活动 ID
 * @param {string} title - 活动标题，作为 AI 生成上下文
 * @returns {Promise<{code: number, msg: string, data: {description: string}}>}
 */
export function generateAIDescriptionAPI(eventId, title) {
  return request.post(`/event/${eventId}/ai-description`, { title })
}