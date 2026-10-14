import { describe, expect, it } from 'vitest'
import {
  normalizeCheckinRecord,
  normalizeManualCheckinCode,
  shouldIgnoreScannedCode
} from '../../src/utils/checkinValidation.js'

describe('B端签到核销核心逻辑', () => {
  it('空票号不生成签到 code', () => {
    expect(normalizeManualCheckinCode('')).toBeNull()
  })

  it('全空格票号不生成签到 code', () => {
    expect(normalizeManualCheckinCode('   ')).toBeNull()
  })

  it('普通票号自动添加 ticket_ 前缀', () => {
    expect(normalizeManualCheckinCode('1_15_1789903800'))
      .toBe('ticket_1_15_1789903800')
  })

  it('已有 ticket_ 前缀时不重复添加', () => {
    expect(normalizeManualCheckinCode('ticket_1_15_1789903800'))
      .toBe('ticket_1_15_1789903800')
  })

  it('票号首尾空格会被去除', () => {
    expect(normalizeManualCheckinCode('  ticket_1_15_1789903800  '))
      .toBe('ticket_1_15_1789903800')
  })

  it('相同二维码在3秒内应忽略', () => {
    expect(shouldIgnoreScannedCode('code-A', 'code-A', 1000, 3999)).toBe(true)
  })

  it('相同二维码达到3秒后允许再次识别', () => {
    expect(shouldIgnoreScannedCode('code-A', 'code-A', 1000, 4000)).toBe(false)
  })

  it('不同二维码不应被重复扫描规则拦截', () => {
    expect(shouldIgnoreScannedCode('code-B', 'code-A', 1000, 1500)).toBe(false)
  })

  it('完整签到响应应生成稳定的最近记录', () => {
    expect(normalizeCheckinRecord({
      ticketId: 18,
      ticketNo: '1_15_1789903800',
      eventTitle: '测试活动',
      username: 'alice',
      checkedAt: '2026-07-15 10:00:00'
    })).toEqual({
      recordKey: '18-2026-07-15 10:00:00',
      eventTitle: '测试活动',
      ticketId: 18,
      username: 'alice',
      checkedAt: '2026-07-15 10:00:00'
    })
  })

  it('缺失字段时应使用票号和默认展示值', () => {
    expect(normalizeCheckinRecord(
      { ticketNo: '1_15_1789903800' },
      '2026-07-15 11:00:00'
    )).toEqual({
      recordKey: '1_15_1789903800-2026-07-15 11:00:00',
      eventTitle: '未知活动',
      ticketId: '1_15_1789903800',
      username: '未知用户',
      checkedAt: '2026-07-15 11:00:00'
    })
  })
})
