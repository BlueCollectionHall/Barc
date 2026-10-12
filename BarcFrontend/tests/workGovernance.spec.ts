// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import WorkDetailComp from '../src/components/Work/WorkDetailComp.vue'
import ManageWorkComp from '../src/components/Manage/ManageWorkComp.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl, UserArchiveImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), infoMessage: vi.fn(), successMessage: vi.fn() }))
const navigation = vi.hoisted(() => ({ route: { query: { work_id: 'w' } }, push: vi.fn() }))
const api = vi.hoisted(() => ({ toggleWorkLike: vi.fn(), fetchMyClaims: vi.fn(), submitWorkClaim: vi.fn(), fetchCommentCountByWork: vi.fn(), resubmitRejectedWork: vi.fn(), updateOwnerWorkVisibility: vi.fn() }))
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRoute: () => navigation.route, useRouter: () => ({ push: navigation.push }) }))
vi.mock('../src/utils/workLikeApi', () => ({ toggleWorkLike: api.toggleWorkLike }))
vi.mock('../src/utils/commentApi', () => ({ fetchCommentCountByWork: api.fetchCommentCountByWork }))
vi.mock('../src/utils/workClaimApi', async importOriginal => ({ ...await importOriginal<object>(), fetchMyClaims: api.fetchMyClaims, submitWorkClaim: api.submitWorkClaim }))
vi.mock('../src/utils/manageWorkStatusActions', async importOriginal => ({ ...await importOriginal<object>(), resubmitRejectedWork: api.resubmitRejectedWork, updateOwnerWorkVisibility: api.updateOwnerWorkVisibility }))
enableAutoUnmount(afterEach)
const ok = (data: unknown) => ({ data: { code: 0, msg: '成功', data } })
const work = (status = 'PUBLIC', review_status = 'APPROVED') => ({ id: 'w', title: '作品标题', description: '', content: '<p>公开正文</p>', is_claim: false, author: 'official', uploader: 'u', author_nickname: '原作作者', student: 's', status, review_status, content_revision: 7, like_count: 5, view_count: 9, created_at: new Date('2026-01-01'), updated_at: new Date('2026-10-12') })
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear(); localStorage.setItem('token', 'Token fixture'); setActivePinia(createPinia()); navigation.route.query.work_id = 'w'
  useUserPinia().userBasic = { uuid: 'u', username: 'member', email: 'u@example.test' } as UserBasicImpl
  useUserPinia().userArchive = { uuid: 'u', nickname: '会员' } as UserArchiveImpl
  vi.spyOn(useUserPinia(), 'fetchUserInfo').mockResolvedValue(undefined)
  http.get.mockImplementation(async (url: string) => ok(url === '/api/work/only' ? work() : url.includes('images_by_work') ? ['/image'] : { nickname: '资料昵称' }))
  http.post.mockImplementation(async (url: string) => ok(url.includes('mine/page') ? { list: [work()], total: 21 } : '已收到反馈'))
  api.fetchMyClaims.mockResolvedValue({ list: [], total: 0 }); api.submitWorkClaim.mockResolvedValue(null); api.fetchCommentCountByWork.mockResolvedValue(25)
  api.toggleWorkLike.mockResolvedValue({ liked: true, like_count: 12 }); api.resubmitRejectedWork.mockResolvedValue(true); api.updateOwnerWorkVisibility.mockResolvedValue(true)
})
async function detail() {
  const wrapper = mount(WorkDetailComp, { global: { stubs: { ...uiStubs, WorkCommentSection: true, 'a-image': true } } }); await flushPromises(); return wrapper
}
async function manage() {
  const wrapper = mount(ManageWorkComp, { global: { stubs: { ...uiStubs, 'el-select': true, 'el-option': true, 'el-pagination': true } } }); await flushPromises(); return wrapper
}
it('详情初次仅加载轻量计数，切换图集和评论弹窗按需读取', async () => {
  const wrapper = await detail(); expect(wrapper.text()).toContain('公开正文'); expect(wrapper.get('.comment_box').text()).toBe('25')
  expect(http.get).toHaveBeenCalledWith('/api/work/only', { params: { work_id: 'w' }, headers: { Authorization: 'Token fixture' } })
  expect(http.get).not.toHaveBeenCalledWith('/api/work/image/images_by_work', expect.anything())
  await wrapper.findAll('.view_items .item')[1].trigger('click'); await flushPromises(); expect(wrapper.find('a-image-stub').attributes('src')).toBe('/image')
  await wrapper.get('.comment_box').trigger('click'); const comments = wrapper.findComponent({ name: 'WorkCommentSection' }); expect(comments.props('visible')).toBe(true)
  comments.vm.$emit('commentCount', 28); await flushPromises(); expect(wrapper.get('.comment_box').text()).toBe('28')
})
it('作品正文在展示前再次移除接口响应中的恶意 HTML', async () => {
  http.get.mockResolvedValueOnce(ok({ ...work(), content: '<p onclick="alert(1)">保留正文<img src="https://image.example.test/a.png" onerror="alert(1)"><script>alert(1)</script></p>' }))
  const wrapper = await detail()
  expect(wrapper.get('.content').text()).toBe('保留正文')
  expect(wrapper.get('.content').find('script,[onclick],[onerror]').exists()).toBe(false)
  expect(wrapper.get('.content img').attributes('src')).toBe('https://image.example.test/a.png')
})
it('点赞数量服从服务端且请求未完成时拦截重复点击，退出后不使用旧身份', async () => {
  const wrapper = await detail(); let finish!: (data: unknown) => void; api.toggleWorkLike.mockReturnValueOnce(new Promise(resolve => { finish = resolve }))
  await wrapper.get('.like_box').trigger('click'); await wrapper.get('.like_box').trigger('click'); expect(api.toggleWorkLike).toHaveBeenCalledTimes(1)
  finish({ liked: true, like_count: 40 }); await flushPromises(); expect(wrapper.get('.like_box').text()).toBe('40'); expect(wrapper.get('.like_box').classes()).toContain('liked')
  api.toggleWorkLike.mockRejectedValueOnce(new Error('作品已不可见')); await wrapper.get('.like_box').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('作品已不可见')
  localStorage.clear(); await wrapper.get('.like_box').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('请先登录'); expect(api.toggleWorkLike).toHaveBeenCalledTimes(2)
})
it('投诉要求说明，成功关闭且失败提示与结果一致', async () => {
  const wrapper = await detail(); await wrapper.get('.feedback_box').trigger('click'); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('反馈内容不能为空')
  await wrapper.get('textarea').setValue('不当内容'); http.post.mockResolvedValueOnce({ data: { code: 1, msg: '不能重复举报' } }); await clickText(wrapper, '投诉')
  expect(notice.errorMessage).toHaveBeenCalledWith('不能重复举报'); expect(wrapper.find('.test-modal').exists()).toBe(true)
  http.post.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '投诉'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
  await clickText(wrapper, '投诉'); expect(notice.successMessage).toHaveBeenCalledWith('已收到反馈'); expect(wrapper.find('.test-modal').exists()).toBe(false)
  // 请求中作者来自当前会话；成功清表单后不能再读取同一响应式对象来检验提交快照。
  expect(http.post.mock.calls[0][0]).toBe('/feedback/upload')
})
it('本人认领进度与提交闭环，待审核申请不能再提交', async () => {
  const wrapper = await detail(); await wrapper.get('.claim_box').trigger('click'); await flushPromises(); expect(api.fetchMyClaims).toHaveBeenCalledWith(1, 'w')
  await clickText(wrapper, '提交申请'); expect(notice.infoMessage).toHaveBeenCalledWith('请填写认领说明')
  await wrapper.get('textarea').setValue('  原作地址与署名  '); api.fetchMyClaims.mockResolvedValueOnce({ list: [{ status: 'PENDING', result_reason: null }], total: 1 })
  await clickText(wrapper, '提交申请'); expect(api.submitWorkClaim).toHaveBeenCalledWith('w', '原作地址与署名'); expect(wrapper.text()).toContain('待审核')
  await clickText(wrapper, '提交申请'); expect(api.submitWorkClaim).toHaveBeenCalledTimes(1); expect(notice.infoMessage).toHaveBeenCalledWith('已有待审核申请')
})
it('认领查询和提交失败可重试，游客必须先登录', async () => {
  const wrapper = await detail(); localStorage.clear(); await wrapper.get('.claim_box').trigger('click'); expect(notice.infoMessage).toHaveBeenCalledWith('请先登录后申请认领')
  localStorage.setItem('token', 'Token fixture'); api.fetchMyClaims.mockRejectedValueOnce(new Error('查询失败')); await wrapper.get('.claim_box').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('查询失败')
  await wrapper.get('textarea').setValue('认领说明'); api.submitWorkClaim.mockRejectedValueOnce(new Error('不可认领')); await clickText(wrapper, '提交申请'); expect(notice.errorMessage).toHaveBeenCalledWith('不可认领')
})
it('详情无 ID、业务空结果、计数失败和网络异常分别反馈', async () => {
  navigation.route.query.work_id = ''; await detail(); expect(notice.errorMessage).toHaveBeenCalledWith('作品ID未找到！')
  navigation.route.query.work_id = 'w'; http.get.mockResolvedValueOnce(ok(null)); api.fetchCommentCountByWork.mockRejectedValueOnce(new Error('计数失败')); await detail()
  expect(notice.errorMessage).toHaveBeenCalledWith('获取作品信息失败'); expect(notice.errorMessage).toHaveBeenCalledWith('加载评论数失败')
  http.get.mockResolvedValueOnce({ data: { code: 404, data: '作品不存在' } }); await detail(); expect(notice.infoMessage).toHaveBeenCalledWith('作品不存在')
  http.get.mockRejectedValueOnce(new Error('离线')); await detail(); expect(notice.errorMessage).toHaveBeenCalledWith('网络异常')
})
it('本人作品只查询已通过公开项，分页、搜索和状态切换使用服务端过滤', async () => {
  const wrapper = await manage(); expect(http.post).toHaveBeenCalledWith('/api/work/mine/page', { page_num: 1, page_size: 20, params: { status: 'PUBLIC', review_status: 'APPROVED' } }, expect.anything())
  await wrapper.get('input').setValue('  星野  '); await clickText(wrapper, '搜索'); expect(http.post.mock.lastCall![1].params.keyword).toBe('星野')
  await clickText(wrapper, '清空'); expect(http.post.mock.lastCall![1].params.keyword).toBeUndefined()
  const pagination = wrapper.findComponent('el-pagination-stub'); pagination.vm.$emit('update:current-page', 2); pagination.vm.$emit('current-change', 2); await flushPromises(); expect(http.post.mock.lastCall![1].page_num).toBe(2)
  await clickText(wrapper, '审核中'); expect(http.post.mock.lastCall![1]).toEqual({ page_num: 1, page_size: 20, params: { review_status: 'PENDING' } })
  await clickText(wrapper, '编辑'); expect(navigation.push).toHaveBeenCalledWith({ name: 'ManageWorkEdit', query: { work_id: 'w' } })
})
it('公开私有操作调用独立命令且失败不改变列表，缺失令牌阻止操作', async () => {
  const wrapper = await manage(); await wrapper.get('.secondary_action_button').trigger('click'); await flushPromises(); expect(api.updateOwnerWorkVisibility).toHaveBeenCalledWith('w', 'PRIVATE', 'Token fixture')
  expect(notice.successMessage).toHaveBeenCalledWith('作品已设为私有'); api.updateOwnerWorkVisibility.mockRejectedValueOnce(new Error('操作失败')); await wrapper.get('.secondary_action_button').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('操作失败')
  localStorage.clear(); await wrapper.get('.secondary_action_button').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('请先登录')
})
it('拒绝作品显示原因并再次提审，失败保留操作机会', async () => {
  http.post.mockResolvedValue(ok({ list: [{ ...work('PRIVATE', 'REJECTED'), review_reason: '请补充来源' }], total: 1 })); const wrapper = await manage(); expect(wrapper.text()).toContain('请补充来源')
  await clickText(wrapper, '再次提审'); expect(api.resubmitRejectedWork).toHaveBeenCalledWith('w', 'Token fixture'); expect(notice.successMessage).toHaveBeenCalledWith('已再次提交审核，请在“审核中”查看进度')
  api.resubmitRejectedWork.mockRejectedValueOnce(new Error('版本错误')); await clickText(wrapper, '再次提审'); expect(notice.errorMessage).toHaveBeenCalledWith('版本错误')
  localStorage.clear(); await clickText(wrapper, '再次提审'); expect(api.resubmitRejectedWork).toHaveBeenCalledTimes(2)
})
it('下架作品只能提交申诉，说明为空不提交，失败保留表单', async () => {
  http.post.mockImplementation(async (url: string) => ok(url.includes('mine/page') ? { list: [work('OFF')], total: 1 } : '申诉已收到'))
  const wrapper = await manage(); await wrapper.get('.secondary_action_button').trigger('click'); await clickText(wrapper, '提交申诉'); expect(notice.errorMessage).toHaveBeenCalledWith('请填写申诉说明')
  await wrapper.get('textarea').setValue('  已补充来源  '); http.post.mockResolvedValueOnce({ data: { code: 1, msg: '重复申诉' } }); await clickText(wrapper, '提交申诉'); expect(notice.infoMessage).toHaveBeenCalledWith('重复申诉')
  http.post.mockRejectedValueOnce(new Error('离线')); await clickText(wrapper, '提交申诉'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
  await clickText(wrapper, '提交申诉'); expect(http.post.mock.lastCall![1]).toMatchObject({ type: 'WORK', target_id: 'w', author: 'u', email: 'u@example.test' })
  expect(wrapper.find('.test-modal').exists()).toBe(false); expect(api.updateOwnerWorkVisibility).not.toHaveBeenCalled()
})
it('未登录不取本人列表，列表错误显示明确反馈', async () => {
  localStorage.clear(); await manage(); expect(http.post).not.toHaveBeenCalled()
  localStorage.setItem('token', 'Token fixture'); http.post.mockResolvedValueOnce({ data: { code: 1, msg: '无权限' } }); await manage(); expect(notice.infoMessage).toHaveBeenCalledWith('无权限')
  http.post.mockRejectedValueOnce(new Error('离线')); await manage(); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
})
