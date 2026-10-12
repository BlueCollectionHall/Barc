import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import MessagesListView from '../src/modules/messages/views/MessagesListView.vue'
import { clickText, uiStubs } from './uiHarness'

const api = vi.hoisted(() => ({ fetchMessagesByPage: vi.fn(), deleteMessage: vi.fn(), updateMessage: vi.fn(), batchDeleteMessages: vi.fn() }))
const messages = vi.hoisted(() => ({ showError: vi.fn(), showSuccess: vi.fn() }))
vi.mock('../src/modules/messages/api/messages.service', () => api)
vi.mock('../src/shared/utils/message', () => messages)
vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessageBox: { confirm: vi.fn() } }))
enableAutoUnmount(afterEach)
const row = { id: 'm', author: 'u', content: '内容'.repeat(60), created_at: '2026-10-12T10:00:00' }
beforeEach(() => {
  vi.resetAllMocks(); api.fetchMessagesByPage.mockResolvedValue({ list: [row], total: 40 })
  api.deleteMessage.mockResolvedValue(''); api.updateMessage.mockResolvedValue(''); api.batchDeleteMessages.mockResolvedValue('')
  vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')
})
it('列表筛选与分页提交正确，重置同时清空选择', async () => {
  const wrapper = mount(MessagesListView, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.text()).toContain('未知昵称@u'); expect(wrapper.text()).toContain('…')
  await wrapper.get('input[placeholder="UUID / 用户名 / 昵称"]').setValue('  用户  ')
  await wrapper.get('input[placeholder="模糊搜索"]').setValue('  内容  ')
  const dates = wrapper.findAllComponents({ name: 'ElDatePicker' }); dates[0]!.vm.$emit('update:modelValue', '2026-10-01'); dates[1]!.vm.$emit('update:modelValue', '2026-10-12')
  await clickText(wrapper, '搜索'); expect(api.fetchMessagesByPage).toHaveBeenLastCalledWith({ page_num: 1, page_size: 15, params: { keyword: '用户', content: '内容', date_from: '2026-10-01', date_to: '2026-10-12' } })
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(api.fetchMessagesByPage).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 2 }))
  await clickText(wrapper, '重置'); expect(api.fetchMessagesByPage).toHaveBeenLastCalledWith({ page_num: 1, page_size: 15, params: {} })
  api.fetchMessagesByPage.mockRejectedValueOnce(new Error('断网')); await clickText(wrapper, '刷新'); expect(messages.showError).toHaveBeenCalledWith('断网')
})
it('删除先确认，取消或失败不会报成功', async () => {
  const wrapper = mount(MessagesListView, { global: { stubs: uiStubs } }); await flushPromises()
  vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel'); await clickText(wrapper, '删除'); expect(api.deleteMessage).not.toHaveBeenCalled()
  await clickText(wrapper, '删除'); expect(api.deleteMessage).toHaveBeenCalledWith('m'); expect(messages.showSuccess).toHaveBeenCalledWith('留言已删除。')
  api.deleteMessage.mockRejectedValueOnce(new Error('无权')); await clickText(wrapper, '删除'); expect(messages.showError).toHaveBeenCalledWith('无权')
})
it('编辑拒绝空内容并发送修改说明，失败保留表单', async () => {
  const wrapper = mount(MessagesListView, { global: { stubs: uiStubs } }); await flushPromises()
  await clickText(wrapper, '编辑'); await wrapper.get('textarea').setValue(' '); await clickText(wrapper, '确认修改并发送通知')
  expect(api.updateMessage).not.toHaveBeenCalled(); expect(messages.showError).toHaveBeenCalledWith('留言内容不能为空')
  await wrapper.get('textarea').setValue('  更正  '); await wrapper.get('input[placeholder="例：内容包含不当言论，已修改为合规内容"]').setValue('  原因  ')
  api.updateMessage.mockRejectedValueOnce(new Error('冲突')); await clickText(wrapper, '确认修改并发送通知'); expect(messages.showError).toHaveBeenCalledWith('冲突')
  await clickText(wrapper, '确认修改并发送通知'); expect(api.updateMessage).toHaveBeenLastCalledWith({ message_id: 'm', content: '更正', reason: '原因' })
  expect(messages.showSuccess).toHaveBeenCalledWith('留言已修改，将通知发布者。')
})
it('批量删除只提交已选 ID，失败不清空选择，成功后刷新', async () => {
  const wrapper = mount(MessagesListView, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.findAll('button').find(b => b.text() === '批量删除 (0)')!.attributes('disabled')).toBeDefined()
  wrapper.findComponent({ name: 'ElTable' }).vm.$emit('selection-change', [row, { ...row, id: 'm2' }]); await flushPromises()
  await clickText(wrapper, '批量删除 (2)'); await wrapper.get('textarea').setValue('  治理原因  ')
  api.batchDeleteMessages.mockRejectedValueOnce(new Error('失败')); await clickText(wrapper, '确认批量删除'); expect(wrapper.text()).toContain('批量删除 (2)')
  await clickText(wrapper, '确认批量删除'); expect(api.batchDeleteMessages).toHaveBeenLastCalledWith({ message_ids: ['m', 'm2'], reason: '治理原因' })
  expect(wrapper.text()).toContain('批量删除 (0)')
})
