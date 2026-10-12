import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../src/app/stores/auth'
import WorkReviewsView from '../src/modules/works/views/WorkReviewsView.vue'
import ClaimListComp from '../src/modules/works/components/ClaimListComp.vue'
import ClaimsListView from '../src/modules/works/views/ClaimsListView.vue'
import OperationLogComp from '../src/modules/works/components/OperationLogComp.vue'
import WorkListComp from '../src/modules/works/components/WorkListComp.vue'
import WorkDetailView from '../src/modules/works/views/WorkDetailView.vue'
import { clickText, uiStubs } from './uiHarness'

const api = vi.hoisted(() => ({ getWorkReviewList: vi.fn(), reviewWork: vi.fn(), getClaimsPage: vi.fn(), approveClaim: vi.fn(), assignAuthor: vi.fn(), revokeClaim: vi.fn(), searchWorks: vi.fn(), searchUsers: vi.fn(), getOperationLogs: vi.fn(), getWorkListManage: vi.fn(), updateWorkStatus: vi.fn() }))
const routing = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn() }))
vi.mock('../src/modules/works/api/workManage', () => api)
vi.mock('vue-router', () => ({ useRouter: () => routing, useRoute: () => ({ params: { workId: 'w' } }) }))
vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
enableAutoUnmount(afterEach)
const row = { id: 'w', title: '作品', status: 'PRIVATE', review_status: 'PENDING', content_revision: 17, author: 'u', is_claim: true, created_at: '2026-10-12T10:00:00' }
const claim = { id: 'claim', work_id: 'w', status: 'PENDING', work_title: '作品', applicant_nickname: '申请人', statement: '证据链接' }
beforeEach(() => {
  vi.clearAllMocks(); setActivePinia(createPinia()); vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')
  api.getWorkReviewList.mockResolvedValue({ list: [row], total: 15 }); api.reviewWork.mockResolvedValue('ok')
  api.getClaimsPage.mockResolvedValue({ list: [claim], total: 25 }); api.approveClaim.mockResolvedValue('ok')
  api.assignAuthor.mockResolvedValue('ok'); api.revokeClaim.mockResolvedValue('ok')
  api.searchWorks.mockResolvedValue([row]); api.searchUsers.mockResolvedValue([{ uuid: 'u', nickname: '会员', username: 'name' }])
  api.getOperationLogs.mockResolvedValue({ list: [], total: 0 }); api.getWorkListManage.mockResolvedValue({ list: [row], total: 31 })
  api.updateWorkStatus.mockResolvedValue('ok')
})
it('审核使用当前内容版本，拒绝原因必填且通过前先确认', async () => {
  const wrapper = mount(WorkReviewsView, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.text()).toContain('私有'); await clickText(wrapper, '通过')
  expect(api.reviewWork).toHaveBeenCalledWith('w', true, 17)
  await clickText(wrapper, '拒绝'); await clickText(wrapper, '确认拒绝')
  expect(ElMessage.warning).toHaveBeenCalled(); expect(api.reviewWork).toHaveBeenCalledTimes(1)
  await wrapper.get('textarea').setValue('  说明不足  '); await clickText(wrapper, '确认拒绝')
  expect(api.reviewWork).toHaveBeenLastCalledWith('w', false, 17, '说明不足')
  await clickText(wrapper, '查看详情'); expect(routing.push).toHaveBeenCalledWith({ name: 'works-detail', params: { workId: 'w' } })
})
it('审核搜索、分页、结论切换和失败提示不会静默丢失', async () => {
  const wrapper = mount(WorkReviewsView, { global: { stubs: uiStubs } }); await flushPromises()
  await wrapper.get('input[placeholder="搜索作品 ID、标题或作者"]').setValue('  搜索  '); await clickText(wrapper, '搜索')
  expect(api.getWorkReviewList).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 1, params: { keyword: '搜索' } }), 'PENDING')
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(api.getWorkReviewList).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 2 }), 'PENDING')
  const segmented = wrapper.findComponent({ name: 'ElSegmented' })
  segmented.vm.$emit('update:modelValue', 'REJECTED'); segmented.vm.$emit('change', 'REJECTED'); await flushPromises()
  expect(api.getWorkReviewList).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 1 }), 'REJECTED')
  vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel'); await clickText(wrapper, '通过'); expect(api.reviewWork).not.toHaveBeenCalled()
  api.reviewWork.mockRejectedValue(new Error('版本冲突')); await clickText(wrapper, '通过'); expect(ElMessage.error).toHaveBeenCalledWith('审核操作失败')
  await clickText(wrapper, '拒绝'); await wrapper.get('textarea').setValue('拒绝'); await clickText(wrapper, '确认拒绝')
  expect(wrapper.text()).toContain('确认拒绝')
  api.getWorkReviewList.mockRejectedValueOnce(new Error('断网')); await clickText(wrapper, '刷新')
  expect(ElMessage.error).toHaveBeenCalledWith('加载审核队列失败')
})
it('审核历史按结论展示，不再出现审核操作按钮', async () => {
  api.getWorkReviewList.mockResolvedValue({ total: 4, list: ['APPROVED', 'REJECTED', null, ''].map(review_status => ({ ...row, review_status, status: 'PUBLIC', content_revision: undefined })) })
  const wrapper = mount(WorkReviewsView, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.text()).toContain('已通过'); expect(wrapper.text()).toContain('未通过'); expect(wrapper.text()).toContain('未知')
  expect(wrapper.findAll('button').some(b => b.text() === '通过')).toBe(false)
})
it('认领列表提供历史筛选和真实分页，终态记录只能预览', async () => {
  api.getClaimsPage.mockResolvedValue({ total: 2, list: [claim, { ...claim, id: 'history', status: 'APPROVED' }] })
  const wrapper = mount(ClaimListComp, { global: { stubs: uiStubs } }); await flushPromises()
  await clickText(wrapper, '通过'); expect(wrapper.emitted('approve')?.[0]?.[0]).toMatchObject({ id: 'claim' })
  await clickText(wrapper, '拒绝'); await clickText(wrapper, '预览')
  expect(wrapper.findAll('button').filter(b => b.text() === '通过')).toHaveLength(1)
  const select = wrapper.findComponent({ name: 'ElSelect' }); select.vm.$emit('update:modelValue', ''); select.vm.$emit('change', ''); await flushPromises()
  expect(api.getClaimsPage).toHaveBeenLastCalledWith(1, undefined)
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('update:current-page', 2)
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(api.getClaimsPage).toHaveBeenLastCalledWith(2, undefined)
  api.getClaimsPage.mockRejectedValueOnce(new Error('断网')); select.vm.$emit('change', 'PENDING'); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('加载认领记录失败')
})
it('风纪委员只看到申请与历史，可以审批和预览，不能指派或撤销', async () => {
  const auth = useAuthStore(); auth.userArchive = { identity: 'MANAGER', permission: 1 } as NonNullable<typeof auth.userArchive>
  const wrapper = mount(ClaimsListView, { global: { stubs: { ...uiStubs, ClaimListComp: true, WorkDetailComp: true } } }); await flushPromises()
  expect(wrapper.text()).not.toContain('指派作者'); expect(wrapper.text()).not.toContain('撤销认领')
  const child = wrapper.findComponent({ name: 'ClaimListComp' }); child.vm.$emit('approve', claim); await flushPromises()
  expect(api.approveClaim).toHaveBeenCalledWith('claim', true)
  child.vm.$emit('reject', claim); await flushPromises(); expect(api.approveClaim).toHaveBeenLastCalledWith('claim', false)
  child.vm.$emit('preview', claim); await flushPromises()
  expect(wrapper.findComponent({ name: 'WorkDetailComp' }).props()).toMatchObject({ workId: 'w', claimMode: true })
  api.approveClaim.mockRejectedValueOnce(new Error('冲突')); child.vm.$emit('approve', claim); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('操作失败')
})
it('审计日志正确展示新旧认领结果大小写、状态和非法历史详情', async () => {
  const entries = [
    ['CLAIM_APPROVE', '{"result":"APPROVED"}'], ['CLAIM_APPROVE', '{"result":"approved"}'], ['CLAIM_APPROVE', '{"result":"REJECTED"}'],
    ['RESTORE', '{"old_status":"OFF","new_status":"PRIVATE","remark":"说明"}'], ['EDIT', '{}'], ['CLAIM_REVOKE', '{}'], ['CLAIM_ASSIGN', '{}'],
    ['COMPLAINT_PROCESS', '{"action":"BAN","remark":"说明"}'], ['OTHER', '原始说明'], ['OTHER', '{}'],
  ]
  api.getOperationLogs.mockResolvedValue({ total: 40, list: entries.map(([operation_type, detail], i) => ({ id: String(i), operation_type, detail })) })
  const wrapper = mount(OperationLogComp, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.text().match(/审批通过/g)).toHaveLength(2); expect(wrapper.text()).toContain('审批拒绝')
  expect(wrapper.text()).toContain('从「下架」改为「私有」，备注：说明'); expect(wrapper.text()).toContain('原始说明')
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('update:current-page', 2)
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(api.getOperationLogs).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 2 }))
})
it('作品列表筛选重置页码，操作把完整作品行交给父页面', async () => {
  const wrapper = mount(WorkListComp, { global: { stubs: uiStubs } }); await flushPromises()
  expect(wrapper.text()).toContain('作品')
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(api.getWorkListManage).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 2 }))
  const select = wrapper.findComponent({ name: 'ElSelect' }); select.vm.$emit('update:modelValue', 'PUBLIC'); select.vm.$emit('change', 'PUBLIC'); await flushPromises()
  expect(api.getWorkListManage).toHaveBeenLastCalledWith(expect.objectContaining({ page_num: 1, params: { status: 'PUBLIC', keyword: undefined } }))
})
it('作品详情恢复保留原可见性含义，操作失败不返回列表', async () => {
  const wrapper = mount(WorkDetailView, { global: { stubs: { ...uiStubs, WorkDetailComp: true, WorkCommentManage: true }, mocks: { $router: routing } } })
  wrapper.findComponent({ name: 'WorkDetailComp' }).vm.$emit('restore'); await flushPromises()
  expect(wrapper.text()).toContain('恢复原可见性'); await clickText(wrapper, '确认')
  expect(api.updateWorkStatus).toHaveBeenCalledWith('w', 'PUBLIC', ''); expect(routing.back).toHaveBeenCalledTimes(1)
  api.updateWorkStatus.mockRejectedValueOnce(new Error('失败'))
  wrapper.findComponent({ name: 'WorkDetailComp' }).vm.$emit('ban'); await flushPromises(); await clickText(wrapper, '确认')
  expect(routing.back).toHaveBeenCalledTimes(1); expect(ElMessage.error).toHaveBeenCalledWith('操作失败')
  wrapper.findComponent({ name: 'WorkDetailComp' }).vm.$emit('off'); await flushPromises(); await clickText(wrapper, '取消')
  wrapper.findComponent({ name: 'WorkDetailComp' }).vm.$emit('delete'); await flushPromises()
  expect(api.updateWorkStatus).toHaveBeenLastCalledWith('w', 'DELETED', '管理员删除')
})
