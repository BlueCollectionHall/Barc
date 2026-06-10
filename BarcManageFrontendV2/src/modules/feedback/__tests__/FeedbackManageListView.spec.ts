import { flushPromises, mount } from '@vue/test-utils'
import { computed, defineComponent, h, inject, nextTick, provide, type ComputedRef, type InjectionKey, type PropType } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import FeedbackManageListView from '../views/FeedbackManageListView.vue'
import { getFeedbacksByType, updateFeedbackStatus, type FeedbackRecord } from '../api/feedbackManage'
import { adminChildren } from '@/app/router/routes'
import { MANAGER_PERMISSION } from '@/shared/constants/permissions'

const currentRoute = vi.hoisted(() => ({
  meta: {
    title: 'BUG反馈',
    feedbackType: 'BUG',
  },
}))

const apiMocks = vi.hoisted(() => ({
  getFeedbacksByType: vi.fn(),
  updateFeedbackStatus: vi.fn(),
}))

vi.mock('../api/feedbackManage', () => apiMocks)

vi.mock('vue-router', () => ({
  useRoute: () => currentRoute,
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }
})

const tableRowsKey = Symbol('tableRows') as InjectionKey<ComputedRef<FeedbackRecord[]>>

const RoutePageShellStub = defineComponent({
  name: 'RoutePageShell',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
  },
  setup(props, { slots }) {
    return () => h('section', [h('h1', props.title), h('p', props.subtitle), slots.actions?.(), slots.default?.()])
  },
})

const TableStub = defineComponent({
  name: 'ElTable',
  props: {
    data: { type: Array as PropType<FeedbackRecord[]>, default: () => [] },
  },
  setup(props, { slots }) {
    const rows = computed(() => props.data)
    provide(tableRowsKey, rows)
    return () => h('table', [slots.default?.({ rows: rows.value })])
  },
})

const TableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: {
    label: { type: String, default: '' },
    prop: { type: String, default: '' },
  },
  setup(props, { slots }) {
    const rows = inject(tableRowsKey, computed(() => []))
    return () =>
      h('tbody', [
        h('tr', [h('th', props.label)]),
        ...rows.value.map((row) => h('tr', [h('td', slots.default ? slots.default({ row }) : String(row[props.prop as keyof FeedbackRecord] ?? ''))])),
      ])
  },
})

const DialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('section', { 'data-testid': 'process-dialog' }, [h('header', props.title), slots.default?.(), h('footer', slots.footer?.())])
        : null
  },
})

const SelectStub = defineComponent({
  name: 'ElSelect',
  props: {
    modelValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'select',
        {
          'data-testid': 'process-status',
          value: props.modelValue,
          onChange: (event: Event) => emit('update:modelValue', (event.target as HTMLSelectElement).value),
        },
        slots.default?.(),
      )
  },
})

const OptionStub = defineComponent({
  name: 'ElOption',
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
  },
  setup(props) {
    return () => h('option', { value: props.value }, props.label)
  },
})

const InputStub = defineComponent({
  name: 'ElInput',
  props: {
    modelValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('textarea', {
        'data-testid': 'process-remark',
        value: props.modelValue,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLTextAreaElement).value),
      })
  },
})

const ButtonStub = defineComponent({
  name: 'ElButton',
  emits: ['click'],
  setup(_props, { attrs, emit, slots }) {
    return () => h('button', { ...attrs, onClick: () => emit('click') }, slots.default?.())
  },
})

function mountView() {
  return mount(FeedbackManageListView, {
    global: {
      stubs: {
        RoutePageShell: RoutePageShellStub,
        'el-table': TableStub,
        'el-table-column': TableColumnStub,
        'el-tag': { template: '<span><slot /></span>' },
        'el-empty': { template: '<div />' },
        'el-dialog': DialogStub,
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<label><slot /></label>' },
        'el-select': SelectStub,
        'el-option': OptionStub,
        'el-input': InputStub,
        'el-button': ButtonStub,
      },
    },
  })
}

const bugFeedback: FeedbackRecord = {
  id: 'fb-bug-1',
  target_id: '',
  author: 'alice',
  content: '页面白屏',
  email: 'alice@example.com',
  echo: '已转交技术复核',
  type: 'BUG',
  status: 'PROCESSING',
  created_at: '2026-06-01T10:00:00.000Z',
  updated_at: '2026-06-02T11:00:00.000Z',
}

describe('FeedbackManageListView', () => {
  beforeEach(() => {
    currentRoute.meta.title = 'BUG反馈'
    currentRoute.meta.feedbackType = 'BUG'
    vi.mocked(getFeedbacksByType).mockReset().mockResolvedValue([bugFeedback])
    vi.mocked(updateFeedbackStatus).mockReset().mockResolvedValue('修改成功')
  })

  it('loads feedback by the current route type and displays generic fields', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getFeedbacksByType).toHaveBeenCalledWith('BUG')
    expect(wrapper.text()).toContain('BUG反馈')
    expect(wrapper.text()).toContain('fb-bug-1')
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('页面白屏')
    expect(wrapper.text()).toContain('alice@example.com')
    expect(wrapper.text()).toContain('处理中')
    expect(wrapper.text()).toContain('已转交技术复核')
  })

  it('shows the complained user column only for USER feedback', async () => {
    currentRoute.meta.title = '用户投诉'
    currentRoute.meta.feedbackType = 'USER'
    vi.mocked(getFeedbacksByType).mockResolvedValueOnce([
      {
        ...bugFeedback,
        id: 'fb-user-1',
        target_id: 'user-2',
        type: 'USER',
        status: 'PENDING',
      },
    ])

    const wrapper = mountView()
    await flushPromises()

    expect(getFeedbacksByType).toHaveBeenCalledWith('USER')
    expect(wrapper.text()).toContain('被投诉用户')
    expect(wrapper.text()).toContain('user-2')
    expect(wrapper.text()).toContain('待处理')
  })

  it('submits feedback-only processing statuses with echo remarks', async () => {
    currentRoute.meta.title = '其他反馈'
    currentRoute.meta.feedbackType = 'OTHER'
    vi.mocked(getFeedbacksByType).mockResolvedValue([{
      ...bugFeedback,
      id: 'fb-other-1',
      type: 'OTHER',
      status: 'PENDING',
    }])

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="open-process-fb-other-1"]').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('标记处理中')
    expect(wrapper.text()).toContain('处理完成')
    expect(wrapper.text()).toContain('驳回反馈')

    await wrapper.get('[data-testid="process-status"]').setValue('REJECTED')
    await wrapper.get('[data-testid="process-remark"]').setValue('已回复用户')
    const buttons = wrapper.findAll('button')
    await buttons[buttons.length - 1]!.trigger('click')
    await flushPromises()

    expect(updateFeedbackStatus).toHaveBeenCalledWith('fb-other-1', 'OTHER', 'REJECTED', '已回复用户')
  })

  it('does not load feedback when route meta feedbackType is invalid', async () => {
    currentRoute.meta.title = '反馈管理'
    currentRoute.meta.feedbackType = 'WORK'

    const wrapper = mountView()
    await flushPromises()

    expect(getFeedbacksByType).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('反馈类型配置错误')
    expect(wrapper.text()).toContain('请检查当前菜单路由配置')
  })

  it('registers four feedback management menu routes', () => {
    const routeByPath = new Map(adminChildren.map((route) => [route.path, route]))

    expect(routeByPath.get('users/complaints')?.meta).toMatchObject({
      title: '用户投诉',
      menuGroup: 'users',
      menuGroupLabel: '用户',
      minManagerPermissionBit: MANAGER_PERMISSION.THI_MAINTAINER,
      feedbackType: 'USER',
    })
    expect(routeByPath.get('system/feedback-bug')?.meta).toMatchObject({
      title: 'BUG反馈',
      menuGroup: 'system',
      menuGroupLabel: '系统',
      minManagerPermissionBit: MANAGER_PERMISSION.ADMINISTRATOR,
      feedbackType: 'BUG',
    })
    expect(routeByPath.get('system/feedback-suggestion')?.meta).toMatchObject({
      title: '意见反馈',
      feedbackType: 'SUGGESTION',
    })
    expect(routeByPath.get('system/feedback-other')?.meta).toMatchObject({
      title: '其他反馈',
      feedbackType: 'OTHER',
    })
  })
})
