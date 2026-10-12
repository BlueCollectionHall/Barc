import { describe, it, expect, vi } from 'vitest'
import { errorMessage, infoMessage, successMessage } from '@/utils/MessageAlert'
import { message } from 'ant-design-vue'

vi.mock('ant-design-vue', () => ({ message: { error: vi.fn(), info: vi.fn(), success: vi.fn() } }))

describe('业务提示数据边界', () => {
  it('显示明确的后端文字', () => {
    successMessage('保存成功')
    infoMessage('请刷新后重试')
    expect(message.success).toHaveBeenCalledWith('保存成功')
    expect(message.info).toHaveBeenCalledWith('请刷新后重试')
  })

  it('不把未知响应对象交给组件渲染', () => {
    errorMessage({ internal: 'diagnostic detail' })
    expect(message.error).toHaveBeenCalledWith('操作未完成，请稍后重试')
  })
})
