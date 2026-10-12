import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage } from 'element-plus'
import WorkCommentManage from '../src/modules/works/components/WorkCommentManage.vue'
import CommentComplaintsView from '../src/modules/works/views/CommentComplaintsView.vue'
import { useAuthStore } from '../src/app/stores/auth'
import { uiStubs, clickText } from './uiHarness'

const api = vi.hoisted(() => ({ getCommentsByWork: vi.fn(), getManagementReplies: vi.fn(), deleteWorkComment: vi.fn(), deleteWorkReply: vi.fn(), getUserArchive: vi.fn(), getCommentComplaints: vi.fn(), processCommentComplaint: vi.fn(), getCommentById: vi.fn() }))
vi.mock('../src/modules/works/api/commentManage', () => api)
vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessage: { success: vi.fn(), error: vi.fn() } }))
enableAutoUnmount(afterEach)
const reply = (id: string) => ({ id, parent_id: 'c', author: 'author', reply_user: 'target', content: '回复正文', created_at: '2026-10-12T10:00:00' })
const comment = () => ({ id: 'c', work_id: 'w', author: 'author', content: '主评论正文', created_at: '2026-10-12T10:00:00', reply_count: 7, replies: [1,2,3,4,5].map(n => reply(String(n))) })
const feedback = { id: 'f', target_id: 'c', author: 'author', content: '投诉正文', status: 'PENDING', created_at: '2026-10-12T10:00:00' }
beforeEach(() => {
  vi.clearAllMocks(); setActivePinia(createPinia())
  api.getCommentsByWork.mockResolvedValue({ list: [comment()], total: 12 })
  api.getManagementReplies.mockResolvedValue({ list: [reply('6'), reply('7')], total: 7 })
  api.getUserArchive.mockResolvedValue({ nickname: '测试用户昵称长于十个中文字符', avatar: 'https://example.test/avatar.png' })
  api.getCommentComplaints.mockResolvedValue({ list: [feedback], total: 25 })
  api.getCommentById.mockResolvedValue({ id: 'c', type: 'comment', content: '证据正文' })
  api.deleteWorkComment.mockResolvedValue('ok'); api.deleteWorkReply.mockResolvedValue('ok'); api.processCommentComplaint.mockResolvedValue('ok')
})
function mountComments(permission = 4) {
  const auth = useAuthStore()
  auth.userArchive = { uuid: 'manager', identity: 'MANAGER', permission } as NonNullable<typeof auth.userArchive>
  return mount(WorkCommentManage, { props: { workId: 'w' }, global: { stubs: uiStubs } })
}
it('受控分页显示真实总数并继续读取回复，二级管理员只读', async () => {
  const wrapper = mountComments(); await flushPromises()
  expect(wrapper.text()).toContain('主评论 (12)'); expect(wrapper.text()).toContain('回复 7')
  expect(wrapper.text()).not.toContain('删除评论')
  await clickText(wrapper, '更多回复')
  expect(api.getManagementReplies).toHaveBeenCalledWith('c', 2)
  expect(wrapper.findAll('.reply-item')).toHaveLength(7)
  expect(wrapper.text()).not.toContain('更多回复')
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('update:current-page', 2)
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
  await flushPromises(); expect(api.getCommentsByWork).toHaveBeenLastCalledWith('w', 2)
  await wrapper.setProps({ workId: 'new' }); await flushPromises()
  expect(api.getCommentsByWork).toHaveBeenLastCalledWith('new', 1)
})
it('一级管理员可以删除主评论与回复，失败会提示且不误报成功', async () => {
  const wrapper = mountComments(2); await flushPromises()
  await clickText(wrapper, '删除评论'); expect(api.deleteWorkComment).toHaveBeenCalledWith('c')
  await clickText(wrapper, '删除'); expect(api.deleteWorkReply).toHaveBeenCalledWith('1')
  api.deleteWorkReply.mockRejectedValueOnce(new Error('拒绝'))
  await clickText(wrapper, '删除'); expect(ElMessage.error).toHaveBeenCalledWith('删除失败')
  api.deleteWorkComment.mockRejectedValueOnce(new Error('拒绝'))
  await clickText(wrapper, '删除评论'); expect(ElMessage.error).toHaveBeenCalledWith('删除失败')
})
it('加载失败与空列表有明确反馈，回复失败保留已有内容', async () => {
  api.getUserArchive.mockResolvedValue(null)
  const wrapper = mountComments(16); await flushPromises()
  expect(wrapper.text()).toContain('未知用户')
  api.getManagementReplies.mockRejectedValueOnce(new Error('断网'))
  await clickText(wrapper, '更多回复'); expect(wrapper.findAll('.reply-item')).toHaveLength(5)
  expect(ElMessage.error).toHaveBeenCalledWith('回复加载失败')
  api.getCommentsByWork.mockRejectedValueOnce(new Error('断网'))
  await wrapper.setProps({ workId: 'other' }); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('评论加载失败')
  api.getCommentsByWork.mockResolvedValue({ list: [], total: 0 })
  await wrapper.setProps({ workId: 'empty' }); await flushPromises(); expect(wrapper.text()).toContain('暂无评论')
})
it.each(['comment', 'reply'])('删除投诉对象 %s 成功后才结案', async type => {
  api.getCommentById.mockResolvedValue({ id: 'c', type, content: '证据正文' })
  const wrapper = mount(CommentComplaintsView, { global: { stubs: uiStubs } }); await flushPromises()
  await clickText(wrapper, '删评论'); await clickText(wrapper, '确认处理')
  expect(type === 'comment' ? api.deleteWorkComment : api.deleteWorkReply).toHaveBeenCalledWith('c')
  expect(api.processCommentComplaint).toHaveBeenCalledWith('f', 'COMPLETED', '已删除被投诉评论')
})
it('取证失败时保留投诉待处理状态，不能盲删再结案', async () => {
  api.getCommentById.mockRejectedValue(new Error('证据无权访问'))
  const wrapper = mount(CommentComplaintsView, { global: { stubs: uiStubs } }); await flushPromises()
  await clickText(wrapper, '删评论'); await clickText(wrapper, '确认处理')
  expect(api.deleteWorkComment).not.toHaveBeenCalled(); expect(api.processCommentComplaint).not.toHaveBeenCalled()
  expect(ElMessage.error).toHaveBeenCalledWith('处理失败')
})
it('查看证据、忽略投诉及分页保留各自行为', async () => {
  const wrapper = mount(CommentComplaintsView, { global: { stubs: uiStubs } }); await flushPromises()
  await clickText(wrapper, '查看内容'); expect(wrapper.text()).toContain('证据正文')
  await clickText(wrapper, '关闭'); await clickText(wrapper, '忽略'); await clickText(wrapper, '确认处理')
  expect(api.processCommentComplaint).toHaveBeenCalledWith('f', 'COMPLETED', '投诉已忽略')
  api.getCommentById.mockRejectedValueOnce(new Error('无权'))
  await clickText(wrapper, '查看内容'); expect(wrapper.text()).toContain('评论已被删除或不存在')
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('update:current-page', 2)
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2)
  await flushPromises(); expect(api.getCommentComplaints).toHaveBeenLastCalledWith(2)
})
it('终态投诉没有处理按钮，未知状态和匿名信息正常展示', async () => {
  api.getCommentComplaints.mockResolvedValue({ total: 4, list: ['PROCESSING', 'COMPLETED', 'REJECTED', 'UNKNOWN'].map(status => ({ ...feedback, status, id: status, author: '', content: '', created_at: '' })) })
  const wrapper = mount(CommentComplaintsView, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.findAll('button').filter(b => b.text() === '删评论')).toHaveLength(1)
  expect(wrapper.text()).toContain('匿名'); expect(wrapper.text()).toContain('UNKNOWN')
  api.getCommentComplaints.mockRejectedValueOnce(new Error('断网'))
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('加载投诉列表失败')
})
