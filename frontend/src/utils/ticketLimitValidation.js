export const MIN_TICKET_LIMIT = 1
export const MAX_TICKET_LIMIT = 100

/**
 * 编辑活动时，门票总数不能低于系统最小值或活动已售票数。
 */
export function getMinimumTicketLimit(soldTickets) {
  const normalizedSoldTickets = Number(soldTickets) || 0
  return Math.max(MIN_TICKET_LIMIT, normalizedSoldTickets)
}

/**
 * 返回门票总数的校验错误；合法时返回 null。
 */
export function getTicketLimitValidationError(value, soldTickets) {
  const minimumTicketLimit = getMinimumTicketLimit(soldTickets)

  if (!Number.isInteger(value) || value < minimumTicketLimit || value > MAX_TICKET_LIMIT) {
    return `门票限额必须是 ${minimumTicketLimit}-${MAX_TICKET_LIMIT} 之间的整数`
  }

  return null
}
