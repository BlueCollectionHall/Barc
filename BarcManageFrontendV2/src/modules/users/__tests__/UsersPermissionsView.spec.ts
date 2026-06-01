import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import UsersPermissionsView from '@/modules/users/views/UsersPermissionsView.vue'
import type { PageResult } from '@/shared/types/api'
import type { UserListItem } from '@/shared/types/user'

const {
  queryUsersByPage,
  changePermission,
  ensureCatalog,
  bootstrapManagerContext,
  optionsForIdentity,
  labelFor,
  messageBoxConfirm,
  showError,
  showSuccess,
} = vi.hoisted(() => ({
  queryUsersByPage: vi.fn(),
  changePermission: vi.fn(),
  ensureCatalog: vi.fn(),
  bootstrapManagerContext: vi.fn(),
  optionsForIdentity: vi.fn(),
  labelFor: vi.fn(),
  messageBoxConfirm: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessageBox: {
      ...actual.ElMessageBox,
      confirm: messageBoxConfirm,
    },
  }
})

vi.mock('@/modules/users/api/users.service', () => ({
  changePermission,
  queryUsersByPage,
}))

vi.mock('@/app/stores/permissions', () => ({
  usePermissionStore: () => ({
    ensureCatalog,
    bootstrapManagerContext,
    optionsForIdentity,
    labelFor,
    identities: [
      { value: 'USER', label: '用户' },
      { value: 'MANAGER', label: '管理者' },
    ],
    myPermissionNearMax: {
      value: 16,
      label: '副馆长',
    },
  }),
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showInfo: vi.fn(),
  showSuccess,
  showWarning: vi.fn(),
}))

function createPageResult(list: UserListItem[]): PageResult<UserListItem> {
  return {
    total: list.length,
    list,
    page_num: 1,
    page_size: 10,
    total_page: 1,
  }
}

function createManagerListItem(overrides: Partial<UserListItem> = {}): UserListItem {
  return {
    uuid: 'manager-1',
    username: 'ops-admin',
    nickname: 'Ops Admin',
    avatar: null,
    age: 24,
    birthday: null,
    gender: null,
    identity: 'MANAGER',
    permission: 16,
    safe_level: null,
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

function findFormByIndex(wrapper: ReturnType<typeof mount>, index: number) {
  const form = wrapper.findAll('form')[index]

  if (!form) {
    throw new Error(`Unable to find form at index ${index}`)
  }

  return form
}

function findSelectByIndex(wrapper: ReturnType<typeof mount>, index: number) {
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[index]

  if (!select) {
    throw new Error(`Unable to find select at index ${index}`)
  }

  return select
}

function findCheckboxInputs(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('input[type="checkbox"]')
}

describe('UsersPermissionsView', () => {
  beforeEach(() => {
    ensureCatalog.mockResolvedValue(undefined)
    bootstrapManagerContext.mockResolvedValue(undefined)
    changePermission.mockResolvedValue('权限已同步')
    messageBoxConfirm.mockResolvedValue('confirm')
    optionsForIdentity.mockImplementation((identity) => {
      if (identity === 'USER') {
        return [{ value: 1, label: '普通用户' }]
      }

      return [
        { value: 8, label: '三级管理员' },
        { value: 16, label: '副馆长' },
        { value: 32, label: '馆长' },
      ]
    })
    labelFor.mockImplementation((identity, permission) => {
      if (identity === 'USER' && permission === 1) {
        return '普通用户'
      }

      if (identity === 'MANAGER' && permission === 8) {
        return '三级管理员'
      }

      if (identity === 'MANAGER' && permission === 16) {
        return '副馆长'
      }

      if (identity === 'MANAGER' && permission === 24) {
        return '副馆长'
      }

      if (identity === 'MANAGER' && permission === 32) {
        return '馆长'
      }

      return `权限 ${permission ?? '未设置'}`
    })
    queryUsersByPage.mockResolvedValue(
      createPageResult([
        createManagerListItem(),
      ]),
    )
  })

  it('resets to the first page when the filters are resubmitted', async () => {
    const wrapper = mount(UsersPermissionsView)

    await flushPromises()

    expect(ensureCatalog).toHaveBeenCalledTimes(1)
    expect(bootstrapManagerContext).toHaveBeenCalledTimes(1)
    expect(queryUsersByPage).toHaveBeenNthCalledWith(1, {
      page_num: 1,
      page_size: 10,
      params: {
        identity: 'MANAGER',
      },
    })

    wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 5)
    await flushPromises()

    await findInputByIndex(wrapper, 1).find('input').setValue('  Ops Admin  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(queryUsersByPage).toHaveBeenLastCalledWith({
      page_num: 1,
      page_size: 10,
      params: {
        nickname: 'Ops Admin',
        identity: 'MANAGER',
      },
    })
  })

  it('shows an explicit empty state inside the table area when no managers are returned', async () => {
    queryUsersByPage.mockResolvedValueOnce(createPageResult([]))

    const wrapper = mount(UsersPermissionsView)

    await flushPromises()

    expect(wrapper.text()).toContain('暂时没有可显示的管理账号')
    expect(wrapper.text()).toContain('这里会继续按当前视图展示')
  })

  it('disables submit until the dialog changes and hides options above the operator ceiling', async () => {
    const wrapper = mount(UsersPermissionsView)

    await flushPromises()
    await findButtonByText(wrapper, '修改').trigger('click')
    await flushPromises()

    expect(findCheckboxInputs(wrapper)).toHaveLength(2)

    const submitButton = findButtonByText(wrapper, '确认修改')
    expect((submitButton.element as HTMLButtonElement).disabled).toBe(true)

    const checkboxInputs = findCheckboxInputs(wrapper)
    const firstCheckbox = checkboxInputs[0]
    expect(firstCheckbox).toBeDefined()
    if (!firstCheckbox) {
      throw new Error('Expected the first permission checkbox to exist')
    }
    await firstCheckbox.setValue(true)
    await flushPromises()

    expect((findButtonByText(wrapper, '确认修改').element as HTMLButtonElement).disabled).toBe(false)
  })

  it('shows an explicit before and after diff for identity and permission changes', async () => {
    const wrapper = mount(UsersPermissionsView)

    await flushPromises()
    await findButtonByText(wrapper, '修改').trigger('click')
    await flushPromises()

    expect(wrapper.find('.dialog-diff').text()).toContain('副馆长')

    const checkboxInputs = findCheckboxInputs(wrapper)
    const firstCheckbox = checkboxInputs[0]
    expect(firstCheckbox).toBeDefined()
    if (!firstCheckbox) {
      throw new Error('Expected the first permission checkbox to exist')
    }
    await firstCheckbox.setValue(true)
    await flushPromises()

    expect(wrapper.find('.dialog-diff').text()).toContain('权限上调')
    expect(wrapper.find('.dialog-diff').text()).toContain('副馆长')

    findSelectByIndex(wrapper, 2).vm.$emit('update:modelValue', 'USER')
    await flushPromises()

    expect(wrapper.find('.dialog-diff').text()).toContain('身份与权限联动调整')
    expect(wrapper.find('.dialog-diff').text()).toContain('管理者')
    expect(wrapper.find('.dialog-diff').text()).toContain('用户')
    expect(wrapper.find('.dialog-diff').text()).toContain('普通用户')
  })

  it('submits the dialog payload and reloads manager context', async () => {
    const wrapper = mount(UsersPermissionsView)

    await flushPromises()
    await findButtonByText(wrapper, '修改').trigger('click')
    await flushPromises()

    const checkboxInputs = findCheckboxInputs(wrapper)
    const firstCheckbox = checkboxInputs[0]
    expect(firstCheckbox).toBeDefined()
    if (!firstCheckbox) {
      throw new Error('Expected the first permission checkbox to exist')
    }
    await firstCheckbox.setValue(true)
    await flushPromises()

    await findFormByIndex(wrapper, 1).trigger('submit')
    await flushPromises()

    expect(changePermission).toHaveBeenCalledWith({
      uuid: 'manager-1',
      identity: 'MANAGER',
      permission: 24,
    })
    expect(showSuccess).toHaveBeenCalledWith('权限已同步')
    expect(bootstrapManagerContext).toHaveBeenCalledTimes(2)
    expect(queryUsersByPage).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.applied-feedback').text()).toContain('已同步 Ops Admin #ops-admin 的权限调整')
    expect(wrapper.find('.applied-feedback').text()).toContain('权限上调')
    expect(wrapper.find('.applied-feedback').text()).toContain('副馆长')
  })

  it('asks for confirmation before closing a dirty permission dialog', async () => {
    messageBoxConfirm.mockRejectedValueOnce('cancel')

    const wrapper = mount(UsersPermissionsView)

    await flushPromises()
    await findButtonByText(wrapper, '修改').trigger('click')
    await flushPromises()

    const checkboxInputs = findCheckboxInputs(wrapper)
    const firstCheckbox = checkboxInputs[0]
    expect(firstCheckbox).toBeDefined()
    if (!firstCheckbox) {
      throw new Error('Expected the first permission checkbox to exist')
    }
    await firstCheckbox.setValue(true)
    await flushPromises()

    await findButtonByText(wrapper, '取消').trigger('click')
    await flushPromises()

    expect(messageBoxConfirm).toHaveBeenCalledWith(
      expect.stringContaining('尚未保存'),
      '放弃权限修改',
      expect.any(Object),
    )
  })
})
