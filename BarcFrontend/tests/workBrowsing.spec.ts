// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { reactive, type Component } from 'vue'
import WorkCategoryGeneralComp from '../src/components/Work/Category/WorkCategoryGeneralComp.vue'
import WorkCategorySearchBarComp from '../src/components/Work/Category/WorkCategorySearchBarComp.vue'
import WorkCategoryTypeComp from '../src/components/Work/Category/WorkCategoryTypeComp.vue'
import WorkGeneralComp from '../src/components/Work/WorkGeneralComp.vue'
import WorkSearchBarComp from '../src/components/Work/WorkSearchBarComp.vue'
import WorkTypeComp from '../src/components/Work/WorkTypeComp.vue'
import WorkNoQueryComp from '../src/components/Work/WorkNoQueryComp.vue'
import WorkView from '../src/views/WorkView.vue'
import AccountWorkListComp from '../src/components/Account/AccountWorkListComp.vue'
import { useWorkItemListPinia } from '../src/stores/WorkItemListPinia'
import { useAccountWorkItemPinia } from '../src/stores/AccountWorkItemListPinia'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), infoMessage: vi.fn() }))
const nav = vi.hoisted(() => ({ route: { name: 'WorkAll', query: {} as Record<string, string>, params: {} as Record<string, string> }, push: vi.fn(), go: vi.fn() }))
const api = vi.hoisted(() => ({ counts: vi.fn(), liked: vi.fn() }))
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRoute: () => nav.route, useRouter: () => nav }))
vi.mock('../src/utils/workCommentCountCache', () => ({ loadWorkCommentCounts: api.counts }))
vi.mock('../src/utils/accountLikeApi', () => ({ fetchLikedWorksByUsername: api.liked }))
enableAutoUnmount(afterEach)
const ok = (data: unknown) => ({ data: { code: 0, msg: '成功', data } })
const result = () => ({ list: [{ id: 'w', title: '作品', author: 'a', is_claim: true, author_nickname: '旧昵称', view_count: 20, like_count: 8 }], total: 30, page_num: 1, page_size: 24, total_page: 2 })
beforeEach(() => {
  vi.resetAllMocks(); setActivePinia(createPinia()); nav.route = reactive({ name: 'WorkAll', query: { keyword: '星野' }, params: { category_id: 'category' } })
  http.post.mockImplementation(async () => ok(result())); http.get.mockResolvedValue(ok({ nickname: '真实作者' })); api.counts.mockResolvedValue({ w: 11 }); api.liked.mockResolvedValue(result())
})
afterEach(() => vi.useRealTimers())
async function open(component: Component) { const wrapper = mount(component, { global: { stubs: { ...uiStubs, 'el-pagination': true } } }); await flushPromises(); return wrapper }
it.each([['WorkAll','keyword'], ['WorkSchool','school'], ['WorkClub','club'], ['WorkStudent','student']])('作品搜索 %s 仅使用对应参数并尊重页码', async (route, field) => {
  nav.route.name = route; nav.route.query.page_num = '2'; const store = useWorkItemListPinia(); await store.fetchWorkList('PUBLIC')
  expect(http.post).toHaveBeenCalledWith('/api/work/works_by_page', { page_num: 2, page_size: 24, params: { [field]: '星野' } }, { params: { status: 'PUBLIC' } })
  expect(store.workList).toHaveLength(1); expect(store.pageResult?.total).toBe(30)
})
it('列表路由、关键词和网络异常反馈正确', async () => {
  const store = useWorkItemListPinia(); nav.route.name = ''; await store.fetchWorkList('PUBLIC'); expect(notice.errorMessage).toHaveBeenCalledWith('路径异常 -> WorkList')
  nav.route.name = 'WorkAll'; nav.route.query = {}; await store.fetchWorkList('PUBLIC'); expect(notice.errorMessage).toHaveBeenCalledWith('参数异常 -> WorkList')
  nav.route.query.keyword = '星野'; http.post.mockRejectedValueOnce(new Error('断网')); await store.fetchWorkList('PUBLIC'); expect(notice.errorMessage).toHaveBeenCalledWith('获取作品信息时出现网络问题')
})
it('公开作品列表显示真实作者和批量评论数，页码导航保留搜索条件', async () => {
  const wrapper = await open(WorkGeneralComp); expect(wrapper.text()).toContain('真实作者'); expect(wrapper.get('.cover_z').text()).toContain('11'); expect(api.counts).toHaveBeenCalledWith(['w'])
  await wrapper.get('.item').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'WorkDetail', query: { work_id: 'w' } })
  const pagination = wrapper.findComponent('el-pagination-stub'); pagination.vm.$emit('current-change', 2); expect(nav.push).toHaveBeenLastCalledWith({ query: { keyword: '星野', page_num: 2 } })
  nav.route.query.page_num = '2'; pagination.vm.$emit('current-change', 1); expect(nav.push).toHaveBeenLastCalledWith({ query: { keyword: '星野' } })
})
it('分类列表共用统一分页，关键词变化与清空后重新取数', async () => {
  nav.route.query.page_num = '2'; const wrapper = await open(WorkCategoryGeneralComp)
  expect(http.post).toHaveBeenCalledWith('/api/work/works_by_page', { page_num: 2, page_size: 24, params: { keyword: '星野', category_id: 'category' } }, { params: { status: 'PUBLIC' } })
  expect(wrapper.text()).toContain('真实作者'); await wrapper.get('.item').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'WorkDetail', query: { work_id: 'w' } })
  wrapper.findComponent('el-pagination-stub').vm.$emit('current-change', 1); expect(nav.push).toHaveBeenLastCalledWith({ query: { keyword: '星野' } })
  wrapper.findComponent('el-pagination-stub').vm.$emit('current-change', 3); expect(nav.push).toHaveBeenLastCalledWith({ query: { keyword: '星野', page_num: 3 } })
  nav.route.query = {}; await flushPromises(); expect(http.post.mock.lastCall![1]).toMatchObject({ page_num: 1, params: { category_id: 'category' } })
  nav.route.query = { keyword: '白子' }; await flushPromises(); expect(http.post.mock.lastCall![1].params.keyword).toBe('白子')
})
it('分类与普通列表对资料缺失、查询失败保留明确兜底', async () => {
  http.get.mockResolvedValueOnce({ data: { code: 1 } }); const category = await open(WorkCategoryGeneralComp); expect(category.text()).toContain('is_claim')
  http.get.mockRejectedValueOnce(new Error('作者失败')); nav.route.query = { keyword: 'change' }; await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('网络异常！')
  http.post.mockResolvedValueOnce({ data: { code: 1, data: '分类不存在' } }); await open(WorkCategoryGeneralComp); expect(notice.infoMessage).toHaveBeenCalledWith('分类不存在')
  http.post.mockRejectedValueOnce(new Error('离线')); await open(WorkCategoryGeneralComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络异常！')
  http.get.mockRejectedValueOnce(new Error('作者失败')); const list = await open(WorkGeneralComp); expect(list.text()).toContain('is_claim')
  http.post.mockResolvedValueOnce(ok({ ...result(), list: [] })); await useWorkItemListPinia().fetchWorkList('PUBLIC'); await flushPromises(); expect(list.text()).toContain('暂无数据')
})
it('搜索和类型切换保留关键词且清除旧页码', async () => {
  const search = await open(WorkSearchBarComp); expect((search.get('input').element as HTMLInputElement).value).toBe('星野')
  await search.get('input').setValue('白子'); await clickText(search, '搜索'); expect(nav.push).toHaveBeenCalledWith({ name: 'WorkAll', query: { keyword: '白子' } })
  nav.route.query = { keyword: '更新', page_num: '2' }; await flushPromises(); expect(http.post.mock.lastCall![1].params.keyword).toBe('更新')
  const type = await open(WorkTypeComp); await type.findAll('.item')[1].trigger('click'); expect(nav.push).toHaveBeenLastCalledWith({ name: 'WorkSchool', query: { keyword: '更新' } })
  nav.route.query = {}; await type.findAll('.item')[2].trigger('click'); expect(nav.push).toHaveBeenLastCalledWith({ name: 'WorkClub' })
  await search.get('input').setValue(''); const count = nav.push.mock.calls.length; await clickText(search, '搜索'); expect(nav.push).toHaveBeenCalledTimes(count)
})
it('分类检索清空关键词与分类元数据失败均可处理', async () => {
  const search = await open(WorkCategorySearchBarComp); await search.get('input').setValue('白子'); await clickText(search, '搜索'); expect(nav.push).toHaveBeenLastCalledWith({ query: { keyword: '白子' } })
  await search.get('input').setValue(''); await clickText(search, '搜索'); expect(nav.push).toHaveBeenLastCalledWith({ query: {} })
  http.get.mockResolvedValueOnce(ok([{ id: 'c', name: '分类一' }])); expect((await open(WorkCategoryTypeComp)).text()).toContain('分类一')
  http.get.mockResolvedValueOnce({ data: { code: 1, msg: '分类失败' } }); await open(WorkCategoryTypeComp); expect(notice.infoMessage).toHaveBeenCalledWith('分类失败')
  http.get.mockRejectedValueOnce(new Error('离线')); await open(WorkCategoryTypeComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络异常！')
})
it('个人作品、收录和点赞使用不同筛选，分页组件导航正确', async () => {
  const store = useAccountWorkItemPinia(); nav.route.query = {}; await store.fetchWorks('member'); expect(http.post.mock.lastCall![1].params).toEqual({ author_username: 'member' })
  nav.route.query = { type: 'collections', page_num: '2' }; await store.fetchWorks('member'); expect(http.post.mock.lastCall![1].params).toEqual({ uploader_username: 'member' })
  nav.route.query.type = 'likes'; await store.fetchWorks('member'); expect(api.liked).toHaveBeenCalledWith('member', { page_num: 2, page_size: 15 })
  const wrapper = await open(AccountWorkListComp); expect(wrapper.text()).toContain('作品'); await wrapper.get('.item').trigger('click'); expect(nav.push).toHaveBeenLastCalledWith({ name: 'WorkDetail', query: { work_id: 'w' } })
  wrapper.findComponent('el-pagination-stub').vm.$emit('current-change', 1); expect(nav.push).toHaveBeenLastCalledWith({ query: { type: 'likes' } })
  wrapper.findComponent('el-pagination-stub').vm.$emit('current-change', 3); expect(nav.push).toHaveBeenLastCalledWith({ query: { type: 'likes', page_num: 3 } })
  api.liked.mockRejectedValueOnce(new Error('不可读取点赞')); await store.fetchWorks('member'); expect(notice.errorMessage).toHaveBeenCalledWith('不可读取点赞')
  nav.route.query.type = 'invalid'; await store.fetchWorks('member'); expect(notice.errorMessage).toHaveBeenCalledWith('客户端处理分类选择时出现异常问题！反馈点：AccountWorkItemError->2')
  nav.route.query.type = 'works'; http.post.mockResolvedValueOnce({ data: { code: 1, msg: '加载失败' } }); await store.fetchWorks('member'); expect(notice.infoMessage).toHaveBeenCalledWith('加载失败')
  http.post.mockRejectedValueOnce(new Error('离线')); await store.fetchWorks('member'); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
})
it('无关键词入口搜索导航及详情外壳随路由切换', async () => {
  vi.useFakeTimers(); nav.route.query = {}; const empty = await open(WorkNoQueryComp); await clickText(empty, '搜索'); expect(nav.push).not.toHaveBeenCalled()
  await empty.get('input').setValue('白子'); await clickText(empty, '搜索'); expect(nav.push).toHaveBeenCalledWith({ name: 'WorkAll', query: { keyword: '白子' } }); await vi.advanceTimersByTimeAsync(100)
  const wrapper = mount(WorkView, { global: { stubs: { HeaderComp: true, WorkSearchBarComp: true, WorkTypeComp: true, WorkNoQueryComp: true, RouterView: true } } }); await flushPromises(); expect(wrapper.find('.no_query').exists()).toBe(true)
  nav.route.name = 'WorkDetail'; await flushPromises(); expect(wrapper.find('.is_detail').exists()).toBe(true)
  nav.route.name = 'WorkAll'; await flushPromises(); expect(wrapper.find('.no_detail').exists()).toBe(true)
})
