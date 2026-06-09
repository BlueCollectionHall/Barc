import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchCommentCountByWork } from '@/utils/commentApi.ts'
import { baseHttp } from '@/utils/https.ts'

vi.mock('@/utils/https.ts', () => ({
  baseHttp: {
    get: vi.fn(),
  },
}))

describe('fetchCommentCountByWork', () => {
  beforeEach(() => {
    vi.mocked(baseHttp.get).mockReset()
  })

  it('requests the backend work comment count and returns the numeric total', async () => {
    vi.mocked(baseHttp.get).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: 5,
      },
    })

    const result = await fetchCommentCountByWork('work-1')

    expect(baseHttp.get).toHaveBeenCalledWith('/comment/work/count_by_work', {
      params: { work_id: 'work-1' },
    })
    expect(result).toBe(5)
  })

  it('rejects non-zero response code with backend message', async () => {
    vi.mocked(baseHttp.get).mockResolvedValueOnce({
      data: {
        code: 1,
        msg: '作品不存在',
        data: null,
      },
    })

    await expect(fetchCommentCountByWork('missing-work')).rejects.toThrow('作品不存在')
  })

  it('rejects malformed count payload with controlled error', async () => {
    vi.mocked(baseHttp.get).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: '5',
      },
    })

    await expect(fetchCommentCountByWork('work-1')).rejects.toThrow('评论数响应格式异常')
  })
})
