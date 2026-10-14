/**
 * Dashboard 百分比计算，返回不带百分号的一位小数字符串。
 */
export function calculatePercent(part, total) {
  return Number(total)
    ? ((Number(part || 0) / Number(total)) * 100).toFixed(1)
    : '0.0'
}

/**
 * 活动数据详情百分比展示。
 */
export function formatPercent(part, total) {
  return `${calculatePercent(part, total)}%`
}

/**
 * 规范化饼图数据；接口无数据时使用 fallback。
 */
export function normalizeRatioData(data, fallback = []) {
  const source = Array.isArray(data) && data.length ? data : fallback
  return source.map(item => ({
    name: item.name,
    value: Number(item.value) || 0
  }))
}

/**
 * 将预测因子限制在 0~1，并转换为页面百分比模型。
 */
export function createPredictionFactor(label, weight, value) {
  const normalized = Math.min(1, Math.max(0, Number(value) || 0))
  const percentage = Math.round(normalized * 100)
  return { label, weight, percentage, value: `${percentage}%` }
}

/**
 * B 端座位数据仅展示“空座”和“订座”两类状态。
 */
export function normalizeSeatStatus(status) {
  return ['sold', 'reserved', 'booked'].includes(status) ? 'reserved' : 'available'
}
