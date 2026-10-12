import { defineComponent, h } from 'vue'
import { flushPromises, type VueWrapper } from '@vue/test-utils'

/** 替换外部 UI 动画与布局，保留 v-model、提交、确认等组件交互协议。 */
const container = defineComponent({ setup: (_, { slots }) => () => h('section', [slots.default?.(), slots.header?.(), slots.footer?.()]) })
const button = defineComponent({
  props: ['disabled', 'loading'], emits: ['click'],
  setup: (props, { emit, slots }) => () => h('button', { disabled: props.disabled || props.loading, onClick: () => emit('click') }, slots.default?.()),
})
const input = defineComponent({
  name: 'TestInput', props: ['value', 'modelValue', 'placeholder', 'disabled', 'type'], emits: ['update:value', 'update:modelValue', 'change', 'pressEnter'],
  setup: (props, { emit }) => () => h(props.type === 'textarea' ? 'textarea' : 'input', {
    value: props.value ?? props.modelValue ?? '', placeholder: props.placeholder, disabled: props.disabled,
    onInput: (event: Event) => { const value = (event.target as HTMLInputElement).value; emit('update:value', value); emit('update:modelValue', value) },
    onChange: (event: Event) => emit('change', event),
    onKeydown: (event: KeyboardEvent) => { if (event.key === 'Enter') emit('pressEnter', event) },
  }),
})
const textarea = defineComponent({ ...input, setup: (props, { emit }) => () => h('textarea', { value: props.value ?? props.modelValue ?? '', placeholder: props.placeholder,
  onInput: (event: Event) => { const value = (event.target as HTMLTextAreaElement).value; emit('update:value', value); emit('update:modelValue', value) },
  onKeydown: (event: KeyboardEvent) => { if (event.key === 'Enter') emit('pressEnter', event) },
}) })
const modal = defineComponent({
  name: 'AModal', props: ['open', 'title', 'okText', 'footer'], emits: ['ok', 'cancel', 'update:open'],
  setup: (props, { emit, slots }) => () => props.open ? h('section', { class: 'test-modal' }, [h('h3', props.title), slots.default?.(), slots.footer?.(),
    props.footer !== null ? h('button', { onClick: () => emit('ok') }, props.okText || '确定') : null,
    h('button', { class: 'modal-close', onClick: () => { emit('update:open', false); emit('cancel') } }, '关闭'),
  ]) : null,
})
export const uiStubs = {
  'a-button': button, 'el-button': button, 'a-input': input, 'el-input': input, 'a-input-password': input, 'a-textarea': textarea,
  'a-modal': modal, 'a-spin': container, 'a-tooltip': container, 'a-flex': container, 'a-form': container, 'a-form-item': container,
  'el-form': container, 'el-form-item': container, 'el-tag': container, 'el-tooltip': container, 'el-card': container,
  'el-icon': container, 'el-container': container, 'el-aside': container, 'el-main': container, 'el-menu': container, 'el-menu-item': button,
  'a-popconfirm': defineComponent({ name: 'APopconfirm', emits: ['confirm'], setup: (_, { emit, slots }) => () => h('span', { onClick: () => emit('confirm') }, slots.default?.()) }),
  'el-popconfirm': defineComponent({ name: 'ElPopconfirm', emits: ['confirm'], setup: (_, { emit, slots }) => () => h('span', { onClick: () => emit('confirm') }, slots.reference?.()) }),
  'el-image': defineComponent({ props: ['src'], setup: props => () => h('img', { src: props.src }) }),
  'router-link': defineComponent({ props: ['to'], setup: (_, { slots }) => () => h('a', slots.default?.()) }),
}
export async function clickText(wrapper: VueWrapper, text: string) {
  const target = wrapper.findAll('button, .action-btn').find(item => item.text().trim() === text)
  if (!target) throw new Error(`找不到操作：${text}`)
  await target.trigger('click'); await flushPromises()
}
