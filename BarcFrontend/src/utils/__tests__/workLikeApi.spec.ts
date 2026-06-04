import { beforeEach, describe, expect, it, vi } from 'vitest'

import { toggleWorkLike } from '@/utils/workLikeApi.ts'
import { baseHttp } from '@/utils/https.ts'

vi.mock('@/utils/https.ts', () => ({
  baseHttp: {
    post: vi.fn(),
  },
}))

describe('toggleWorkLike', () => {
  beforeEach(() => {
    vi.mocked(baseHttp.post).mockReset()
  })

  it('sends raw JWT authorization and returns backend like state', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: {
          liked: true,
          like_count: 8,
        },
      },
    })

    const result = await toggleWorkLike('work-1', 'raw.jwt.token')

    expect(baseHttp.post).toHaveBeenCalledWith(
      '/api/work/like/toggle',
      { work_id: 'work-1' },
      { headers: { Authorization: 'raw.jwt.token' } },
    )
    expect(result).toEqual({ liked: true, like_count: 8 })
  })

  it('rejects with backend message when response code is non-zero', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 1,
        msg: '作品不存在',
        data: null,
      },
    })

    await expect(toggleWorkLike('missing-work', 'raw.jwt.token')).rejects.toThrow('作品不存在')
  })

  it('rejects malformed success payload with controlled error', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: {
          liked: 'true',
          like_count: 8,
        },
      },
    })

    await expect(toggleWorkLike('work-1', 'raw.jwt.token')).rejects.toThrow('点赞响应格式异常')
  })
})
