/**
 * 将 B 端手工输入的票号转换为签到接口需要的 code。
 * 空输入返回 null，调用方据此阻止请求。
 */
export function normalizeManualCheckinCode(value) {
  const normalized = String(value ?? '').trim()
  if (!normalized) return null
  return normalized.startsWith('ticket_') ? normalized : `ticket_${normalized}`
}

/**
 * 相同二维码在指定时间窗内视为重复扫描。
 */
export function shouldIgnoreScannedCode(rawValue, lastCode, lastScannedAt, now, windowMs = 3000) {
  return rawValue === lastCode && now - lastScannedAt < windowMs
}

/**
 * 将签到接口数据规范化为 B 端最近签到记录。
 */
export function normalizeCheckinRecord(data = {}, fallbackCheckedAt) {
  const ticketId = data.ticketId || data.ticketNo || '-'
  const checkedAt = data.checkedAt
    || fallbackCheckedAt
    || new Date().toLocaleString('zh-CN', { hour12: false })

  return {
    recordKey: `${ticketId}-${checkedAt}`,
    eventTitle: data.eventTitle || '未知活动',
    ticketId,
    username: data.username || '未知用户',
    checkedAt
  }
}
