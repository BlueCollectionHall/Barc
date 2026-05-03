import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import NoticesListView from '@/modules/notices/views/NoticesListView.vue'
import { MANAGER_PERMISSION } from '@/shared/constants/permissions'
import type { PageResult } from '@/shared/types/api'
import type { NoticeRecord } from '@/shared/types/notice'

const {
  routeState,
  push,
  replace,
  confirm,
  fetchNoticesByPage,
  deleteNotice,
  showError,
  showSuccess,
} = vi.hoisted(() => ({
  routeState: {
    query: {} as Record<string, unknown>,
  },
  push: vi.fn(),
  replace: vi.fn(),
  confirm: vi.fn(),
  fetchNoticesByPage: vi.fn(),
  deleteNotice: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push,
    replace,
  }),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessageBox: {
      confirm,
    },
  }
})

vi.mock('@/app/stores/auth', () => ({
  useAuthStore: () => ({
    userArchive: {
      uuid: 'manager-1',
      nickname: 'Manager One',
      avatar: null,
      gender: null,
      birthday: null,
      age: 28,
      permission: MANAGER_PERMISSION.ADMINISTRATOR,
      identity: 'MANAGER',
      updated_at: null,
    },
  }),
}))

vi.mock('@/modules/notices/api/notices.service', () => ({
  deleteNotice,
  fetchNoticesByPage,
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showInfo: vi.fn(),
  showSuccess,
  showWarning: vi.fn(),
}))

function createPageResult(list: NoticeRecord[], pageNum = 1): PageResult<NoticeRecord> {
  return {
    total: list.length,
    list,
    page_num: pageNum,
    page_size: 8,
    total_page: 1,
  }
}

function createNotice(id: string, title = 'Phase 1 Notice'): NoticeRecord {
  return {
    id,
    title,
    content: '<p>Phase 1 content</p>',
    author: 'manager-1',
    created_at: '2026-04-18T10:00:00Z',
    updated_at: '2026-04-18T10:30:00Z',
  }
}

function findButtonByText(wrapper: ReturnType<typeof mount>, label: string, index = 0) {
  const matches = wrapper.findAll('button').filter((candidate) => candidate.text() === label)
  const button = matches[index]

  if (!button) {
    throw new Error(`Unable to find button: ${label}#${index}`)
  }

  return button
}

describe('NoticesListView', () => {
  beforeEach(() => {
    routeState.query = {}
    fetchNoticesByPage.mockResolvedValue(createPageResult([createNotice('notice-1')]))
    deleteNotice.mockResolvedValue('公告删除成功')
    confirm.mockResolvedValue(undefined)
  })

  it('uses current page query for list loading and preserves it in new/edit navigation', async () => {
    routeState.query = { page: '3' }

    const wrapper = mount(NoticesListView)

    await flushPromises()

    expect(fetchNoticesByPage).toHaveBeenCalledWith({
      page_num: 3,
      page_size: 8,
      params: {},
    })
    expect(wrapper.text()).toContain('作者：我')

    await findButtonByText(wrapper, '发布新公告').trigger('click')
    await findButtonByText(wrapper, '编辑').trigger('click')

    expect(push).toHaveBeenNthCalledWith(1, {
      name: 'notices-new',
      query: { page: '3' },
    })
    expect(push).toHaveBeenNthCalledWith(2, {
      name: 'notices-edit',
      params: { noticeId: 'notice-1' },
      query: { page: '3' },
    })
  })

  it('consumes one-shot route feedback and clears it from the URL', async () => {
    routeState.query = {
      page: '2',
      noticeAction: 'created',
    }

    const wrapper = mount(NoticesListView)

    await flushPromises()

    expect(wrapper.text()).toContain('公告已发布')
    expect(wrapper.text()).toContain('列表已回到当前页上下文')
    expect(replace).toHaveBeenCalledWith({ query: { page: '2' } })
  })

  it('reloads the previous page after deleting the last item on a later page', async () => {
    routeState.query = { page: '2' }
    fetchNoticesByPage.mockResolvedValueOnce(createPageResult([createNotice('notice-1')], 2))

    const wrapper = mount(NoticesListView)

    await flushPromises()
    await findButtonByText(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith('删除后将直接影响线上公告展示，是否继续？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    expect(deleteNotice).toHaveBeenCalledWith('notice-1')
    expect(replace).toHaveBeenLastCalledWith({ query: { page: '1' } })
    expect(fetchNoticesByPage).toHaveBeenLastCalledWith({
      page_num: 1,
      page_size: 8,
      params: {},
    })
    expect(showSuccess).toHaveBeenCalledWith('公告删除成功')
  })
})
