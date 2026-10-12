// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { computed, defineComponent, h, inject, provide, type ComputedRef } from 'vue'
import ManageClaimsComp from '../src/components/Manage/ManageClaimsComp.vue'
import ManageHeaderComp from '../src/components/Manage/ManageHeaderComp.vue'
import { submitWorkClaim } from '../src/utils/workClaimApi'
import { useUserPinia } from '../src/stores/UserPinia'
import type { UserArchiveImpl } from '../src/interfaces/UserImpl'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const notice = vi.hoisted(() => ({ errorMessage: vi.fn() }))
const replace = vi.hoisted(() => vi.fn())
vi.mock('../src/utils/https', () => ({ baseHttp: http }))
vi.mock('../src/utils/MessageAlert', () => notice)
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }))
enableAutoUnmount(afterEach)
/** 表格保留逐行插槽，让处理结果与状态文案在组件测试中真实渲染。 */
const table = defineComponent({ props: ['data'], setup: (props, { slots }) => { provide('rows', computed(() => props.data)); return () => h('section', slots.default?.()) } })
const column = defineComponent({ props: ['prop'], setup: (props, { slots }) => { const rows = inject<ComputedRef<Record<string, unknown>[]>>('rows')!; return () => h('div', rows.value.map(row => slots.default ? slots.default({ row }) : h('span', String(row[props.prop] ?? '')))) } })
beforeEach(() => {
  vi.resetAllMocks(); setActivePinia(createPinia())
  http.get.mockResolvedValue({ data: { code: 0, data: { list: [{ id: 'claim', work_title: '已不再公开的作品', status: 'SUPERSEDED', result_reason: '另一个申请已获批准', created_at: '2026-10-12' }], total: 21 } } })
  http.post.mockResolvedValue({ data: { code: 0 } })
})
it('本人认领记录显示结案结果并支持继续分页，不依赖公开作品详情', async () => {
  const wrapper = mount(ManageClaimsComp, { global: { stubs: { 'el-table': table, 'el-table-column': column, 'el-pagination': true } } }); await flushPromises()
  expect(wrapper.text()).toContain('已不再公开的作品'); expect(wrapper.text()).toContain('已结案（归属已确定）'); expect(wrapper.text()).toContain('另一个申请已获批准')
  expect(http.get).toHaveBeenCalledWith('/api/work/claim/mine', { params: { page: 1, size: 20, work_id: undefined } })
  const pagination = wrapper.findComponent('el-pagination-stub'); pagination.vm.$emit('update:current-page', 2); pagination.vm.$emit('current-change', 2); await flushPromises()
  expect(http.get.mock.lastCall![1].params.page).toBe(2)
  http.get.mockResolvedValueOnce({ data: { code: 1, data: '会话已过期' } }); pagination.vm.$emit('current-change'); await flushPromises(); expect(notice.errorMessage).toHaveBeenCalledWith('会话已过期')
})
it('认领写入只提交作品与说明，拒绝时保留业务错误', async () => {
  await submitWorkClaim('work', '署名证据'); expect(http.post).toHaveBeenCalledWith('/api/work/claim/upload', { work_id: 'work', statement: '署名证据' })
  http.post.mockResolvedValueOnce({ data: { code: 1, data: '已有待审核申请' } }); await expect(submitWorkClaim('work', '说明')).rejects.toThrow('已有待审核申请')
})
it('会员内容管理可进入本人认领记录，未加载身份时不显示入口', async () => {
  const wrapper = mount(ManageHeaderComp); expect(wrapper.text()).toContain('加载中')
  useUserPinia().userArchive = { uuid: 'u', identity: 'USER', permission: 1 } as UserArchiveImpl; await flushPromises(); expect(wrapper.text()).toContain('认领记录'); expect(wrapper.text()).not.toContain('公告')
  await wrapper.findAll('.menu-item')[0].trigger('click'); expect(replace).toHaveBeenCalledWith({ name: 'ManageClaims' })
  useUserPinia().userArchive = { uuid: 'u', identity: 'MANAGER', permission: 4 } as UserArchiveImpl; await flushPromises(); expect(wrapper.text()).toContain('公告')
})
