/**
 * Dashboard 数据大屏模块 API
 * 
 * 对应后端接口文档第六章「Dashboard 数据大屏模块」：
 * - GET /api/v1/dashboard/overview
 * - GET /api/v1/dashboard/event/{eventId}
 * - GET /api/v1/dashboard/arrival-rate/{eventId}（AI 禁飞区）
 * 
 * 组员A 实现，仅 B端使用。
 * 
 * 设计说明：
 * - getDashboardOverviewAPI：获取 Dashboard 总览数据，包含所有活动的汇总指标和图表数据
 * - getDashboardEventDetailAPI：获取单个活动的详细数据指标和图表数据
 *   该接口用于活动数据详情页（EventDataDetailView），
 *   数据详情页数量与活动数量一致，每个活动都有独立的数据详情页
 * - getPredictionAPI：获取单个活动的参会率预测、四项因子与运营建议
 */

import request from './request'

/**
 * 获取 Dashboard 总览数据（仅 organizer 角色）
 * @returns {Promise<{code: number, msg: string, data: {totalEvents: number, draftCount: number, onlineCount: number, offlineCount: number, finishedCount: number, totalTickets: number, soldTickets: number, totalCheckins: number, recentEvents: Array, charts: Object}}>}
 */
export function getDashboardOverviewAPI() {
  return request.get('/dashboard/overview')
}

/**
 * 获取单个活动数据详情（仅 organizer 角色，且必须是发布者）
 * 
 * 用于活动数据详情页（EventDataDetailView），
 * 展示单个活动的详细数据指标、图表和签到明细。
 * 数据详情页数量与活动数量一致，每个活动都有独立的数据详情页。
 * 
 * @param {number} eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: {eventId: number, title: string, startTime: string, location: string, status: string, totalTickets: number, soldTickets: number, checkedIn: number, charts: {weeklyTrend: Object, genderRatio: Object}, checkinDetails: Array}}>}
 */
export function getDashboardEventDetailAPI(eventId) {
  return request.get(`/dashboard/event/${eventId}`)
}

/**
 * 【AI 禁飞区】获取单个活动参会率预测（仅 organizer 角色，且必须是发布者）
 * @param {number} eventId - 活动 ID
 * @returns {Promise<{code: number, msg: string, data: {eventId: number, eventTitle: string, predictedArrivalRate: number, historicalCheckinRate: number, popularity: number, purchaseConcentration: number, timeFactor: number, reason: string, suggestion: string}}>}
 */
export function getPredictionAPI(eventId) {
  return request.get(`/dashboard/arrival-rate/${eventId}`)
}
