// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { type Component } from 'vue'
import MbContainerComp from '../src/components/MessageBoard/MbContainerComp.vue'
import HomeMessageComp from '../src/components/Home/HomeMessageComp.vue'
import FeedbackContainerComp from '../src/components/Feedback/FeedbackContainerComp.vue'
import AccountHeaderComp from '../src/components/Account/AccountHeaderComp.vue'
import AccountUserCardComp from '../src/components/Account/AccountUserCardComp.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl, UserArchiveImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => Object.assign(vi.fn(), { get: vi.fn(), post: vi.fn() }))
const api = vi.hoisted(() => ({ fetchBoardPage: vi.fn(), postBoardMessage: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), errorCatchMessage: vi.fn(), infoMessage: vi.fn(), successMessage: vi.fn() }))
const navigation = vi.hoisted(() => ({ route: { query: { username: 'member' } }, push: vi.fn(), go: vi.fn() }))
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/messageBoardApi', () => api)
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => navigation }))
enableAutoUnmount(afterEach)
const ok = (data: unknown) => ({ data: { code: 0, msg: '成功', data } })
const messages = [{ id: 'm1', author: 'u', content: '留言一' }, { id: 'm2', author: 'u', content: '留言二' }]
beforeEach(() => {
  vi.resetAllMocks(); setActivePinia(createPinia()); localStorage.clear(); localStorage.setItem('token', 'Token fixture'); navigation.route.query.username = 'member'
  useUserPinia().userBasic = { uuid: 'u', username: 'member', email: 'u@example.test' } as UserBasicImpl
  useUserPinia().userArchive = { uuid: 'u', nickname: '本人的昵称' } as UserArchiveImpl
  http.mockImplementation(async (url: string | object) => ok(typeof url === 'string' ? { uuid: 'u', nickname: '留言作者', avatar: '/avatar' } : messages))
  http.get.mockResolvedValue(ok({ uuid: 'target', nickname: '公开的用户', gender: 1, age: 18 })); http.post.mockResolvedValue(ok('已收到'))
  api.fetchBoardPage.mockResolvedValue({ list: messages, total: 3 }); api.postBoardMessage.mockResolvedValue(true)
})
async function open(component: Component) { const wrapper = mount(component, { global: { stubs: uiStubs } }); await flushPromises(); return wrapper }
it('留言分页去重查询用户资料，追加失败保留页码，发布后刷新第一页', async () => {
  const wrapper = await open(MbContainerComp); expect(http).toHaveBeenCalledTimes(1); expect(wrapper.text()).toContain('留言作者')
  api.fetchBoardPage.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '加载更多留言'); expect(notice.errorCatchMessage).toHaveBeenCalled(); expect(wrapper.findAll('.message_item')).toHaveLength(2)
  api.fetchBoardPage.mockResolvedValueOnce({ list: [{ id: 'm3', author: 'other', content: '旧留言' }], total: 3 }); await clickText(wrapper, '加载更多留言'); expect(api.fetchBoardPage).toHaveBeenLastCalledWith(2); expect(wrapper.text()).toContain('旧留言')
  await wrapper.get('input[placeholder="30字以内"]').setValue('新留言'); await wrapper.get('.input_bar .button').trigger('click'); await flushPromises()
  expect(api.postBoardMessage).toHaveBeenCalledWith('新留言', 'Token fixture'); expect(api.fetchBoardPage).toHaveBeenLastCalledWith(1); expect(notice.successMessage).toHaveBeenCalledWith('留言成功')
})
it('留言登录、空内容与失败场景均不误报成功', async () => {
  const wrapper = await open(MbContainerComp); localStorage.clear(); await wrapper.get('.input_bar .button').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('请先登录')
  localStorage.setItem('token', 'Token fixture'); await wrapper.get('.input_bar .button').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('留言不得为空')
  await wrapper.get('.input_bar input').setValue('内容'); api.postBoardMessage.mockRejectedValueOnce(new Error('拒绝')); await wrapper.get('.input_bar .button').trigger('click'); await flushPromises(); expect(notice.errorCatchMessage).toHaveBeenCalled(); expect(notice.successMessage).not.toHaveBeenCalled()
})
it('留言投诉使用被点击的目标与当前用户，空说明、业务失败和异常可见', async () => {
  const wrapper = await open(MbContainerComp); await wrapper.get('.message_item').trigger('click'); expect(wrapper.text()).toContain('目标内容ID：m1')
  await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('投诉内容不能为空！')
  await wrapper.get('textarea').setValue('不合适'); http.post.mockImplementationOnce(async (_url, payload) => { expect(payload).toMatchObject({ target_id: 'm1', author: 'u', type: 'MESSAGE_BOARD', content: '不合适' }); return ok('已收到') }); await clickText(wrapper, '投诉'); expect(notice.successMessage).toHaveBeenCalledWith('已收到')
  await wrapper.get('.message_item').trigger('click'); await wrapper.get('textarea').setValue('原因'); http.post.mockResolvedValueOnce({ data: { code: 1, msg: '失败' } }); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('失败')
  await wrapper.get('.message_item').trigger('click'); http.post.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
})
it('首页留言复用 POST 命令，成功清空输入并刷新，更多进入留言板', async () => {
  const wrapper = await open(HomeMessageComp); expect(wrapper.text()).toContain('留言一'); localStorage.clear(); await wrapper.get('#b').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('请先登录')
  localStorage.setItem('token', 'Token fixture'); await wrapper.get('#b').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('留言不得为空')
  await wrapper.get('#input').setValue('首页留言'); await wrapper.get('#b').trigger('click'); await flushPromises(); expect(api.postBoardMessage).toHaveBeenCalledWith('首页留言', 'Token fixture'); expect((wrapper.get('#input').element as HTMLInputElement).value).toBe('')
  await clickText(wrapper, '更多'); expect(navigation.push).toHaveBeenCalledWith({ name: 'MessageBoard' })
  await wrapper.get('#input').setValue('失败重试'); api.postBoardMessage.mockRejectedValueOnce(new Error('拒绝')); await wrapper.get('#b').trigger('click'); await flushPromises(); expect(notice.errorCatchMessage).toHaveBeenCalled()
})
it('留言读取和用户资料失败有兜底，不让整个界面消失', async () => {
  http.mockRejectedValueOnce(new Error('查询作者失败')); const board = await open(MbContainerComp); expect(board.text()).toContain('未知昵称')
  http.mockResolvedValueOnce({ data: { code: 1, msg: '列表失败' } }); await open(HomeMessageComp); expect(notice.errorMessage).toHaveBeenCalledWith('列表失败')
  http.mockRejectedValueOnce(new Error('离线')); await open(HomeMessageComp); expect(notice.errorCatchMessage).toHaveBeenCalled()
})
it('常规反馈使用本地枚举选择，游客可填写邮箱，业务错误正确反馈', async () => {
  useUserPinia().userBasic = null; const wrapper = await open(FeedbackContainerComp); await clickText(wrapper, '发送表单'); expect(notice.infoMessage).toHaveBeenCalledWith('投诉内容不得为空！')
  await wrapper.findAll('.item')[0].trigger('click'); await wrapper.get('input').setValue('guest@example.test'); await wrapper.get('textarea').setValue('缺陷说明')
  await clickText(wrapper, '发送表单'); expect(http.post.mock.lastCall![1]).toMatchObject({ type: 'BUG', email: 'guest@example.test', content: '缺陷说明', author: null })
  useUserPinia().userBasic = { uuid: 'u' } as UserBasicImpl; await wrapper.findAll('.item')[1].trigger('click'); http.post.mockResolvedValueOnce({ data: { code: 1, msg: '不可重复' } }); await clickText(wrapper, '发送表单'); expect(notice.errorMessage).toHaveBeenCalledWith('不可重复')
  expect(http.post.mock.lastCall![1]).toMatchObject({ type: 'SUGGESTION', author: 'u' }); http.post.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '发送表单'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
})
it('本人菜单导航携带正确身份上下文，登出清除令牌', async () => {
  const wrapper = await open(AccountHeaderComp); const menu = wrapper.findAll('.menu-item'); await menu[0].trigger('click'); expect(navigation.push).toHaveBeenLastCalledWith({ name: 'UpdateAccount' })
  await menu[1].trigger('click'); expect(navigation.push).toHaveBeenLastCalledWith({ name: 'UploadWork', query: { username: 'member' } }); await menu[2].trigger('click'); expect(navigation.push).toHaveBeenLastCalledWith({ name: 'Manage' })
  await menu[4].trigger('click'); expect(localStorage.getItem('token')).toBeNull(); expect(navigation.go).toHaveBeenCalledWith(0)
})
it('用户投诉先解析目标 UUID，不把用户名当目标关联字段', async () => {
  navigation.route.query.username = 'other'; const wrapper = await open(AccountHeaderComp); await wrapper.findAll('.menu-item')[3].trigger('click'); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('投诉内容不能为空！')
  await wrapper.get('textarea').setValue('说明'); http.post.mockImplementationOnce(async (_url, payload) => { expect(payload).toMatchObject({ type: 'USER', target_id: 'target', author: 'u' }); return ok('已收到') }); await clickText(wrapper, '投诉')
  expect(http.get).toHaveBeenCalledWith('/user/current_by_username', { params: { username: 'other' } }); expect(notice.successMessage).toHaveBeenCalledWith('已收到')
  await wrapper.findAll('.menu-item')[3].trigger('click'); await wrapper.get('textarea').setValue('说明'); http.get.mockResolvedValueOnce({ data: { code: 1, msg: '用户不存在' } }); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('用户不存在')
  http.post.mockResolvedValueOnce({ data: { code: 1, msg: '失败' } }); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('失败')
  await wrapper.findAll('.menu-item')[3].trigger('click'); http.get.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
})
it('公开个人资料游客可读，本人使用当前资料，缺失参数和失败不伪造用户', async () => {
  const mine = await open(AccountUserCardComp); expect(mine.text()).toContain('本人的昵称'); expect(http.get).not.toHaveBeenCalled()
  useUserPinia().userBasic = null; navigation.route.query.username = 'other'; const publicView = await open(AccountUserCardComp); expect(publicView.text()).toContain('公开的用户')
  http.get.mockResolvedValueOnce({ data: { code: 1, msg: '查无此人' } }); await open(AccountUserCardComp); expect(notice.infoMessage).toHaveBeenCalledWith('查无此人')
  http.get.mockRejectedValueOnce(new Error('离线')); await open(AccountUserCardComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
  navigation.route.query.username = ''; await open(AccountUserCardComp); expect(notice.errorMessage).toHaveBeenCalledWith('页面没有正确的数值！')
})
