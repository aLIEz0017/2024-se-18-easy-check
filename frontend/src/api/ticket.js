/**
 * 票务模块 API
 * 
 * 对应后端接口文档第五章「票务模块」：
 * - POST  /api/v1/ticket/purchase
 * - GET   /api/v1/ticket/my-tickets
 * - GET   /api/v1/ticket/{ticketId}
 * - POST  /api/v1/ticket/checkin
 * - GET   /api/v1/ticket/event/{eventId}/checkins
 * 
 * 组员A 最先使用 checkin 接口，负责创建初始版本。
 * 组员C 在 C端票夹页面会大量使用此模块。
 */

import request from './request'

/**
 * 用户抢票（仅 attendee 角色）
 * @param {Object} data
 * @param {number} data.eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: {ticketId: number, ticketNo: string, eventTitle: string, status: string}}>}
 */
export function purchaseTicketAPI(data) {
  return request.post('/ticket/purchase', data)
}

/**
 * 获取我的票夹（仅 attendee 角色）
 * @param {Object} params - 查询参数
 * @param {string} [params.status] - 筛选状态：'pending' | 'checked' | 'invalid' | 'refunded'
 * @param {number} [params.page] - 页码，默认 1
 * @param {number} [params.size] - 每页条数，默认 10
 * @returns {Promise<{code: number, msg: string, data: {records: Array, total: number, page: number, size: number, pages: number}}>}
 */
export function getMyTicketsAPI(params = {}) {
  return request.get('/ticket/my-tickets', { params })
}

/**
 * 获取单张票详情（仅 attendee 角色，且必须是票主）
 * @param {number} ticketId - 门票 ID
 * @returns {Promise<{code: number, msg: string, data: {ticketId: number, ticketNo: string, eventTitle: string, status: string}}>}
 */
export function getTicketDetailAPI(ticketId) {
  return request.get(`/ticket/${ticketId}`)
}

/**
 * 举办方扫码核销签到（仅 organizer 角色）
 * @param {Object} data
 * @param {string} data.code - 扫码解析出的完整文本
 * @returns {Promise<{code: number, msg: string, data: {ticketNo: string, eventTitle: string, checkedAt: string}}>}
 */
export function checkinAPI(data) {
  return request.post('/ticket/checkin', data)
}

/**
 * 获取活动签到明细列表（仅 organizer 角色，且必须是发布者）
 * @param {number} eventId - 活动 ID
 * @param {Object} params - 查询参数
 * @param {string} [params.status] - 筛选状态：'pending' | 'checked' | 'invalid'
 * @param {number} [params.page] - 页码，默认 1
 * @param {number} [params.size] - 每页条数，默认 10
 * @returns {Promise<{code: number, msg: string, data: {records: Array, total: number, page: number, size: number, pages: number}}>}
 */
export function getEventCheckinsAPI(eventId, params) {
  return request.get(`/ticket/event/${eventId}/checkins`, { params })
}
