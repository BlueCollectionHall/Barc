import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import DashboardView from '@/modules/dashboard/views/DashboardView.vue'

/**
 * 控制台首页测试
 *
 * 2026-09-20 拍板：原先平铺全部可访问模块的入口卡片已删除，
 * 这里锁定"不再出现重复入口列表"以及"会话信息仍然展示"两个行为。
 */

const authState = reactive({
  userArchive: {
    uuid: 'user-1',
    nickname: '馆长甲',
    avatar: null,
    gender: null,
    birthday: null,
    age: null,
    permission: 63,
    identity: 'MANAGER',
    updated_at: null,
  },
  userBasic: {
    uuid: 'user-1',
    username: 'barc-admin',
    password: '',
    password_version: 1,
    email: 'admin@barc.work',
    email_verified: true,
    telephone: null,
    safe_level: 1,
    created_at: null,
    updated_at: null,
  },
})

vi.mock('@/app/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/app/stores/permissions', () => ({
  usePermissionStore: () => ({
    labelFor: (_identity: string, permission: number | null | undefined): string =>
      permission === null || permission === undefined ? '未设置' : '馆长',
  }),
}))

describe('DashboardView', () => {
  it('不再渲染「可访问模块」入口卡片', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.find('.quick-link').exists()).toBe(false)
    expect(wrapper.findAll('.quick-links')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('可访问模块')

    wrapper.unmount()
  })

  it('保留当前会话信息与能力层级展示', () => {
    const wrapper = mount(DashboardView)

    expect(wrapper.text()).toContain('当前会话')
    expect(wrapper.text()).toContain('馆长甲')
    expect(wrapper.text()).toContain('barc-admin')
    expect(wrapper.text()).toContain('馆长')
    expect(wrapper.text()).toContain('Phase 0 约束')

    wrapper.unmount()
  })
})
