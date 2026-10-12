// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import ManageWorkEditComp from '../src/components/Manage/ManageWorkEditComp.vue'
import UploadWorkComp from '../src/components/Upload/UploadWorkComp.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => Object.assign(vi.fn(), { get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), infoMessage: vi.fn(), successMessage: vi.fn() }))
const navigation = vi.hoisted(() => ({ route: { query: { work_id: 'w', username: 'member' } }, push: vi.fn() }))
const crop = vi.hoisted(() => ({ destroy: vi.fn(), canvas: vi.fn() }))
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => ({ push: navigation.push }) }))
vi.mock('cropperjs', () => ({ default: class { destroy = crop.destroy; getCroppedCanvas = crop.canvas } }))
vi.mock('@vueup/vue-quill', () => ({ QuillEditor: defineComponent({ name: 'QuillEditor', props: ['content'], emits: ['update:content'],
  setup: (props, { emit }) => () => h('textarea', { class: 'test-editor', value: props.content, onInput: (e: Event) => emit('update:content', (e.target as HTMLTextAreaElement).value) }),
}) }))
enableAutoUnmount(afterEach)
const work = () => ({ id: 'w', title: '原标题', description: '原简介', content: '<p>正文</p>', is_claim: false, author: 'official', uploader: 'u', author_nickname: '来源', student: 's', status: 'PUBLIC', review_status: 'APPROVED', content_revision: 7 })
const detail = () => ({ work: work(), cover_image_url: '/signed-cover', content_images: [{ id: 'image-1', url: '/signed-image', sort: 1 }], student_name: '星野', author_display: '来源', uploader_nickname: '收录员' })
const ok = (data: unknown = null) => ({ data: { code: 0, msg: '成功', data } })
const student = { id: 's2', cn_name: '白子' }
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear(); localStorage.setItem('token', 'Token fixture'); setActivePinia(createPinia())
  navigation.route.query = { work_id: 'w', username: 'member' }
  useUserPinia().userBasic = { uuid: 'u', username: 'member' } as UserBasicImpl
  vi.spyOn(useUserPinia(), 'fetchUserInfo').mockResolvedValue(undefined)
  http.get.mockImplementation(async (url: string) => url.includes('edit-detail') ? ok(detail()) : ok([{ id: 'cat', name: '插画' }]))
  http.mockImplementation(async (url: string) => url.includes('student/list') ? ok({ list: [student] }) : ok('new-work'))
  http.put.mockResolvedValue(ok()); http.post.mockResolvedValue(ok()); http.delete.mockResolvedValue(ok())
  crop.canvas.mockReturnValue({ toBlob: (callback: (blob: Blob | null) => void) => callback(new Blob(['cropped'], { type: 'image/png' })) })
  vi.stubGlobal('FileReader', class { result = 'data:image/png;base64,fixture'; onloadend: (() => void) | null = null; readAsDataURL() { queueMicrotask(() => this.onloadend?.()) } })
  let imageId = 0; URL.createObjectURL = vi.fn(() => `blob:fixture-${++imageId}`)
})
afterEach(() => vi.unstubAllGlobals())
const radio = defineComponent({ name: 'ElRadioGroup', props: ['modelValue'], emits: ['update:modelValue'], setup: (_, { slots }) => () => h('section', slots.default?.()) })
async function open(edit = true) {
  const wrapper = mount(edit ? ManageWorkEditComp : UploadWorkComp, { global: { stubs: { ...uiStubs, 'el-radio-group': radio, 'el-radio': true } } })
  await flushPromises(); return wrapper
}
async function files(wrapper: VueWrapper, index: number, names = ['original.png']) {
  const input = wrapper.findAll('input[type=file]')[index]
  Object.defineProperty(input.element, 'files', { configurable: true, value: names.map(name => new File([name], name, { type: 'image/png' })) })
  await input.trigger('change'); await flushPromises()
}
async function selectStudent(wrapper: VueWrapper) {
  await wrapper.get('input[placeholder="学生的中文或英文名"]').setValue('白子'); await clickText(wrapper, '搜索'); await clickText(wrapper, '白子')
}
async function readyUpload(wrapper: VueWrapper) {
  await files(wrapper, 0); await clickText(wrapper, '确认使用'); await selectStudent(wrapper)
  await wrapper.findAll('.work_props_box input')[1].setValue('新作品')
  await wrapper.get('input[placeholder="请输入该作品的作者在其他主流平台的昵称"]').setValue('来源作者')
}
it('编辑读取带身份的专用详情，无修改时不发送请求，返回作品管理', async () => {
  const wrapper = await open(); expect(http.get).toHaveBeenCalledWith('/api/work/edit-detail', { params: { work_id: 'w' }, headers: { Authorization: 'Token fixture' } })
  expect(wrapper.text()).toContain('星野'); await clickText(wrapper, '保存修改'); expect(http.put).not.toHaveBeenCalled()
  expect(notice.infoMessage).toHaveBeenCalledWith('还没有需要保存的修改哦'); await clickText(wrapper, '返回作品管理'); expect(navigation.push).toHaveBeenCalledWith({ name: 'ManageWork' })
})
it('编辑正文仅提交字段白名单及读取版本，保留归属并过滤脚本', async () => {
  const wrapper = await open(); await wrapper.findAll('.work_props_box input')[1].setValue('改后标题'); await selectStudent(wrapper)
  await wrapper.get('.test-editor').setValue('<p>新正文</p><script>alert(1)</script>'); await clickText(wrapper, '保存修改')
  expect(http.put).toHaveBeenCalledWith('/api/work/edit-update', { id: 'w', title: '改后标题', description: '原简介', content: '<p>新正文</p>', author_nickname: '来源', student: 's2', expected_revision: 7 }, expect.anything())
  expect(notice.successMessage).toHaveBeenCalledWith('作品修改已保存，并已重新提交审核')
})
it('选封面不直接提交，确认裁剪后按文字、封面、删除、追加顺序保存', async () => {
  const wrapper = await open(); await files(wrapper, 0); await clickText(wrapper, '保存修改'); expect(http.put).not.toHaveBeenCalled()
  await clickText(wrapper, '确认使用'); await wrapper.findAll('.work_props_box input')[1].setValue('同时改字')
  await clickText(wrapper, '删除'); await files(wrapper, 1, ['a.png', 'b.png']); await clickText(wrapper, '移除待传')
  await clickText(wrapper, '保存修改')
  expect(http.put.mock.calls.map(call => call[0])).toEqual(['/api/work/edit-update', '/api/work/cover/replace'])
  const cover = http.put.mock.calls[1][1] as FormData; expect((cover.get('cover_image') as File).name).toBe('cropped-cover.png')
  expect(http.delete).toHaveBeenCalledWith('/api/work/image/delete', { params: { work_image_id: 'image-1' }, headers: { Authorization: 'Token fixture' } })
  expect(http.post).toHaveBeenCalledTimes(1); expect(((http.post.mock.calls[0][1] as FormData).get('file') as File).name).toBe('b.png')
  expect(http.put.mock.invocationCallOrder[1]).toBeLessThan(http.delete.mock.invocationCallOrder[0]); expect(http.delete.mock.invocationCallOrder[0]).toBeLessThan(http.post.mock.invocationCallOrder[0])
  expect(crop.destroy).toHaveBeenCalled(); expect(wrapper.text()).toContain('文字无修改')
})
it('保存冲突立即停止，保留待保存图片供重试', async () => {
  const wrapper = await open(); await wrapper.findAll('.work_props_box input')[1].setValue('修改'); await files(wrapper, 1)
  http.put.mockResolvedValueOnce({ data: { code: 409, msg: '版本冲突' } }); await clickText(wrapper, '保存修改')
  expect(http.post).not.toHaveBeenCalled(); expect(notice.errorMessage).toHaveBeenCalledWith('版本冲突'); expect(wrapper.text()).toContain('新增图1')
})
it('审核未通过保存后提示再次提审，旧 URL 图集不产生错误删除请求', async () => {
  http.get.mockResolvedValueOnce(ok({ ...detail(), work: { ...work(), review_status: 'REJECTED' }, content_images: [], content_image_urls: ['/legacy'] }))
  const wrapper = await open(); await clickText(wrapper, '删除'); expect(notice.infoMessage).toHaveBeenCalledWith('这张旧图片缺少删除ID，暂时只能预览')
  await wrapper.findAll('.work_props_box input')[1].setValue('修正'); await clickText(wrapper, '保存修改')
  expect(http.delete).not.toHaveBeenCalled(); expect(notice.successMessage).toHaveBeenCalledWith('作品修改已保存，请返回“审核未通过”列表点击再次提审')
})
it('编辑缺失身份、ID和加载异常均不能出现可保存表单', async () => {
  localStorage.clear(); const guest = await open(); expect(notice.errorMessage).toHaveBeenCalledWith('未登录，无法编辑作品'); expect(guest.find('.save_bar').exists()).toBe(false)
  localStorage.setItem('token', 'Token fixture'); navigation.route.query.work_id = ''; await open(); expect(notice.errorMessage).toHaveBeenCalledWith('作品ID未找到')
  navigation.route.query.work_id = 'w'; http.get.mockRejectedValueOnce(new Error('网络')); const failed = await open(); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误'); expect(failed.find('.save_bar').exists()).toBe(false)
  http.get.mockResolvedValueOnce({ data: { code: 403, msg: '无权编辑' } }); await open(); expect(notice.infoMessage).toHaveBeenCalledWith('无权编辑')
})
it('学生搜索验证空串、业务失败及网络失败，裁剪失败不产生封面上传', async () => {
  const wrapper = await open(); await clickText(wrapper, '搜索'); expect(notice.errorMessage).toHaveBeenCalledWith('请正确输入学生名字！')
  await wrapper.get('input[placeholder="学生的中文或英文名"]').setValue('名字'); http.mockResolvedValueOnce({ data: { code: 1, msg: '搜索失败' } }); await clickText(wrapper, '搜索'); expect(notice.infoMessage).toHaveBeenCalledWith('搜索失败')
  http.mockRejectedValueOnce(new Error('断网')); await clickText(wrapper, '搜索'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
  await files(wrapper, 0); crop.canvas.mockReturnValueOnce({ toBlob: (cb: (blob: null) => void) => cb(null) }); await clickText(wrapper, '确认使用'); await clickText(wrapper, '保存修改')
  expect(notice.errorMessage).toHaveBeenCalledWith('动态裁剪失败'); expect(http.put).not.toHaveBeenCalled()
})
it('投稿按登录、路径、封面、学生、标题与来源依次验证', async () => {
  const wrapper = await open(false); useUserPinia().userBasic = null; await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('您尚未登录！')
  useUserPinia().userBasic = { uuid: 'u', username: 'wrong' } as UserBasicImpl; await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('登录信息与路径不匹配！')
  useUserPinia().userBasic = { uuid: 'u', username: 'member' } as UserBasicImpl; await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('未设置封面图！')
  await files(wrapper, 0); await clickText(wrapper, '上传作品文章'); expect(http).not.toHaveBeenCalledWith('/api/work/upload', expect.anything())
  await clickText(wrapper, '确认使用'); await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('未选择作品归属的学生！')
  await selectStudent(wrapper); await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('未设置标题！')
  await wrapper.findAll('.work_props_box input')[1].setValue('作品'); await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('[收录]类型需要说明来源作者！')
})
it('投稿提交裁剪封面、图集、分类与展示意图，成功明确等待审核', async () => {
  const wrapper = await open(false); await readyUpload(wrapper); await clickText(wrapper, '插画'); await files(wrapper, 1, ['one.png', 'two.png'])
  await wrapper.get('.test-editor').setValue('<p>投稿正文</p>'); const groups = wrapper.findAllComponents({ name: 'ElRadioGroup' }); groups[1].vm.$emit('update:modelValue', 'PRIVATE'); await flushPromises()
  await clickText(wrapper, '上传作品文章'); const options = http.mock.calls.find(call => call[0] === '/api/work/upload')![1]
  expect(options.method).toBe('POST'); expect(options.headers.Authorization).toBe('Token fixture')
  const form = options.data as FormData; expect(form.get('category_id')).toBe('cat'); expect(form.getAll('files')).toHaveLength(2); expect(form.get('cover_image')).toBeInstanceOf(File)
  expect(JSON.parse(String(form.get('form')))).toMatchObject({ title: '新作品', student: 's2', status: 'PRIVATE', content: '<p>投稿正文</p>' })
  expect(notice.successMessage).toHaveBeenCalledWith('作品已提交，正在等待审核')
})
it('原创无需来源作者，切换类型仍绑定当前投稿者，服务失败不报成功', async () => {
  const wrapper = await open(false); await readyUpload(wrapper); const group = wrapper.findComponent({ name: 'ElRadioGroup' })
  group.vm.$emit('update:modelValue', true); await flushPromises(); expect(wrapper.find('input[placeholder="请输入该作品的作者在其他主流平台的昵称"]').exists()).toBe(false)
  http.mockResolvedValueOnce({ data: { code: 1, msg: '失败：Error', data: '官方账号未配置' } }); await clickText(wrapper, '上传作品文章'); expect(notice.infoMessage).toHaveBeenCalledWith('官方账号未配置')
  group.vm.$emit('update:modelValue', false); await flushPromises(); http.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '上传作品文章'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
  const form = http.mock.calls.find(call => call[0] === '/api/work/upload')![1].data as FormData; expect(JSON.parse(String(form.get('form'))).author).toBe('u')
})
it('投稿分类与裁剪失败提供反馈，重新选图销毁旧裁剪器', async () => {
  http.get.mockResolvedValueOnce({ data: { code: 1 } }); const wrapper = await open(false); expect(notice.errorMessage).toHaveBeenCalledWith('获取所有分类信息失败！网络问题？请刷新界面！')
  await clickText(wrapper, '确认使用'); expect(notice.errorMessage).toHaveBeenCalledWith('图片加载失败')
  await files(wrapper, 0); await files(wrapper, 0); expect(crop.destroy).toHaveBeenCalled()
  crop.canvas.mockReturnValueOnce({ toBlob: (cb: (blob: null) => void) => cb(null) }); await clickText(wrapper, '确认使用'); expect(notice.errorMessage).toHaveBeenCalledWith('动态裁剪失败')
  http.get.mockRejectedValueOnce(new Error('离线')); await open(false); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
})
