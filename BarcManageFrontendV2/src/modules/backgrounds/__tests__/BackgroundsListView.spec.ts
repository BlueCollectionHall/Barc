import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import BackgroundsListView from '@/modules/backgrounds/views/BackgroundsListView.vue'
import type { BackgroundImageRecord, BackgroundOrderItem } from '@/modules/backgrounds/api/backgrounds.service'

const {
  fetchBackgroundModules,
  fetchBackgroundList,
  uploadBackground,
  reorderBackground,
  setBackgroundEnabled,
  deleteBackground,
  showError,
  showSuccess,
  confirm,
} = vi.hoisted(() => ({
  fetchBackgroundModules: vi.fn(),
  fetchBackgroundList: vi.fn(),
  uploadBackground: vi.fn(),
  reorderBackground: vi.fn(),
  setBackgroundEnabled: vi.fn(),
  deleteBackground: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/modules/backgrounds/api/backgrounds.service', () => ({
  fetchBackgroundModules,
  fetchBackgroundList,
  uploadBackground,
  reorderBackground,
  setBackgroundEnabled,
  deleteBackground,
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showSuccess,
  showInfo: vi.fn(),
  showWarning: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessageBox: {
      confirm,
    },
    ElMessage: {
      error: vi.fn(),
      success: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    },
  }
})

function createRecord(id: string, filename: string, enabled = true): BackgroundImageRecord {
  return {
    id,
    module: 'home',
    object_key: `client/bg/home/${id}`,
    filename,
    sort_order: 1,
    enabled,
    created_by: null,
    created_at: null,
    updated_at: null,
    deleted_at: null,
    url: `https://signed/${id}`,
  }
}

function findButton(wrapper: ReturnType<typeof mount>, label: string, index = 0) {
  const matches = wrapper.findAll('button').filter((candidate) => candidate.text() === label)
  const button = matches[index]
  if (!button) {
    throw new Error(`Unable to find button: ${label}#${index}`)
  }
  return button
}

describe('BackgroundsListView', () => {
  beforeEach(() => {
    fetchBackgroundModules.mockResolvedValue([
      { value: 'sign', label: '登录/注册' },
      { value: 'home', label: '首页' },
    ])
    fetchBackgroundList.mockResolvedValue([
      createRecord('bg-1', 'a.jpg', true),
      createRecord('bg-2', 'b.jpg', false),
    ])
    deleteBackground.mockResolvedValue(null)
    reorderBackground.mockResolvedValue(null)
    setBackgroundEnabled.mockResolvedValue(createRecord('bg-1', 'a.jpg', false))
    uploadBackground.mockResolvedValue(createRecord('bg-3', 'c.jpg', true))
    confirm.mockResolvedValue(undefined)
  })

  it('loads module options, defaults to the first module, and renders its list', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    expect(fetchBackgroundModules).toHaveBeenCalledTimes(1)
    // 默认选择第一个模块（sign），并加载该模块列表
    expect(fetchBackgroundList).toHaveBeenCalledWith('sign')
    expect(wrapper.text()).toContain('a.jpg')
    expect(wrapper.text()).toContain('b.jpg')
    expect(showError).not.toHaveBeenCalled()
  })

  it('confirms and deletes a background image, then reloads the list', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    await findButton(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalledWith(
      '删除后该背景图将不再展示，是否继续？',
      '确认删除',
      expect.any(Object),
    )
    expect(deleteBackground).toHaveBeenCalledWith('bg-1')
    expect(fetchBackgroundList).toHaveBeenCalledTimes(2)
    expect(showSuccess).toHaveBeenCalledWith('背景图已删除')
  })

  it('moves an item down and persists the new order', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    await findButton(wrapper, '下移').trigger('click')
    await flushPromises()

    const ordered: BackgroundOrderItem[] = [
      { id: 'bg-2', sort_order: 1 },
      { id: 'bg-1', sort_order: 2 },
    ]
    expect(reorderBackground).toHaveBeenCalledWith(ordered)
  })

  it('persists the enabled switch change', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    const switchComponent = wrapper.findComponent({ name: 'ElSwitch' })
    await switchComponent.vm.$emit('update:modelValue', false)
    await switchComponent.vm.$emit('change', false)
    await flushPromises()

    expect(setBackgroundEnabled).toHaveBeenCalledWith('bg-1', false)
    expect(showSuccess).toHaveBeenCalledWith('背景图已停用')
  })
})
