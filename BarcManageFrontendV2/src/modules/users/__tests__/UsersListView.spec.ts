import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import UsersListView from '@/modules/users/views/UsersListView.vue'
import type { PageResult } from '@/shared/types/api'
import type { UserListItem } from '@/shared/types/user'

const { queryUsersByPage, ensureCatalog, optionsForIdentity, labelFor, showError } = vi.hoisted(() => ({
  queryUsersByPage: vi.fn(),
  ensureCatalog: vi.fn(),
  optionsForIdentity: vi.fn(),
  labelFor: vi.fn(),
  showError: vi.fn(),
}))

vi.mock('@/modules/users/api/users.service', () => ({
  queryUsersByPage,
}))

vi.mock('@/app/stores/permissions', () => ({
  usePermissionStore: () => ({
    ensureCatalog,
    optionsForIdentity,
    labelFor,
  }),
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showInfo: vi.fn(),
  showSuccess: vi.fn(),
  showWarning: vi.fn(),
}))

vi.mock('@/modules/users/components/UserQuickCreateDrawer.vue', () => ({
  default: defineComponent({
    name: 'UserQuickCreateDrawer',
    props: {
      loading: {
        type: Boolean,
        default: false,
      },
      modelValue: {
        type: Boolean,
        default: false,
      },
    },
    emits: ['submitted', 'update:loading', 'update:modelValue'],
    setup() {
      return () => h('div', { 'data-testid': 'user-quick-create-drawer-stub' })
    },
  }),
}))

function createPageResult(list: UserListItem[]): PageResult<UserListItem> {
  return {
    total: list.length,
    list,
    page_num: 1,
    page_size: 12,
    total_page: 1,
  }
}

function createUserListItem(overrides: Partial<UserListItem> = {}): UserListItem {
  return {
    uuid: 'user-1',
    username: 'alice',
    nickname: 'Alice',
    avatar: null,
    age: 18,
    birthday: null,
    gender: null,
    identity: 'USER',
    permission: 1,
    ...overrides,
  }
}

function findButtonByText(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === label)

  if (!button) {
    throw new Error(`Unable to find button: ${label}`)
  }

  return button
}

function findInputByIndex(wrapper: ReturnType<typeof mount>, index: number) {
  const input = wrapper.findAllComponents({ name: 'ElInput' })[index]

  if (!input) {
    throw new Error(`Unable to find input at index ${index}`)
  }

  return input
}

describe('UsersListView', () => {
  beforeEach(() => {
    ensureCatalog.mockResolvedValue(undefined)
    optionsForIdentity.mockImplementation((identity) => {
      if (identity === 'MANAGER') {
        return [{ value: 16, label: '副馆长' }]
      }

      return [{ value: 1, label: '普通用户' }]
    })
    labelFor.mockImplementation((_identity, permission) => `权限 ${permission ?? '未设置'}`)
    queryUsersByPage.mockResolvedValue(
      createPageResult([
        createUserListItem(),
      ]),
    )
  })

  it('resets to the first page when submitting refreshed filters', async () => {
    const wrapper = mount(UsersListView)

    await flushPromises()

    expect(ensureCatalog).toHaveBeenCalledTimes(1)
    expect(queryUsersByPage).toHaveBeenNthCalledWith(1, {
      page_num: 1,
      page_size: 12,
      params: {
        identity: 'USER',
      },
    })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 3)
    await flushPromises()

    await findInputByIndex(wrapper, 0).find('input').setValue('  alice  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(queryUsersByPage).toHaveBeenLastCalledWith({
      page_num: 1,
      page_size: 12,
      params: {
        username: 'alice',
        identity: 'USER',
      },
    })
  })

  it('resets filters back to the first page', async () => {
    const wrapper = mount(UsersListView)

    await flushPromises()

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 4)
    await flushPromises()

    await findInputByIndex(wrapper, 0).find('input').setValue('  alice  ')
    await findButtonByText(wrapper, '重置').trigger('click')
    await flushPromises()

    expect(queryUsersByPage).toHaveBeenLastCalledWith({
      page_num: 1,
      page_size: 12,
      params: {
        identity: 'USER',
      },
    })
  })

  it('shows an explicit empty state inside the list area when no users are returned', async () => {
    queryUsersByPage.mockResolvedValueOnce(createPageResult([]))

    const wrapper = mount(UsersListView)

    await flushPromises()

    expect(wrapper.text()).toContain('暂时没有用户记录')
    expect(wrapper.text()).toContain('当新的用户进入系统后，这里会继续按当前视图展示。')
  })

  it('shows a success feedback when the created user becomes visible on the current page after reload', async () => {
    queryUsersByPage.mockReset()
    queryUsersByPage
      .mockResolvedValueOnce(createPageResult([createUserListItem()]))
      .mockResolvedValueOnce(
        createPageResult([
          createUserListItem(),
          createUserListItem({
            uuid: 'user-2',
            username: 'new-user',
            nickname: 'New User',
          }),
        ]),
      )

    const wrapper = mount(UsersListView)

    await flushPromises()

    wrapper.findComponent({ name: 'UserQuickCreateDrawer' }).vm.$emit('submitted', {
      username: 'new-user',
      email_verified: true,
      safe_level: 0,
      telephone_present: true,
    })
    await flushPromises()

    expect(wrapper.text()).toContain('当前页已显示')
    expect(wrapper.text()).toContain('这个新用户已经出现在当前页')
    expect(wrapper.text()).not.toContain('邮箱仍处于待验证状态')
  })

  it('keeps the current filters after quick create and shows filter-aware follow-up hints', async () => {
    const wrapper = mount(UsersListView)

    await flushPromises()

    await findInputByIndex(wrapper, 0).find('input').setValue('  alice  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    wrapper.findComponent({ name: 'UserQuickCreateDrawer' }).vm.$emit('submitted', {
      username: 'new-user',
      email_verified: false,
      safe_level: 2,
      telephone_present: false,
    })
    await flushPromises()

    expect(queryUsersByPage).toHaveBeenLastCalledWith({
      page_num: 1,
      page_size: 12,
      params: {
        username: 'alice',
        identity: 'USER',
      },
    })
    expect(wrapper.text()).toContain('已创建用户 new-user')
    expect(wrapper.text()).toContain('被当前筛选隐藏')
    expect(wrapper.text()).toContain('当前筛选条件仍在生效')
    expect(wrapper.text()).toContain('邮箱仍处于待验证状态')
    expect(wrapper.text()).toContain('当前没有登记电话')
    expect(wrapper.text()).toContain('安全等级已设为 2')
    expect(wrapper.text()).not.toContain('暂时没有用户记录')
  })

  it('explains when the operator remains on a later page after quick create', async () => {
    const wrapper = mount(UsersListView)

    await flushPromises()

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 3)
    await flushPromises()

    wrapper.findComponent({ name: 'UserQuickCreateDrawer' }).vm.$emit('submitted', {
      username: 'new-user',
      email_verified: true,
      safe_level: 0,
      telephone_present: true,
    })
    await flushPromises()

    expect(queryUsersByPage).toHaveBeenLastCalledWith({
      page_num: 3,
      page_size: 12,
      params: {
        identity: 'USER',
      },
    })
    expect(wrapper.text()).toContain('当前停留在后续页')
    expect(wrapper.text()).toContain('可以翻回第一页继续确认')
  })

  it('warns when the list reload fails after quick create', async () => {
    queryUsersByPage.mockReset()
    queryUsersByPage
      .mockResolvedValueOnce(createPageResult([createUserListItem()]))
      .mockRejectedValueOnce(new Error('列表刷新失败'))

    const wrapper = mount(UsersListView)

    await flushPromises()

    wrapper.findComponent({ name: 'UserQuickCreateDrawer' }).vm.$emit('submitted', {
      username: 'new-user',
      email_verified: false,
      safe_level: 4,
      telephone_present: false,
    })
    await flushPromises()

    expect(wrapper.text()).toContain('列表刷新失败')
    expect(wrapper.text()).toContain('当前列表刷新失败了')
    expect(wrapper.text()).toContain('邮箱仍处于待验证状态')
    expect(wrapper.text()).toContain('安全等级已设为 4')
    expect(showError).toHaveBeenCalled()
  })
})
