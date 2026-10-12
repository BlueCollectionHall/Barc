import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import WorkDetailComp from '../src/modules/works/components/WorkDetailComp.vue'
import { uiStubs } from './uiHarness'

const api = vi.hoisted(() => ({ getWorkEditDetail: vi.fn(), getClaimWorkDetail: vi.fn() }))
vi.mock('../src/modules/works/api/workManage', () => api)
enableAutoUnmount(afterEach)

// 审核者会看到尚未公开的投稿，组件必须在插入 DOM 前独立净化接口内容。
it.each([false, true])('管理详情 claimMode=%s 净化恶意投稿且保留排版', async claimMode => {
  const detail = { work: { id: 'w', title: '投稿', content: '<p class="ql-align-center" onclick="alert(1)">审核正文<script>alert(1)</script><img src="https://image.example.test/a.png" onerror="alert(1)"></p>', is_claim: false, status: 'PUBLIC', review_status: 'PENDING', created_at: '2026-10-12' }, content_image_urls: [] }
  api.getWorkEditDetail.mockResolvedValue(detail); api.getClaimWorkDetail.mockResolvedValue(detail)
  const wrapper = mount(WorkDetailComp, { props: { workId: 'w', claimMode }, global: { stubs: { ...uiStubs, WorkStatusBadge: true } } })
  await flushPromises()
  expect(wrapper.get('.body').text()).toBe('审核正文')
  expect(wrapper.get('.body').find('script,[onclick],[onerror]').exists()).toBe(false)
  expect(wrapper.get('.body p').classes()).toContain('ql-align-center')
  expect(wrapper.get('.body img').attributes('src')).toBe('https://image.example.test/a.png')
  expect(claimMode ? api.getClaimWorkDetail : api.getWorkEditDetail).toHaveBeenCalledWith('w')
})
