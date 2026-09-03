import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getWorkReviewList, reviewWork } from '../api/workManage'
import { http } from '@/shared/api/http'
import { adminChildren } from '@/app/router/routes'
import { MANAGER_PERMISSION } from '@/shared/constants/permissions'

vi.mock('@/shared/api/http', () => ({
  http: {
    post: vi.fn(),
  },
}))

describe('work upload review management', () => {
  beforeEach(() => {
    vi.mocked(http.post).mockReset()
  })

  it('loads the selected review queue with paging filters', async () => {
    vi.mocked(http.post).mockResolvedValueOnce({
      total: 0,
      list: [],
      page_num: 1,
      page_size: 10,
      total_page: 1,
    })

    await getWorkReviewList({
      page_num: 1,
      page_size: 10,
      params: { keyword: '白子' },
    }, 'PENDING')

    expect(http.post).toHaveBeenCalledWith(
      '/api/work/manage/reviews',
      {
        page_num: 1,
        page_size: 10,
        params: { keyword: '白子' },
      },
      { params: { review_status: 'PENDING' } },
    )
  })

  it('submits silent reject reasons through the review endpoint', async () => {
    vi.mocked(http.post).mockResolvedValueOnce('修改成功')

    await reviewWork('work-1', false, '封面不符合要求')

    expect(http.post).toHaveBeenCalledWith('/api/work/manage/review', {
      work_id: 'work-1',
      approved: false,
      reason: '封面不符合要求',
    })
  })

  it('gates the upload review route by the exact work permission bit', () => {
    const route = adminChildren.find((item) => item.name === 'works-reviews')

    expect(route?.meta?.minManagerPermissionBit).toBe(MANAGER_PERMISSION.SEC_MAINTAINER)
    expect(route?.meta?.minManagerPermission).toBeUndefined()
  })
})
