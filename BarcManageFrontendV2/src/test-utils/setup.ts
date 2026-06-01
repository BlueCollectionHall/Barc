import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'

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
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('section', { 'data-testid': 'dialog-stub' }, [h('header', props.title), slots.default?.()]) : null)
  },
})

const DrawerStub = defineComponent({
  name: 'ElDrawer',
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
  emits: ['close'],
  setup(props, { slots }) {
    return () => (props.modelValue ? h('section', { 'data-testid': 'drawer-stub' }, [h('header', props.title), slots.default?.()]) : null)
  },
})

const FormItemStub = defineComponent({
  name: 'ElFormItem',
  props: {
    error: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: '',
    },
  },
  setup(props, { slots }) {
    return () =>
      h('section', { 'data-testid': 'form-item-stub' }, [
        props.label ? h('label', props.label) : null,
        slots.default?.(),
        props.error ? h('p', { 'data-testid': 'form-item-error' }, props.error) : null,
      ])
  },
})

config.global.plugins = [ElementPlus]
config.global.stubs = {
  transition: false,
  'transition-group': false,
  teleport: true,
  'el-dialog': DialogStub,
  'el-drawer': DrawerStub,
  'el-form-item': FormItemStub,
}

class TestResizeObserver implements ResizeObserver {
  observe(_target: Element, _options?: ResizeObserverOptions): void {}

  unobserve(_target: Element): void {}

  disconnect(): void {}
}

if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = TestResizeObserver
}

if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  })
}

if (!window.scrollTo) {
  Object.defineProperty(window, 'scrollTo', {
    configurable: true,
    writable: true,
    value: vi.fn(),
  })
}

if (!globalThis.requestAnimationFrame) {
  Object.defineProperty(globalThis, 'requestAnimationFrame', {
    configurable: true,
    writable: true,
    value: (callback: FrameRequestCallback): number => window.setTimeout(() => callback(performance.now()), 16),
  })
}

if (!window.requestAnimationFrame) {
  Object.defineProperty(window, 'requestAnimationFrame', {
    configurable: true,
    writable: true,
    value: globalThis.requestAnimationFrame,
  })
}

if (!globalThis.cancelAnimationFrame) {
  Object.defineProperty(globalThis, 'cancelAnimationFrame', {
    configurable: true,
    writable: true,
    value: (handle: number): void => {
      window.clearTimeout(handle)
    },
  })
}

if (!window.cancelAnimationFrame) {
  Object.defineProperty(window, 'cancelAnimationFrame', {
    configurable: true,
    writable: true,
    value: globalThis.cancelAnimationFrame,
  })
}

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})
