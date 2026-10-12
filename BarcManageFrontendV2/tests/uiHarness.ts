import { computed, defineComponent, h, inject, provide, type ComputedRef } from 'vue'
import { flushPromises, type VueWrapper } from '@vue/test-utils'

/** 保留表格的真实逐行插槽和表单事件，外部 UI 库只替换布局与动画。 */
const Table = defineComponent({
  name: 'ElTable', props: ['data'],
  setup(props, { slots }) {
    provide('test-rows', computed(() => props.data as Record<string, unknown>[]))
    return () => h('section', slots.default?.())
  },
})
const Column = defineComponent({
  name: 'ElTableColumn', props: ['prop', 'label'],
  setup(props, { slots }) {
    const rows = inject<ComputedRef<Record<string, unknown>[]>>('test-rows')
    return () => h('div', [h('strong', props.label), ...(rows?.value || []).map((row, $index) =>
      h('div', { class: 'test-cell' }, slots.default?.({ row, $index }) || String(row[props.prop] ?? '')))])
  },
})
const Container = defineComponent({ setup: (_, { slots }) => () => h('section', [slots.default?.(), slots.header?.(), slots.actions?.(), slots.footer?.()]) })
const Dialog = defineComponent({
  name: 'ElDialog', props: ['modelValue', 'title'], emits: ['update:modelValue'],
  setup: (props, { slots }) => () => props.modelValue ? h('section', [h('header', props.title), slots.default?.(), slots.footer?.()]) : null,
})
const Button = defineComponent({
  name: 'ElButton', props: ['disabled', 'loading'], emits: ['click'],
  setup: (props, { emit, slots }) => () => h('button', { disabled: props.disabled || props.loading, onClick: () => emit('click') }, slots.default?.()),
})
export const uiStubs = {
  ElTable: Table, ElTableColumn: Column, ElButton: Button,
  RoutePageShell: Container, ElForm: Container, ElFormItem: Container,
  'el-dialog': Dialog,
  ElTooltip: Container, ElTag: Container,
  ElPopconfirm: defineComponent({ name: 'ElPopconfirm', emits: ['confirm'], setup: (_, { emit, slots }) => () => h('span', { onClick: () => emit('confirm') }, slots.reference?.()) }),
}
export async function clickText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find(button => button.text() === text)
  if (!button) throw new Error(`找不到按钮：${text}`)
  await button.trigger('click')
  await flushPromises()
}
