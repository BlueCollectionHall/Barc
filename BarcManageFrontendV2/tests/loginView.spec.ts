import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { reactive } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import LoginView from '../src/modules/auth/views/LoginView.vue'
import { ROUTE_REASON } from '../src/app/router/meta'

const deps = vi.hoisted(() => ({ signInWithBarc: vi.fn(), signInWithNaigos: vi.fn(), authenticating: false, replace: vi.fn(), showError: vi.fn(), showSuccess: vi.fn(), showInfo: vi.fn(), showWarning: vi.fn() }))
const route = reactive({ query: {} as Record<string, unknown> })
vi.mock('../src/app/stores/auth', () => ({ useAuthStore: () => deps }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ replace: deps.replace }) }))
vi.mock('../src/shared/utils/message', () => deps)
enableAutoUnmount(afterEach)
beforeEach(() => { vi.resetAllMocks(); route.query = {} })
it('本地登录校验空凭据，账号去空格但密码原样提交，成功后回原页', async () => {
  route.query = { redirect: '/works/claims' }
  const wrapper = mount(LoginView)
  const form = wrapper.findAllComponents({ name: 'ElForm' })[0]!
  await form.trigger('submit'); await flushPromises(); expect(deps.showError).toHaveBeenCalled(); expect(deps.signInWithBarc).not.toHaveBeenCalled()
  const inputs = form.findAllComponents({ name: 'ElInput' }); inputs[0]!.vm.$emit('update:modelValue', ' owner '); inputs[1]!.vm.$emit('update:modelValue', ' password '); await flushPromises()
  await form.trigger('submit'); await flushPromises()
  expect(deps.signInWithBarc).toHaveBeenCalledWith({ type: 'username', account: 'owner', password: ' password ' }); expect(deps.replace).toHaveBeenCalledWith('/works/claims')
  deps.signInWithBarc.mockRejectedValueOnce(new Error('账号冻结')); await form.trigger('submit'); await flushPromises(); expect(deps.showError).toHaveBeenCalledWith('账号冻结')
})
it('Naigos 登录保持独立登录类型与失败提示', async () => {
  const wrapper = mount(LoginView); wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'naigos'); await flushPromises()
  const form = wrapper.findAllComponents({ name: 'ElForm' })[1]!
  await form.trigger('submit'); await flushPromises(); expect(deps.showError).toHaveBeenCalledWith('请填写完整的 Naigos 凭据。')
  const inputs = form.findAllComponents({ name: 'ElInput' }); inputs[0]!.vm.$emit('update:modelValue', ' external '); inputs[1]!.vm.$emit('update:modelValue', ' password '); await flushPromises()
  await form.trigger('submit'); await flushPromises(); expect(deps.signInWithNaigos).toHaveBeenCalledWith({ type: 'uid', account: 'external', password: ' password ' }); expect(deps.replace).toHaveBeenCalledWith('/dashboard')
  deps.signInWithNaigos.mockRejectedValueOnce(new Error('外部服务失败')); await form.trigger('submit'); await flushPromises(); expect(deps.showError).toHaveBeenCalledWith('外部服务失败')
})
it('登录原因在初始进入和后续路由变化时清楚说明', async () => {
  route.query = { reason: ROUTE_REASON.AUTH_REQUIRED }
  const wrapper = mount(LoginView); expect(deps.showInfo).toHaveBeenCalled()
  route.query = { reason: ROUTE_REASON.MANAGER_ONLY }; await flushPromises(); expect(deps.showWarning).toHaveBeenCalled(); expect(wrapper.text()).toContain('只有 identity')
  route.query = { reason: 'unknown' }; await flushPromises(); expect(deps.showWarning).toHaveBeenCalledTimes(1)
  route.query = { reason: [] }; await flushPromises(); expect(deps.showInfo).toHaveBeenCalledTimes(1)
  route.query = { reason: ROUTE_REASON.SESSION_EXPIRED }; await flushPromises(); expect(wrapper.text()).toContain('登录状态已失效')
})
