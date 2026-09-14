import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import BackgroundsListView from '@/modules/backgrounds/views/BackgroundsListView.vue'
import type { BackgroundImageRecord, BackgroundOrderItem } from '@/modules/backgrounds/api/backgrounds.service'

const {
  fetchBackgroundModules,
  fetchBackgroundList,
  uploadBackground,
  reorderBackground,
  setBackgroundEnabled,
  updateBackgroundScene,
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
  updateBackgroundScene: vi.fn(),
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
  updateBackgroundScene,
  deleteBackground,
  BACKGROUND_TIME_PERIOD_OPTIONS: [
    { value: null, label: '任意时段' },
    { value: 'day', label: '白天' },
    { value: 'eventing', label: '傍晚' },
    { value: 'night', label: '夜晚' },
  ],
  BACKGROUND_FESTIVAL_OPTIONS: [
    { value: null, label: '无' },
    { value: 'newyear', label: '新年' },
  ],
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

function createRecord(
  id: string,
  filename: string,
  enabled = true,
  scene: { time_period?: string | null; festival?: string | null } = {},
): BackgroundImageRecord {
  return {
    id,
    module: 'home',
    object_key: `client/bg/home/${id}`,
    filename,
    sort_order: 1,
    enabled,
    time_period: scene.time_period ?? null,
    festival: scene.festival ?? null,
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

/** 把模块下拉切到「首页」（启用场景化的模块）并等待列表重载 */
async function switchToHome(wrapper: ReturnType<typeof mount>) {
  const moduleSelect = wrapper.findAllComponents({ name: 'ElSelect' })[0]
  await moduleSelect!.vm.$emit('update:modelValue', 'home')
  await moduleSelect!.vm.$emit('change', 'home')
  await flushPromises()
}

/**
 * 按 data-test 取单个下拉组件实例。
 * findComponent 传 CSS 选择器时返回 WrapperLike（类型上没有 vm），
 * 这里统一断言成 VueWrapper，便于在测试中 emit 组件事件。
 */
function findSelect(wrapper: ReturnType<typeof mount>, testId: string): VueWrapper {
  return wrapper.findComponent(`[data-test="${testId}"]`) as unknown as VueWrapper
}

/** 按 data-test 取多个下拉组件实例（返回类型同上，统一为 VueWrapper 数组） */
function findAllSelects(wrapper: ReturnType<typeof mount>, testId: string): VueWrapper[] {
  return wrapper.findAllComponents(`[data-test="${testId}"]`) as unknown as VueWrapper[]
}

describe('BackgroundsListView', () => {
  beforeEach(() => {
    fetchBackgroundModules.mockResolvedValue([
      { value: 'sign', label: '登录/注册', sceneEnabled: false },
      { value: 'home', label: '首页', sceneEnabled: true },
    ])
    fetchBackgroundList.mockResolvedValue([
      createRecord('bg-1', 'a.jpg', true),
      createRecord('bg-2', 'b.jpg', false),
    ])
    deleteBackground.mockResolvedValue(null)
    reorderBackground.mockResolvedValue(null)
    setBackgroundEnabled.mockResolvedValue(createRecord('bg-1', 'a.jpg', false))
    updateBackgroundScene.mockResolvedValue(createRecord('bg-1', 'a.jpg', true))
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

  it('hides scene columns and shows a hint for a non-scene module', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    // sign 为纯随机模块：除了模块下拉，不应再出现「时段/节日」select
    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    expect(selects.length).toBe(1)
    expect(wrapper.text()).toContain('该模块按启用顺序随机轮播，暂不支持时段/节日')
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

  it('persists the scene select change for the home module', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    // 先切到「首页」（启用场景化的模块）
    const moduleSelect = wrapper.findAllComponents({ name: 'ElSelect' })[0]
    await moduleSelect!.vm.$emit('update:modelValue', 'home')
    await moduleSelect!.vm.$emit('change', 'home')
    await flushPromises()

    // 取首行的「时段」下拉（用 data-test 定位，避免与筛选栏的下拉混淆）
    const timeSelect = findAllSelects(wrapper, 'row-time-period')[0]
    expect(timeSelect).toBeDefined()
    await timeSelect!.vm.$emit('update:modelValue', 'day')
    await timeSelect!.vm.$emit('change', 'day')
    await flushPromises()

    expect(updateBackgroundScene).toHaveBeenCalledWith('bg-1', 'day', null)
    expect(showSuccess).toHaveBeenCalledWith('背景图场景已更新')
  })

  it('shows the scene filter only for a scene-enabled module', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()

    // 默认 sign（纯随机模块）→ 不渲染筛选栏
    expect(wrapper.find('[data-test="scene-filter"]').exists()).toBe(false)

    await switchToHome(wrapper)

    // 首页（启用场景化）→ 出现筛选栏
    expect(wrapper.find('[data-test="scene-filter"]').exists()).toBe(true)
  })

  it('filters rows by time period and can narrow down to untagged rows', async () => {
    fetchBackgroundList.mockResolvedValue([
      createRecord('bg-day', 'day.jpg', true, { time_period: 'day' }),
      createRecord('bg-night', 'night.jpg', true, { time_period: 'night' }),
      createRecord('bg-any', 'any.jpg', true, { time_period: null }),
    ])
    const wrapper = mount(BackgroundsListView)

    await flushPromises()
    await switchToHome(wrapper)

    const timeSelect = findSelect(wrapper, 'filter-time-period')

    // 选「白天」→ 只剩 day.jpg
    await timeSelect.vm.$emit('update:modelValue', 'day')
    await flushPromises()
    expect(wrapper.text()).toContain('day.jpg')
    expect(wrapper.text()).not.toContain('night.jpg')
    expect(wrapper.text()).not.toContain('any.jpg')

    // 选「未标注（任意）」（null）→ 只剩未标注时段的图（兼容无时段）
    await timeSelect.vm.$emit('update:modelValue', null)
    await flushPromises()
    expect(wrapper.text()).toContain('any.jpg')
    expect(wrapper.text()).not.toContain('day.jpg')
    expect(wrapper.text()).not.toContain('night.jpg')
  })

  it('filters rows by festival and can narrow down to untagged rows', async () => {
    fetchBackgroundList.mockResolvedValue([
      createRecord('bg-ny', 'ny.jpg', true, { festival: 'newyear' }),
      createRecord('bg-plain', 'plain.jpg', true, { festival: null }),
    ])
    const wrapper = mount(BackgroundsListView)

    await flushPromises()
    await switchToHome(wrapper)

    const festivalSelect = findSelect(wrapper, 'filter-festival')

    await festivalSelect.vm.$emit('update:modelValue', 'newyear')
    await flushPromises()
    expect(wrapper.text()).toContain('ny.jpg')
    expect(wrapper.text()).not.toContain('plain.jpg')

    // 选「未标注（无节日）」（null）→ 兼容无节日的筛选
    await festivalSelect.vm.$emit('update:modelValue', null)
    await flushPromises()
    expect(wrapper.text()).toContain('plain.jpg')
    expect(wrapper.text()).not.toContain('ny.jpg')
  })

  it('combines both filters and reports how many images match', async () => {
    fetchBackgroundList.mockResolvedValue([
      createRecord('bg-1', 'day-newyear.jpg', true, { time_period: 'day', festival: 'newyear' }),
      createRecord('bg-2', 'night-newyear.jpg', true, { time_period: 'night', festival: 'newyear' }),
    ])
    const wrapper = mount(BackgroundsListView)

    await flushPromises()
    await switchToHome(wrapper)

    await findSelect(wrapper, 'filter-time-period').vm.$emit('update:modelValue', 'day')
    await findSelect(wrapper, 'filter-festival').vm.$emit('update:modelValue', 'newyear')
    await flushPromises()

    const summary = wrapper.find('[data-test="filter-summary"]').text()
    expect(summary).toContain('命中 1')
    expect(summary).toContain('共 2 张')
    expect(wrapper.text()).toContain('day-newyear.jpg')
    expect(wrapper.text()).not.toContain('night-newyear.jpg')
  })

  it('disables reordering while a filter is active', async () => {
    const wrapper = mount(BackgroundsListView)

    await flushPromises()
    await switchToHome(wrapper)

    await findSelect(wrapper, 'filter-time-period').vm.$emit('update:modelValue', null)
    await flushPromises()

    const moveButtons = wrapper
      .findAll('button')
      .filter((candidate) => candidate.text() === '上移' || candidate.text() === '下移')
    expect(moveButtons.length).toBeGreaterThan(0)
    expect(
      moveButtons.every((candidate) => candidate.attributes('disabled') !== undefined),
    ).toBe(true)
  })
})
