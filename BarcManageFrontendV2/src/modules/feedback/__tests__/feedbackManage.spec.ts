import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, put } = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
}))

vi.mock('@/shared/api/http', () => ({
  http: { get, put },
}))

describe('feedback manage API', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
  })

  it.each(['USER', 'BUG', 'SUGGESTION', 'OTHER'] as const)(
    'lists %s feedback through the generic manager endpoint',
    async (type) => {
      const { getFeedbacksByType } = await import('@/modules/feedback/api/feedbackManage')
      get.mockResolvedValueOnce([])

      await getFeedbacksByType(type)

      expect(get).toHaveBeenCalledWith('/feedback/feedbacks_by_type', {
        params: { type, is_manager: true },
      })
    },
  )

  it('updates feedback status and echoes the processing remark', async () => {
    const { updateFeedbackStatus } = await import('@/modules/feedback/api/feedbackManage')
    put.mockResolvedValueOnce('修改成功')

    await updateFeedbackStatus('feedback-1', 'BUG', 'COMPLETED', '已复现并修复')

    expect(put).toHaveBeenCalledWith('/feedback/update', {
      id: 'feedback-1',
      type: 'BUG',
      status: 'COMPLETED',
      echo: '已复现并修复',
    })
  })
})
