import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import ImageUploadField from '../src/modules/school-club-student/components/ImageUploadField.vue'
import { SESSION_TOKEN_KEY } from '../src/shared/constants/auth'
import { clickText, uiStubs } from './uiHarness'

vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessage: { success: vi.fn(), error: vi.fn() } }))
enableAutoUnmount(afterEach)
beforeEach(() => { vi.clearAllMocks(); localStorage.clear() })
const uploadUrl = '/api/file/upload?path=students%2Fid%2Favatar_rectangle'
it('相对上传路径可用，认证头使用统一 Token 格式', async () => {
  localStorage.setItem(SESSION_TOKEN_KEY, 'jwt')
  const wrapper = mount(ImageUploadField, { props: { uploadUrl }, global: { stubs: uiStubs } })
  const uploader = wrapper.findComponent({ name: 'ElUpload' })
  expect(uploader.props('headers')).toEqual({ Authorization: 'Token jwt' })
  await wrapper.setProps({ uploadUrl: '' }); expect(wrapper.text()).toContain('请先填写 ID / 英文名')
  await wrapper.setProps({ uploadUrl: '/api/file/upload?path=bad//path' }); expect(wrapper.text()).toContain('请先填写 ID / 英文名')
})
it('文件类型与大小先验证，上传成功只发出对象 key', async () => {
  const wrapper = mount(ImageUploadField, { props: { uploadUrl }, global: { stubs: uiStubs } })
  const uploader = wrapper.findComponent({ name: 'ElUpload' })
  const beforeUpload = uploader.props('beforeUpload') as (file: File) => boolean
  expect(beforeUpload(new File(['html'], 'fake.png', { type: 'text/html' }))).toBe(false)
  const oversized = new File(['x'], 'image.png', { type: 'image/png' }); Object.defineProperty(oversized, 'size', { value: 11 * 1024 * 1024 })
  expect(beforeUpload(oversized)).toBe(false)
  expect(beforeUpload(new File(['png'], 'image.png', { type: 'image/png' }))).toBe(true)
  const success = uploader.props('onSuccess') as (response: unknown) => void
  success({ code: 0, msg: 'ok', data: 'objects/image.png' }); await flushPromises()
  expect(wrapper.emitted('update:modelValue')).toEqual([['objects/image.png']])
  success({ code: 0, msg: '格式不符', data: { unexpected: true } }); await flushPromises()
  expect(wrapper.emitted('update:modelValue')).toHaveLength(1); expect(ElMessage.error).toHaveBeenCalledWith('格式不符')
  success(null); expect(ElMessage.error).toHaveBeenCalledWith('上传响应无效')
})
it('独立保存成功后通知父级换签名 URL，失败保留待保存状态', async () => {
  const saveFn = vi.fn().mockResolvedValue('ok')
  const wrapper = mount(ImageUploadField, { props: { uploadUrl, entityId: 'id', saveFn }, global: { stubs: uiStubs } })
  const uploader = wrapper.findComponent({ name: 'ElUpload' })
  ;(uploader.props('onSuccess') as (response: unknown) => void)({ code: 0, msg: 'ok', data: 'objects/new' })
  await wrapper.setProps({ modelValue: 'objects/new' }); await flushPromises()
  await clickText(wrapper, '保存图片'); expect(saveFn).toHaveBeenCalledWith('id', 'objects/new'); expect(wrapper.emitted('saved')).toEqual([[]])
  await wrapper.setProps({ previewUrl: 'https://signed.example/new' }); expect(wrapper.get('img').attributes('src')).toBe('https://signed.example/new')
  await clickText(wrapper, '替换'); await wrapper.setProps({ modelValue: '' }); await flushPromises()
  const nextUploader = wrapper.findComponent({ name: 'ElUpload' })
  ;(nextUploader.props('onSuccess') as (response: unknown) => void)({ code: 0, msg: 'ok', data: 'objects/other' })
  await wrapper.setProps({ modelValue: 'objects/other' }); saveFn.mockRejectedValueOnce(new Error('无权')); await clickText(wrapper, '保存图片')
  expect(ElMessage.error).toHaveBeenCalledWith('无权'); expect(wrapper.text()).toContain('保存图片')
})
it('失败上传清除临时状态并显示可读错误，旧 URL 仍可预览', async () => {
  const wrapper = mount(ImageUploadField, { props: { uploadUrl }, global: { stubs: uiStubs } })
  const uploader = wrapper.findComponent({ name: 'ElUpload' })
  ;(uploader.props('onError') as (error: unknown) => void)(new Error('上传失败原因')); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('上传失败原因')
  await wrapper.setProps({ modelValue: 'https://legacy.example/a' }); expect(wrapper.get('img').attributes('src')).toBe('https://legacy.example/a')
  await clickText(wrapper, '替换'); expect(wrapper.emitted('update:modelValue')).toEqual([['']])
})
