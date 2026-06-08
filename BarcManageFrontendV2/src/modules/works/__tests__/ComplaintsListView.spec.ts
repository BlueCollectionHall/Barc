import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ComplaintsListView from '../views/ComplaintsListView.vue'
import { processComplaint, updateWorkStatus } from '../api/workManage'

const apiMocks = vi.hoisted(() => ({
  processComplaint: vi.fn(),
  updateWorkStatus: vi.fn(),
}))

vi.mock('../api/workManage', () => apiMocks)

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

const complaintRow = {
  id: 'feedback-1',
  target_id: 'work-1',
  status: 'PENDING',
}

const refreshComplaintList = vi.fn()

const ComplaintListStub = defineComponent({
  name: 'ComplaintListComp',
  emits: ['process'],
  setup(_props, { emit, expose }) {
    expose({ refresh: refreshComplaintList })

    return () =>
      h(
        'button',
        {
          'data-testid': 'open-process',
          onClick: () => emit('process', complaintRow),
        },
        '处理',
      )
  },
})

const DialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: '',
    },
  },
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('section', { 'data-testid': 'dialog-stub' }, [
            h('header', props.title),
            slots.default?.(),
            h('footer', slots.footer?.()),
          ])
        : null
  },
})

const SelectStub = defineComponent({
  name: 'ElSelect',
  props: {
    modelValue: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'select',
        {
          'data-testid': 'process-action',
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
    label: {
      type: String,
      required: true,
    },
    value: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    return () => h('option', { value: props.value }, props.label)
  },
})

const InputStub = defineComponent({
  name: 'ElInput',
  props: {
    modelValue: {
      type: String,
      default: '',
    },
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
  setup(_props, { emit, slots }) {
    return () => h('button', { onClick: () => emit('click') }, slots.default?.())
  },
})

function mountView() {
  return mount(ComplaintsListView, {
    global: {
      stubs: {
        ComplaintListComp: ComplaintListStub,
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

describe('ComplaintsListView', () => {
  beforeEach(() => {
    vi.mocked(processComplaint).mockReset().mockResolvedValue('修改成功')
    vi.mocked(updateWorkStatus).mockReset().mockResolvedValue('修改成功')
    refreshComplaintList.mockReset()
  })

  it('offers all WORK complaint moderation actions in Chinese', async () => {
    const wrapper = mountView()

    await wrapper.get('[data-testid="open-process"]').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('封禁作品并完成')
    expect(wrapper.text()).toContain('下架作品并完成')
    expect(wrapper.text()).toContain('恢复作品并完成')
    expect(wrapper.text()).toContain('处理完成')
    expect(wrapper.text()).toContain('驳回投诉')
  })

  it.each([
    ['BAN', 'BAN', '管理员已封禁作品并完成投诉'],
    ['OFF', 'OFF', '管理员已下架作品并完成投诉'],
    ['RESTORE', 'PUBLIC', '管理员已恢复作品公开状态'],
  ])('changes work status before completing feedback for %s', async (action, workStatus, defaultEcho) => {
    const wrapper = mountView()

    await wrapper.get('[data-testid="open-process"]').trigger('click')
    await wrapper.get('[data-testid="process-action"]').setValue(action)
    await wrapper.findAll('button')[2]!.trigger('click')
    await flushPromises()

    expect(updateWorkStatus).toHaveBeenCalledWith('work-1', workStatus, '')
    expect(processComplaint).toHaveBeenCalledWith('feedback-1', 'COMPLETED', defaultEcho)
    expect(vi.mocked(updateWorkStatus).mock.invocationCallOrder[0]!).toBeLessThan(
      vi.mocked(processComplaint).mock.invocationCallOrder[0]!,
    )
  })

  it.each(['COMPLETED', 'REJECTED'])('keeps %s as a feedback-only action', async (action) => {
    const wrapper = mountView()

    await wrapper.get('[data-testid="open-process"]').trigger('click')
    await wrapper.get('[data-testid="process-action"]').setValue(action)
    await wrapper.get('[data-testid="process-remark"]').setValue('人工复核完成')
    await wrapper.findAll('button')[2]!.trigger('click')
    await flushPromises()

    expect(updateWorkStatus).not.toHaveBeenCalled()
    expect(processComplaint).toHaveBeenCalledWith('feedback-1', action, '人工复核完成')
  })
})
