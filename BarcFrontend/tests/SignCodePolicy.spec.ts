import { describe, expect, it } from 'vitest'
import {
  COUNT_DOWN_SECONDS,
  codeButtonText,
  isCodeLocked,
  nextCountDown,
} from '../src/utils/SignCodePolicy.ts'

/**
 * 注册验证码按钮策略测试
 *
 * 覆盖三类此前出问题的行为：
 * 1、冷却是有限且会真正结束的（原实现到负数就重置回 60，永不结束）；
 * 2、冷却中与请求中都必须锁定按钮（原实现从不 disabled，可被连点）；
 * 3、按钮文案要如实反映当前状态。
 */
describe('SignCodePolicy 验证码按钮策略', () => {
  describe('nextCountDown 倒计时推进', () => {
    it('冷却初始值为 60 秒', () => {
      expect(COUNT_DOWN_SECONDS).toBe(60)
    })

    it('每推进一次减一秒', () => {
      expect(nextCountDown(60)).toBe(59)
      expect(nextCountDown(2)).toBe(1)
    })

    it('推进到 1 秒后归零且不再回弹（原实现会重置回 60）', () => {
      expect(nextCountDown(1)).toBe(0)
      expect(nextCountDown(0)).toBe(0)
      expect(nextCountDown(-3)).toBe(0)
    })

    it('完整走完 60 秒冷却后必然归零', () => {
      let countDown = COUNT_DOWN_SECONDS
      for (let i = 0; i < COUNT_DOWN_SECONDS + 5; i++) {
        countDown = nextCountDown(countDown)
      }
      expect(countDown).toBe(0)
    })
  })

  describe('isCodeLocked 按钮锁定', () => {
    it('空闲状态不锁定', () => {
      expect(isCodeLocked(0, false)).toBe(false)
    })

    it('冷却中锁定', () => {
      expect(isCodeLocked(59, false)).toBe(true)
      expect(isCodeLocked(1, false)).toBe(true)
    })

    it('请求进行中锁定（即使冷却为 0，也要防止并发发码）', () => {
      expect(isCodeLocked(0, true)).toBe(true)
    })

    it('冷却结束且请求完成后恢复可点', () => {
      expect(isCodeLocked(nextCountDown(1), false)).toBe(false)
    })
  })

  describe('codeButtonText 按钮文案', () => {
    it('空闲时显示「获取验证码」', () => {
      expect(codeButtonText(0, false)).toBe('获取验证码')
    })

    it('冷却中显示剩余秒数', () => {
      expect(codeButtonText(60, false)).toBe('60s后再试')
      expect(codeButtonText(1, false)).toBe('1s后再试')
    })

    it('请求中优先显示「发送中...」', () => {
      expect(codeButtonText(0, true)).toBe('发送中...')
      expect(codeButtonText(30, true)).toBe('发送中...')
    })
  })
})
