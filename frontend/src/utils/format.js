/**
 * 工具函数 - 格式化
 * 
 * 提供项目中常用的格式化函数。
 * 组员A 和 组员C 共同维护此文件。
 */

/**
 * 格式化日期时间为字符串
 * @param {string|Date} date - 日期对象或日期字符串
 * @param {string} format - 格式模板，默认 'yyyy-MM-dd HH:mm:ss'
 * @returns {string} 格式化后的日期字符串
 */
export function formatDateTime(date, format = 'yyyy-MM-dd HH:mm:ss') {
  if (!date) return ''
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const map = {
    'yyyy': d.getFullYear(),
    'MM': String(d.getMonth() + 1).padStart(2, '0'),
    'dd': String(d.getDate()).padStart(2, '0'),
    'HH': String(d.getHours()).padStart(2, '0'),
    'mm': String(d.getMinutes()).padStart(2, '0'),
    'ss': String(d.getSeconds()).padStart(2, '0')
  }

  let result = format
  for (const [key, value] of Object.entries(map)) {
    result = result.replace(key, value)
  }
  return result
}

/**
 * 格式化日期为短格式（用于卡片展示）
 * @param {string|Date} date
 * @returns {string} 如 "09-15"
 */
export function formatShortDate(date) {
  return formatDateTime(date, 'MM-dd')
}

/**
 * 格式化数字为千分位
 * @param {number} num
 * @returns {string}
 */
export function formatNumber(num) {
  if (num === null || num === undefined) return '0'
  return num.toLocaleString('zh-CN')
}

/**
 * 格式化售票进度百分比
 * @param {number} sold - 已售数量
 * @param {number} total - 总数
 * @returns {string} 如 "60%"
 */
export function formatProgress(sold, total) {
  if (!total || total === 0) return '0%'
  return Math.round((sold / total) * 100) + '%'
}

/**
 * 获取活动状态的显示文本
 * @param {string} status - 活动状态枚举值
 * @returns {string} 中文显示文本
 */
export function getEventStatusText(status) {
  const map = {
    'draft': '草稿',
    'online': '已上线',
    'offline': '已下线'
  }
  return map[status] || status
}

/**
 * 获取门票状态的显示文本
 * @param {string} status - 门票状态枚举值
 * @returns {string} 中文显示文本
 */
export function getTicketStatusText(status) {
  const map = {
    'pending': '待签到',
    'checked': '已签到',
    'invalid': '已失效',
    'refunded': '已退票'
  }
  return map[status] || status
}
