import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get, put, post } = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/shared/api/http', () => ({
  http: { get, put, post },
}))

describe('work complaint manage API', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    post.mockReset()
  })

  it('lists WORK complaints through the generic feedback endpoint', async () => {
    const { getComplaintsList } = await import('@/modules/works/api/workManage')
    get.mockResolvedValueOnce([])

    await getComplaintsList()

    expect(get).toHaveBeenCalledWith('/feedback/page', {
      params: { type: 'WORK', is_manager: true, page: 1, size: 20, status: undefined },
    })
    expect(get).not.toHaveBeenCalledWith('/api/work/manage/complaints')
  })

  it('reviews WORK complaints through the generic feedback update endpoint', async () => {
    const { processComplaint } = await import('@/modules/works/api/workManage')
    put.mockResolvedValueOnce('修改成功')

    await processComplaint('feedback-1', 'COMPLETED', '已处理')

    expect(put).toHaveBeenCalledWith('/feedback/update', {
      id: 'feedback-1',
      type: 'WORK',
      status: 'COMPLETED',
      echo: '已处理',
    })
    expect(post).not.toHaveBeenCalledWith('/api/work/manage/complaint/process', expect.anything())
  })
})
