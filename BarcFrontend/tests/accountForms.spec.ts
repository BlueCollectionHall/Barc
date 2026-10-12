// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h, type Component } from 'vue'
import SignSignupComp from '../src/components/Sign/SignSignupComp.vue'
import SignSigninComp from '../src/components/Sign/SignSigninComp.vue'
import UpdateAccountBasicComp from '../src/components/Update/UpdateAccountBasicComp.vue'
import UpdateAccountPasswordComp from '../src/components/Update/UpdateAccountPasswordComp.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => ({ post: vi.fn() }))
const api = vi.hoisted(() => ({ login: vi.fn(), changeAccountEmail: vi.fn(), sendVerificationCode: vi.fn(), updateAccountBasic: vi.fn(), resetAccountPassword: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), successMessage: vi.fn() }))
const router = vi.hoisted(() => ({ back: vi.fn(), replace: vi.fn() }))
vi.mock('../src/utils/https', () => ({ slowHttp: http, baseHttp: vi.fn() }))
vi.mock('../src/utils/accountApi', () => api)
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRouter: () => router }))
enableAutoUnmount(afterEach)
const form = defineComponent({ name: 'TestForm', emits: ['finish', 'submit'], setup: (_, { emit, slots }) => () => h('form', { onSubmit: (event: Event) => { event.preventDefault(); emit('finish'); emit('submit', event) } }, slots.default?.()) })
const stubs = { ...uiStubs, 'a-form': form, 'el-form': form, 'a-radio-group': true, 'a-radio': true }
async function open(component: Component) { const wrapper = mount(component, { global: { stubs } }); await flushPromises(); return wrapper }
beforeEach(() => {
  vi.resetAllMocks(); sessionStorage.clear(); localStorage.clear(); setActivePinia(createPinia()); localStorage.setItem('token', 'Token old')
  useUserPinia().userBasic = { uuid: 'u', username: 'member', email: 'old@example.test' } as UserBasicImpl
  vi.spyOn(useUserPinia(), 'fetchUserInfo').mockResolvedValue(undefined)
  http.post.mockResolvedValue({ data: { code: 0, msg: '注册成功', data: 'session-code' } }); api.login.mockResolvedValue('Token new'); api.sendVerificationCode.mockResolvedValue('code-session')
  api.changeAccountEmail.mockResolvedValue(null); api.updateAccountBasic.mockResolvedValue(null); api.resetAccountPassword.mockResolvedValue(null)
})
afterEach(() => vi.useRealTimers())
it('注册发送验证码使用 JSON，倒计时拦截重发且卸载清理计时器', async () => {
  vi.useFakeTimers(); const wrapper = await open(SignSignupComp)
  await clickText(wrapper, '获取验证码'); expect(notice.errorMessage).toHaveBeenCalledWith('邮箱不得为空')
  await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('请先获取邮箱验证码')
  await wrapper.findAll('input')[1].setValue('new@example.test'); await clickText(wrapper, '获取验证码')
  expect(http.post).toHaveBeenCalledWith('/email/send/signup', { email: 'new@example.test' }); expect(wrapper.text()).toContain('60')
  expect(JSON.parse(sessionStorage.getItem('barc_signup_code_session')!)).toEqual({ email: 'new@example.test', unique_id: 'session-code' })
  await vi.advanceTimersByTimeAsync(60000); expect(wrapper.text()).toContain('获取验证码'); expect(vi.getTimerCount()).toBe(0)
  await clickText(wrapper, '获取验证码'); wrapper.unmount(); expect(vi.getTimerCount()).toBe(0)
})
it('注册恢复验证码会话后可以提交，成功清理一次性凭证', async () => {
  sessionStorage.setItem('barc_signup_code_session', JSON.stringify({ email: 'restored@example.test', unique_id: 'restored' }))
  const wrapper = await open(SignSignupComp); expect((wrapper.findAll('input')[1].element as HTMLInputElement).value).toBe('restored@example.test')
  await wrapper.findAll('input')[0].setValue('member'); await wrapper.findAll('input')[2].setValue('123456'); await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(http.post).toHaveBeenCalledWith('/user/sign/up', expect.objectContaining({ email: 'restored@example.test', username: 'member', code: '123456' }))
  expect(notice.successMessage).toHaveBeenCalledWith('注册成功'); expect(sessionStorage.getItem('barc_signup_code_session')).toBeNull()
})
it('更换邮箱废弃旧会话，发码和注册失败展示业务原因并允许重试', async () => {
  vi.useFakeTimers(); const wrapper = await open(SignSignupComp); await wrapper.findAll('input')[1].setValue('a@example.test')
  http.post.mockResolvedValueOnce({ data: { code: 1, data: '发送太频繁' } }); await clickText(wrapper, '获取验证码'); expect(notice.errorMessage).toHaveBeenCalledWith('发送太频繁')
  http.post.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '获取验证码'); expect(notice.errorMessage).toHaveBeenCalledWith('验证码发送失败，请检查网络后重试')
  await clickText(wrapper, '获取验证码'); http.post.mockResolvedValueOnce({ data: { code: 1, data: '验证码错误' } }); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('验证码错误')
  http.post.mockRejectedValueOnce(new Error('离线')); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('注册失败，请检查网络后重试')
  await wrapper.findAll('input')[1].setValue('b@example.test'); expect(sessionStorage.getItem('barc_signup_code_session')).toBeNull()
  await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('请先获取邮箱验证码')
})
it('损坏的注册会话忽略，外部登录类型副本不会污染随后本地登录', async () => {
  sessionStorage.setItem('barc_signup_code_session', '{'); await open(SignSignupComp)
  sessionStorage.setItem('barc_signup_code_session', '{"email":5}'); await open(SignSignupComp)
  const wrapper = await open(SignSigninComp); await wrapper.findAll('input')[0].setValue('member'); await wrapper.findAll('input')[1].setValue('password')
  await clickText(wrapper, 'Naigos登录'); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(api.login).toHaveBeenLastCalledWith({ type: 'uid', account: 'member', password: 'password' }, true)
  await clickText(wrapper, 'B.A.R.C.登录'); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(api.login).toHaveBeenLastCalledWith({ type: 'username', account: 'member', password: 'password' }, false)
  expect(localStorage.getItem('token')).toBe('Token new'); expect(router.back).toHaveBeenCalledTimes(2)
  api.login.mockRejectedValueOnce(new Error('密码错误')); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('密码错误')
})
it('基础资料更新带当前密码，成功刷新资料且不改变邮箱', async () => {
  const wrapper = await open(UpdateAccountBasicComp); const inputs = wrapper.findAll('input')
  await inputs[0].setValue('newname'); await inputs[1].setValue('1234'); await inputs[2].setValue('current-pass')
  await wrapper.findAll('form')[0].trigger('submit'); await flushPromises()
  expect(api.updateAccountBasic).toHaveBeenCalledWith({ uuid: 'u', email: 'old@example.test', username: 'newname', telephone: '1234', current_password: 'current-pass' })
  expect(useUserPinia().fetchUserInfo).toHaveBeenCalledWith('Token old'); expect((inputs[2].element as HTMLInputElement).value).toBe('')
  api.updateAccountBasic.mockRejectedValueOnce(new Error('用户名已占用')); await wrapper.findAll('form')[0].trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('用户名已占用')
})
it('邮箱改绑验证新邮箱和当前密码，成功撤销旧会话并重新登录', async () => {
  vi.useFakeTimers(); const wrapper = await open(UpdateAccountBasicComp); const inputs = wrapper.findAll('input')
  await wrapper.findAll('form')[1].trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('请先获取并输入新邮箱验证码')
  await inputs[4].setValue('current-pass'); await inputs[5].setValue('new@example.test'); await clickText(wrapper, '获取')
  expect(api.sendVerificationCode).toHaveBeenCalledWith('change_email', 'new@example.test', 'current-pass'); await inputs[6].setValue('123456')
  api.changeAccountEmail.mockRejectedValueOnce(new Error('验证码错误')); await wrapper.findAll('form')[1].trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('验证码错误')
  await wrapper.findAll('form')[1].trigger('submit'); await flushPromises(); expect(api.changeAccountEmail).toHaveBeenLastCalledWith({ email: 'new@example.test', current_password: 'current-pass', unique_id: 'code-session', code: '123456' })
  expect(localStorage.getItem('token')).toBeNull(); expect(router.replace).toHaveBeenCalledWith({ name: 'Sign' })
  wrapper.unmount(); await vi.advanceTimersByTimeAsync(0); expect(vi.getTimerCount()).toBe(0)
})
it('改绑发码失败可重试，更换目标邮箱必须重新发码', async () => {
  vi.useFakeTimers(); const wrapper = await open(UpdateAccountBasicComp); const inputs = wrapper.findAll('input'); await inputs[5].setValue('one@example.test')
  api.sendVerificationCode.mockRejectedValueOnce(new Error('当前密码错误')); await clickText(wrapper, '获取'); expect(notice.errorMessage).toHaveBeenCalledWith('当前密码错误')
  await clickText(wrapper, '获取'); await vi.advanceTimersByTimeAsync(60000); expect(wrapper.text()).toContain('获取')
  await inputs[5].setValue('two@example.test'); await inputs[6].setValue('123456'); await wrapper.findAll('form')[1].trigger('submit'); await flushPromises(); expect(api.changeAccountEmail).not.toHaveBeenCalled()
})
it('改密校验会话、六位验证码和密码长度，成功清除会话', async () => {
  vi.useFakeTimers(); const wrapper = await open(UpdateAccountPasswordComp); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('请先获取并输入六位验证码')
  api.sendVerificationCode.mockRejectedValueOnce(new Error('发码失败')); await clickText(wrapper, '获取验证码'); expect(notice.errorMessage).toHaveBeenCalledWith('发码失败')
  await clickText(wrapper, '获取验证码'); expect(api.sendVerificationCode).toHaveBeenLastCalledWith('reset_password', 'old@example.test')
  await wrapper.get('input[placeholder="六位验证码"]').setValue('123456'); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('密码长度须为 8 至 128 个字符')
  await wrapper.findAll('input')[0].setValue('new-password'); api.resetAccountPassword.mockRejectedValueOnce(new Error('验证码已过期')); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('验证码已过期')
  await vi.advanceTimersByTimeAsync(60000); await wrapper.get('form').trigger('submit'); await flushPromises(); expect(api.resetAccountPassword).toHaveBeenLastCalledWith({ email: 'old@example.test', unique_id: 'code-session', code: '123456', password: 'new-password' })
  expect(useUserPinia().userBasic).toBeNull(); expect(router.replace).toHaveBeenCalledWith({ name: 'Sign' })
})
