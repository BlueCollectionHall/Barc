import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchLikedWorksByUsername } from '@/utils/accountLikeApi.ts'
import { baseHttp } from '@/utils/https.ts'
import type { PageRequestImpl, PageResultImpl } from '@/interfaces/PageImpl.ts'
import type { WorkImpl } from '@/interfaces/WorkImpl.ts'

vi.mock('@/utils/https.ts', () => ({
  baseHttp: {
    post: vi.fn(),
  },
}))

const pageRequest: PageRequestImpl = {
  page_num: 2,
  page_size: 12,
}

const likedWork: WorkImpl = {
  id: 'work-1',
  title: 'Liked work',
  description: 'A public liked work',
  content: null,
  banner_image: 'banner.webp',
  cover_image: 'cover.webp',
  view_count: 32,
  like_count: 7,
  liked_by_current_user: true,
  author: 'author-uuid',
  author_nickname: 'Author',
  uploader: null,
  is_claim: false,
  status: 'PUBLIC',
  student: 'student-uuid',
  created_at: new Date('2026-06-01T00:00:00Z'),
  updated_at: new Date('2026-06-02T00:00:00Z'),
}

const likedWorksPage: PageResultImpl<WorkImpl> = {
  page_num: 2,
  page_size: 12,
  total: 18,
  total_page: 2,
  list: [likedWork],
}

describe('fetchLikedWorksByUsername', () => {
  beforeEach(() => {
    vi.mocked(baseHttp.post).mockReset()
  })

  it('requests liked works page by username with PageRequest payload', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: likedWorksPage,
      },
    })

    const result = await fetchLikedWorksByUsername('alice name', pageRequest)

    expect(baseHttp.post).toHaveBeenCalledWith(
      '/api/work/like/list_by_username?username=alice%20name',
      pageRequest,
    )
    expect(result).toEqual(likedWorksPage)
  })

  it('rejects non-zero response code with backend message', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 1,
        msg: '用户不存在',
        data: null,
      },
    })

    await expect(fetchLikedWorksByUsername('missing-user', pageRequest)).rejects.toThrow('用户不存在')
  })

  it('rejects malformed page payload with controlled error', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: {
          page_num: 1,
          page_size: 12,
          total: 1,
          total_page: 1,
        },
      },
    })

    await expect(fetchLikedWorksByUsername('alice', pageRequest)).rejects.toThrow('喜欢作品分页响应格式异常')
  })

  it('rejects page payload when list contains malformed work items', async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: {
          ...likedWorksPage,
          list: [null],
        },
      },
    })

    await expect(fetchLikedWorksByUsername('alice', pageRequest)).rejects.toThrow('喜欢作品分页响应格式异常')
  })
})
