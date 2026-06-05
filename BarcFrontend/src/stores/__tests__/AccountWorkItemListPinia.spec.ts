import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAccountWorkItemPinia } from '@/stores/AccountWorkItemListPinia.ts'
import { baseHttp } from '@/utils/https.ts'
import { fetchLikedWorksByUsername } from '@/utils/accountLikeApi.ts'
import type { PageResultImpl } from '@/interfaces/PageImpl.ts'
import type { WorkImpl } from '@/interfaces/WorkImpl.ts'

const routeState = vi.hoisted(() => ({
  route: {
    query: {} as Record<string, string>,
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState.route,
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
}))

vi.mock('@/utils/https.ts', () => ({
  baseHttp: {
    post: vi.fn(),
  },
}))

vi.mock('@/utils/accountLikeApi.ts', () => ({
  fetchLikedWorksByUsername: vi.fn(),
}))

vi.mock('@/utils/MessageAlert.ts', () => ({
  errorMessage: vi.fn(),
  infoMessage: vi.fn(),
}))

const work: WorkImpl = {
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

const page: PageResultImpl<WorkImpl> = {
  page_num: 3,
  page_size: 15,
  total: 1,
  total_page: 1,
  list: [work],
}

describe('useAccountWorkItemPinia', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeState.route.query = {}
    vi.mocked(baseHttp.post).mockReset()
    vi.mocked(fetchLikedWorksByUsername).mockReset()
  })

  it('fetches liked works with the account like helper without falling through to works_by_page', async () => {
    routeState.route.query = { type: 'likes', page_num: '3' }
    vi.mocked(fetchLikedWorksByUsername).mockResolvedValueOnce(page)

    const store = useAccountWorkItemPinia()

    await store.fetchWorks('alice')

    expect(fetchLikedWorksByUsername).toHaveBeenCalledWith('alice', {
      page_num: 3,
      page_size: 15,
    })
    expect(baseHttp.post).not.toHaveBeenCalled()
    expect(store.pageResult).toEqual(page)
  })

  it('keeps works requests on the shared works_by_page endpoint', async () => {
    routeState.route.query = { type: 'works', page_num: '2' }
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {
        code: 0,
        msg: 'ok',
        data: page,
      },
    })

    const store = useAccountWorkItemPinia()

    await store.fetchWorks('alice')

    expect(baseHttp.post).toHaveBeenCalledWith(
      '/api/work/works_by_page',
      {
        page_num: 2,
        page_size: 15,
        params: { author_username: 'alice' },
      },
      { params: { status: 'PUBLIC' } },
    )
    expect(fetchLikedWorksByUsername).not.toHaveBeenCalled()
    expect(store.pageResult).toEqual(page)
  })
})
