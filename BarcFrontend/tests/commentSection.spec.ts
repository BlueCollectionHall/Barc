// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import WorkCommentSection from '../src/components/Work/WorkCommentSection.vue'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserBasicImpl } from '../src/interfaces/UserImpl'
import { clickText, uiStubs } from './uiHarness'

const api = vi.hoisted(() => ({ fetchCommentsPage: vi.fn(), fetchRepliesPage: vi.fn(), fetchCommentCountByWork: vi.fn(), uploadComment: vi.fn(), uploadReply: vi.fn(), deleteComment: vi.fn(), deleteReply: vi.fn(), reportComment: vi.fn(), fetchUserArchive: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn(), successMessage: vi.fn() }))
vi.mock('../src/utils/commentApi', () => api)
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('../src/utils/https', () => ({ baseHttp: vi.fn() }))
enableAutoUnmount(afterEach)
const reply = (id: string) => ({ id, parent_id: 'c', author: 'u', reply_user: 'target', content: '回复内容', created_at: '2026-10-12T10:00:00' })
const comment = { id: 'c', author: 'u', work_id: 'w', content: '评论正文', reply_count: 7, created_at: '2026-10-12T10:00:00', replies: [1,2,3,4,5].map(n => reply(String(n))) }
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear(); setActivePinia(createPinia())
  api.fetchCommentsPage.mockResolvedValue({ list: [comment], total: 2 })
  api.fetchRepliesPage.mockResolvedValue({ list: [reply('6'), reply('7')], total: 7 })
  api.fetchCommentCountByWork.mockResolvedValue(42); api.fetchUserArchive.mockResolvedValue({ nickname: '测试昵称超过十个字的用户', avatar: null })
  api.uploadComment.mockResolvedValue(true); api.uploadReply.mockResolvedValue(true); api.deleteComment.mockResolvedValue(true); api.deleteReply.mockResolvedValue(true); api.reportComment.mockResolvedValue(true)
})
async function open(logged = true) {
  if (logged) { localStorage.setItem('token', 'jwt'); useUserPinia().userBasic = { uuid: 'u', email: 'u@example.test' } as UserBasicImpl }
  const wrapper = mount(WorkCommentSection, { props: { visible: true, workId: 'w' }, global: { stubs: uiStubs } }); await flushPromises(); return wrapper
}
it('初始打开显示服务端总数，主评和回复都能继续分页', async () => {
  const wrapper = await open(); expect(wrapper.text()).toContain('评论区 (42)'); expect(wrapper.emitted('commentCount')).toContainEqual([42])
  await clickText(wrapper, '加载更多回复'); expect(api.fetchRepliesPage).toHaveBeenCalledWith('c', 2); expect(wrapper.findAll('.reply-item')).toHaveLength(7)
  api.fetchCommentsPage.mockResolvedValueOnce({ total: 2, list: [{ ...comment, id: 'c2', replies: [], reply_count: 0 }] })
  await wrapper.get('.load-more').trigger('click'); await flushPromises(); expect(api.fetchCommentsPage).toHaveBeenLastCalledWith('w', 2)
  expect(wrapper.findAll('.comment-item')).toHaveLength(2); expect(wrapper.text()).toContain('没有更多评论了')
  await wrapper.get('.comment-close').trigger('click'); expect(wrapper.emitted('update:visible')).toContainEqual([false])
})
it('新评论、回复主评和回复子评保留正确目标，取消回复恢复新评论', async () => {
  const wrapper = await open()
  await wrapper.get('textarea[placeholder="写下你的评论..."]').setValue('  新评论  '); await wrapper.get('.send-btn').trigger('click'); await flushPromises()
  expect(api.uploadComment).toHaveBeenCalledWith('w', '新评论', 'jwt'); expect(notice.successMessage).toHaveBeenCalledWith('评论成功')
  await clickText(wrapper, '回复'); expect(wrapper.text()).toContain('回复 @')
  await wrapper.get('textarea').setValue('给主评回复'); await wrapper.get('.send-btn').trigger('click'); await flushPromises(); expect(api.uploadReply).toHaveBeenLastCalledWith('c', 'u', '给主评回复', 'jwt')
  await wrapper.get('.reply-item .action-btn').trigger('click'); await flushPromises()
  await wrapper.get('textarea').setValue('给回复再回复'); await wrapper.get('.send-btn').trigger('click'); await flushPromises(); expect(api.uploadReply).toHaveBeenLastCalledWith('c', 'u', '给回复再回复', 'jwt')
  await clickText(wrapper, '回复'); await wrapper.get('.cancel-reply').trigger('click'); expect(wrapper.find('.reply-tag').exists()).toBe(false)
})
it('未登录及退出后的回复、发布均不使用旧令牌', async () => {
  const wrapper = await open(); localStorage.removeItem('token')
  await clickText(wrapper, '回复'); expect(notice.errorMessage).toHaveBeenCalledWith('请先登录后再回复')
  await wrapper.get('textarea').setValue('评论'); await wrapper.get('.send-btn').trigger('click'); await flushPromises()
  expect(notice.errorMessage).toHaveBeenCalledWith('请先登录后再评论'); expect(api.uploadComment).not.toHaveBeenCalled()
  await clickText(wrapper, '删除'); expect(api.deleteComment).not.toHaveBeenCalled()
})
it('作者删除主评论和回复后刷新，失败不报成功', async () => {
  const wrapper = await open(); await clickText(wrapper, '删除'); expect(api.deleteComment).toHaveBeenCalledWith('c', 'jwt')
  await wrapper.get('.reply-item .danger').trigger('click'); await flushPromises(); expect(api.deleteReply).toHaveBeenCalledWith('1', 'jwt')
  api.deleteComment.mockRejectedValueOnce(new Error('失败')); await clickText(wrapper, '删除'); expect(notice.errorMessage).toHaveBeenCalledWith('删除失败')
  api.deleteReply.mockRejectedValueOnce(new Error('失败')); await wrapper.get('.reply-item .danger').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('删除失败')
})
it('举报验证原因、记录目标并显示成功或失败', async () => {
  const wrapper = await open(false); await clickText(wrapper, '举报'); await clickText(wrapper, '提交举报'); expect(notice.errorMessage).toHaveBeenCalledWith('请填写举报原因')
  await wrapper.get('textarea[placeholder="请描述举报原因..."]').setValue('  举报说明  ')
  await clickText(wrapper, '提交举报'); expect(api.reportComment).toHaveBeenCalledWith(expect.objectContaining({ type: 'COMMENT', target_id: 'c', content: '举报说明', author: null }))
  expect(notice.successMessage).toHaveBeenCalledWith('举报已提交')
  await clickText(wrapper, '举报'); await wrapper.get('textarea[placeholder="请描述举报原因..."]').setValue('原因'); api.reportComment.mockRejectedValueOnce(new Error('失败')); await clickText(wrapper, '提交举报')
  expect(notice.errorMessage).toHaveBeenCalledWith('举报失败')
})
it('获取失败保留已有数据且不推进页码，重试同一页', async () => {
  api.fetchUserArchive.mockResolvedValue(null); const wrapper = await open(); expect(wrapper.text()).toContain('未知用户')
  api.fetchRepliesPage.mockRejectedValueOnce(new Error('失败')); await clickText(wrapper, '加载更多回复'); expect(notice.errorMessage).toHaveBeenCalledWith('加载回复失败')
  api.fetchCommentsPage.mockRejectedValueOnce(new Error('失败')); await wrapper.get('.load-more').trigger('click'); await flushPromises()
  expect(wrapper.findAll('.comment-item')).toHaveLength(1); expect(notice.errorMessage).toHaveBeenCalledWith('加载评论失败')
  api.fetchCommentsPage.mockResolvedValueOnce({ list: [], total: 1 }); await wrapper.get('.comment-list').trigger('scroll'); await flushPromises(); expect(api.fetchCommentsPage).toHaveBeenLastCalledWith('w', 2)
  api.uploadComment.mockRejectedValueOnce(new Error('失败')); await wrapper.get('textarea').setValue('新评论'); await wrapper.get('.send-btn').trigger('click'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('操作失败')
})
it('切换作品后晚到的旧响应不能覆盖新作品评论', async () => {
  let finishOld!: (value: unknown) => void
  api.fetchCommentsPage.mockReturnValueOnce(new Promise(resolve => { finishOld = resolve }))
  const wrapper = mount(WorkCommentSection, { props: { visible: true, workId: 'old' }, global: { stubs: uiStubs } })
  await wrapper.setProps({ workId: 'new' }); await flushPromises(); expect(wrapper.text()).toContain('评论正文')
  finishOld({ total: 1, list: [{ ...comment, content: '不应显示的旧内容' }] }); await flushPromises()
  expect(wrapper.text()).not.toContain('不应显示的旧内容')
  await wrapper.setProps({ visible: false }); await wrapper.setProps({ visible: true, workId: '' }); expect(api.fetchCommentsPage).toHaveBeenCalledTimes(2)
})
