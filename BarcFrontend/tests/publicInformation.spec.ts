// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Component } from 'vue'
import StudentCardComp from '../src/components/Student/StudentCardComp.vue'
import StudentContainerComp from '../src/components/Student/StudentContainerComp.vue'
import StudentBgComp from '../src/components/Student/StudentBgComp.vue'
import NoticeListComp from '../src/components/Notice/NoticeListComp.vue'
import NoticeDetailComp from '../src/components/Notice/NoticeDetailComp.vue'
import HomeNoticeComp from '../src/components/Home/HomeNoticeComp.vue'
import HomeUpdatedComp from '../src/components/Home/HomeUpdatedComp.vue'
import HomeSearchComp from '../src/components/Home/HomeSearchComp.vue'
import HomeTimeComp from '../src/components/Home/HomeTimeComp.vue'
import HeaderComp from '../src/components/HeaderFooter/HeaderComp.vue'
import FooterComp from '../src/components/HeaderFooter/FooterComp.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl, UserArchiveImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const http = vi.hoisted(() => Object.assign(vi.fn(), { get: vi.fn(), post: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), infoMessage: vi.fn() }))
const nav = vi.hoisted(() => ({ route: { query: { student_id: 'student', notice_id: 'notice' } }, push: vi.fn() }))
const counts = vi.hoisted(() => vi.fn())
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRoute: () => nav.route, useRouter: () => nav }))
vi.mock('../src/utils/workCommentCountCache', () => ({ loadWorkCommentCounts: counts }))
enableAutoUnmount(afterEach)
const ok = (data: unknown) => ({ data: { code: 0, msg: '成功', data } })
const news = { id: 'notice', title: '更新公告', author: 'author', content: '<p>公告正文</p>', created_at: new Date('2026-01-01'), updated_at: new Date('2026-10-01') }
const student = { id: 'student', cn_name: '白子', en_name: 'Shiroko', school: 'school', club: 'club', avatar_rectangle: '/avatar', body_image: '/body' }
beforeEach(() => {
  vi.resetAllMocks(); setActivePinia(createPinia()); nav.route.query = { student_id: 'student', notice_id: 'notice' }
  counts.mockResolvedValue({ w: 11 }); http.mockResolvedValue(ok([{ id: 'cat', name: '插画' }]))
  http.get.mockImplementation(async (url: string) => {
    if (url === '/api/student/student') return ok(student)
    if (url === '/api/school/school') return ok({ cn_name: '阿拜多斯', logo: '/school' })
    if (url === '/api/club/club') return ok({ cn_name: '对策委员会', logo: '/club' })
    if (url === '/notice/only') return ok(news)
    if (url === '/notice/latest') return ok([news])
    if (url === '/api/work/new') return ok([student])
    if (url === '/info/icp_num') return ok('测试备案')
    if (url === '/info/copyright') return ok('测试版权')
    return ok({ nickname: '公告发布者', avatar: '/user-avatar' })
  })
  http.post.mockImplementation(async (url: string) => ok({ list: url.includes('notice') ? [{ ...news }] : [{ id: 'w', title: '学生作品', like_count: 8, view_count: 2 }], total: 15, page_size: 5, total_page: 3 }))
})
afterEach(() => vi.useRealTimers())
async function open(component: Component) { const wrapper = mount(component, { global: { stubs: { ...uiStubs, 'el-pagination': true, HomeBgComp: true } } }); await flushPromises(); return wrapper }
it('学生卡片按实体 ID 读取关联学园部团，签名图片直接用于展示', async () => {
  const wrapper = await open(StudentCardComp); expect(wrapper.text()).toContain('白子'); expect(wrapper.text()).toContain('阿拜多斯'); expect(wrapper.text()).toContain('对策委员会')
  expect(http.get.mock.calls.map(call => call[0])).toEqual(['/api/student/student', '/api/school/school', '/api/club/club']); expect(wrapper.get('.avatar').attributes('src')).toBe('/avatar')
  const bg = await open(StudentBgComp); expect(bg.get('.body').attributes('src')).toBe('/body')
})
it.each([
  ['student', '学生不存在'], ['school', '学园不存在'], ['club', '部团不存在'],
])('学生关系读取 %s 失败提供明确反馈', async (entity, reason) => {
  const original = http.get.getMockImplementation()!
  http.get.mockImplementation(async (url: string) => url === `/api/${entity}/${entity}` ? { data: { code: 1, msg: reason } } : original(url))
  await open(StudentCardComp); expect(notice.infoMessage).toHaveBeenCalledWith(reason)
})
it('学生空结果、缺少 ID 和断网安全退化', async () => {
  http.get.mockResolvedValueOnce(ok(null)); await open(StudentCardComp); expect(notice.errorMessage).toHaveBeenCalledWith('赋值失败！')
  http.get.mockResolvedValueOnce(ok(student)).mockResolvedValueOnce(ok(null)); await open(StudentCardComp); expect(notice.errorMessage).toHaveBeenCalledTimes(2)
  http.get.mockRejectedValueOnce(new Error('离线')); await open(StudentCardComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
  http.get.mockResolvedValueOnce({ data: { code: 1, msg: '学生不存在' } }); await open(StudentBgComp); expect(notice.infoMessage).toHaveBeenCalledWith('学生不存在')
  http.get.mockRejectedValueOnce(new Error('离线')); await open(StudentBgComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误！')
  nav.route.query.student_id = ''; const previous = http.get.mock.calls.length; await open(StudentCardComp); await open(StudentBgComp); expect(http.get).toHaveBeenCalledTimes(previous)
})
it('学生作品使用准确 student_id 筛选并显示评论计数', async () => {
  const wrapper = await open(StudentContainerComp); expect(http.post).toHaveBeenCalledWith('/api/work/works_by_page', { page_num: 1, page_size: 12, params: { student_id: 'student' } }, { params: { status: 'PUBLIC' } })
  expect(wrapper.text()).toContain('学生作品'); expect(wrapper.get('.cover_z').text()).toContain('11'); await wrapper.get('.item').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'WorkDetail', query: { work_id: 'w' } })
  http.post.mockResolvedValueOnce({ data: { code: 1, data: '作品不可见' } }); await open(StudentContainerComp); expect(notice.infoMessage).toHaveBeenCalledWith('作品不可见')
  http.post.mockRejectedValueOnce(new Error('离线')); await open(StudentContainerComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络异常')
})
it('公告列表分页获取作者公开资料并导航详情', async () => {
  const wrapper = await open(NoticeListComp); expect(wrapper.text()).toContain('更新公告'); expect(wrapper.text()).toContain('公告发布者')
  await wrapper.get('.notice').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'NoticeDetail', query: { notice_id: 'notice' } })
  wrapper.findComponent('el-pagination-stub').vm.$emit('current-change', 2); await flushPromises(); expect(http.post.mock.lastCall).toEqual(['/notice/notices_by_page', { page_num: 2, page_size: 5 }])
  http.get.mockRejectedValueOnce(new Error('资料失败')); await open(NoticeListComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
  http.post.mockRejectedValueOnce(new Error('列表失败')); await open(NoticeListComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
})
it('公告详情与首页摘要显示作者正文，首页更多进入列表', async () => {
  const detail = await open(NoticeDetailComp); expect(detail.text()).toContain('公告正文'); expect(detail.text()).toContain('公告发布者')
  const home = await open(HomeNoticeComp); expect(home.text()).toContain('公告正文'); await clickText(home, '更多'); expect(nav.push).toHaveBeenCalledWith({ name: 'Notice' })
})
it('公告详情和首页摘要都净化接口中的历史危险富文本', async () => {
  const malicious = { ...news, content: '<p onclick="alert(1)">历史正文<iframe src="https://outside.example.test"></iframe><img src="https://image.example.test/a.png" onerror="alert(1)"></p>' }
  const original = http.get.getMockImplementation()!
  http.get.mockImplementation(async (url: string) => url === '/notice/only' ? ok(malicious) : url === '/notice/latest' ? ok([malicious]) : original(url))
  for (const component of [NoticeDetailComp, HomeNoticeComp]) {
    const wrapper = await open(component)
    expect(wrapper.get('.content').text()).toBe('历史正文')
    expect(wrapper.get('.content').find('iframe,[onclick],[onerror]').exists()).toBe(false)
  }
})
it('公告详情错误和作者错误区别处理，缺 ID 不发送查询', async () => {
  http.get.mockResolvedValueOnce({ data: { code: 1, msg: '已删除' } }); await open(NoticeDetailComp); expect(notice.errorMessage).toHaveBeenCalledWith('已删除')
  http.get.mockResolvedValueOnce(ok(news)).mockResolvedValueOnce({ data: { code: 1, data: '作者不存在' } }); await open(NoticeDetailComp); expect(notice.errorMessage).toHaveBeenCalledWith('作者不存在')
  http.get.mockResolvedValueOnce(ok(news)).mockRejectedValueOnce(new Error('作者离线')); await open(NoticeDetailComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
  http.get.mockRejectedValueOnce(new Error('详情离线')); await open(NoticeDetailComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络错误')
  nav.route.query.notice_id = ''; await open(NoticeDetailComp); expect(notice.errorMessage).toHaveBeenCalledWith('没有公告ID')
})
it('首页公告空列表和网络失败不影响更多导航', async () => {
  http.get.mockResolvedValueOnce(ok([])); expect((await open(HomeNoticeComp)).text()).toContain('暂无数据')
  http.get.mockRejectedValueOnce(new Error('详情离线')); await open(HomeNoticeComp); expect(notice.errorMessage).toHaveBeenCalledWith('公告组件出现异常')
  http.get.mockResolvedValueOnce(ok([news])).mockRejectedValueOnce(new Error('作者离线')); await open(HomeNoticeComp); expect(notice.errorMessage).toHaveBeenCalledWith('公告组件出现异常:USER')
})
it('最新学生卡片和综合搜索导向正确目标', async () => {
  const updated = await open(HomeUpdatedComp); expect(updated.text()).toContain('白子'); await updated.get('.student').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'Student', query: { student_id: 'student' } })
  http.get.mockResolvedValueOnce({ data: { code: 1, msg: '读取失败' } }); await open(HomeUpdatedComp); expect(notice.infoMessage).toHaveBeenCalledWith('读取失败')
  http.get.mockRejectedValueOnce(new Error('离线')); await open(HomeUpdatedComp); expect(notice.errorMessage).toHaveBeenCalledWith('网络出错！')
  const search = await open(HomeSearchComp); const previous = nav.push.mock.calls.length; await clickText(search, '搜索'); expect(nav.push).toHaveBeenCalledTimes(previous)
  await search.get('input').setValue('白子'); await clickText(search, '搜索'); expect(nav.push).toHaveBeenLastCalledWith({ name: 'WorkAll', query: { keyword: '白子' } })
})
it('页首游客显示登录，登录后头像导航本人；页脚加载系统信息并提供导航', async () => {
  const guest = await open(HeaderComp); expect(guest.text()).toContain('登录 | 注册'); expect(guest.text()).toContain('插画')
  useUserPinia().userBasic = { username: 'member' } as UserBasicImpl; useUserPinia().userArchive = { avatar: '/avatar' } as UserArchiveImpl; await flushPromises()
  await guest.get('img').trigger('click'); expect(nav.push).toHaveBeenCalledWith({ name: 'Account', query: { username: 'member' } })
  const footer = await open(FooterComp); expect(footer.text()).toContain('测试版权'); await clickText(footer, '投诉反馈'); expect(nav.push).toHaveBeenLastCalledWith({ name: 'Feedback' })
  http.get.mockResolvedValueOnce({ data: { code: 1 } }).mockResolvedValueOnce({ data: { code: 1 } }); await open(FooterComp); expect(notice.infoMessage).toHaveBeenCalledTimes(2)
  http.get.mockRejectedValueOnce(new Error('离线')).mockRejectedValueOnce(new Error('离线')); await open(FooterComp); expect(notice.errorMessage).toHaveBeenCalledTimes(2)
})
it('时间组件切换本地、中国和日本时区，卸载停止计时', async () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-12T12:00:00Z')); const wrapper = await open(HomeTimeComp)
  await vi.advanceTimersByTimeAsync(1000); expect(wrapper.get('.content').text()).toContain('本地时间')
  await clickText(wrapper, '中国时间'); await vi.advanceTimersByTimeAsync(1000); expect(wrapper.get('.content').text()).toBe('中国时间 2026年10月12日 20:00:02')
  await clickText(wrapper, '日本时间'); await vi.advanceTimersByTimeAsync(1000); expect(wrapper.get('.content').text()).toBe('日本时间 2026年10月12日 21:00:03')
  wrapper.unmount(); expect(vi.getTimerCount()).toBe(0)
})
