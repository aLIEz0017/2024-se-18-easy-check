import { describe, expect, it } from 'vitest'

import {
  getMinimumTicketLimit,
  getTicketLimitValidationError
} from '../../src/utils/ticketLimitValidation.js'

describe('B2：活动编辑门票数量边界', () => {
  it('总票数 1 低于已售 2 张，应拒绝', () => {
    expect(getTicketLimitValidationError(1, 2))
      .toBe('门票限额必须是 2-100 之间的整数')
  })

  it('总票数 2 恰好等于已售数量，应通过', () => {
    expect(getTicketLimitValidationError(2, 2)).toBeNull()
  })

  it('总票数 101 超过前端上限 100，应拒绝', () => {
    expect(getTicketLimitValidationError(101, 2))
      .toBe('门票限额必须是 2-100 之间的整数')
  })

  it('总票数为小数时应拒绝', () => {
    expect(getTicketLimitValidationError(2.5, 2))
      .toBe('门票限额必须是 2-100 之间的整数')
  })

  it('尚未售票时动态下限应为1', () => {
    expect(getMinimumTicketLimit(0)).toBe(1)
  })

  it('已售数量为数字字符串时应正确计算动态下限', () => {
    expect(getMinimumTicketLimit('5')).toBe(5)
  })
})
