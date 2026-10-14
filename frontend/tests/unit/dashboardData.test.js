import { describe, expect, it } from 'vitest'
import {
  calculatePercent,
  createPredictionFactor,
  formatPercent,
  normalizeRatioData,
  normalizeSeatStatus
} from '../../src/utils/dashboardData.js'

describe('B端 Dashboard 与活动数据详情核心逻辑', () => {
  it('50/100 应计算为 50.0', () => {
    expect(calculatePercent(50, 100)).toBe('50.0')
  })

  it('总数为0时应返回 0.0', () => {
    expect(calculatePercent(10, 0)).toBe('0.0')
  })

  it('数字字符串应正常参与比例计算', () => {
    expect(calculatePercent('1', '4')).toBe('25.0')
  })

  it('部分值为空时按0计算', () => {
    expect(calculatePercent(null, 10)).toBe('0.0')
  })

  it('详情百分比应带百分号', () => {
    expect(formatPercent(3, 4)).toBe('75.0%')
  })

  it('接口饼图数据中的数字字符串应转换为数值', () => {
    expect(normalizeRatioData([{ name: '已售票', value: '12' }]))
      .toEqual([{ name: '已售票', value: 12 }])
  })

  it('空接口数据应使用 fallback', () => {
    expect(normalizeRatioData([], [{ name: '未售票', value: 8 }]))
      .toEqual([{ name: '未售票', value: 8 }])
  })

  it('非法图表数值应归零', () => {
    expect(normalizeRatioData([{ name: '异常', value: 'abc' }]))
      .toEqual([{ name: '异常', value: 0 }])
  })

  it('0.75预测因子应转换为75%', () => {
    expect(createPredictionFactor('历史签到率', '50%', 0.75)).toEqual({
      label: '历史签到率', weight: '50%', percentage: 75, value: '75%'
    })
  })

  it('超过1的预测因子应限制为100%', () => {
    expect(createPredictionFactor('当前售票率', '20%', 1.5).percentage).toBe(100)
  })

  it('负预测因子应限制为0%', () => {
    expect(createPredictionFactor('时段因子', '10%', -0.2).percentage).toBe(0)
  })

  it('sold状态应映射为订座', () => {
    expect(normalizeSeatStatus('sold')).toBe('reserved')
  })

  it('booked状态应兼容映射为订座', () => {
    expect(normalizeSeatStatus('booked')).toBe('reserved')
  })

  it('未知状态应安全降级为空座', () => {
    expect(normalizeSeatStatus('unknown')).toBe('available')
  })
})
