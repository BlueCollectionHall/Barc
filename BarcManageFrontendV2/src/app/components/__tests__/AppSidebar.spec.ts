import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'

import AppSidebar from '@/app/components/AppSidebar.vue'
import { hasManagerPermissionBit, hasMinimumManagerPermission } from '@/shared/constants/permissions'

/**
 * 管理台侧边栏测试
 *
 * 覆盖 2026-09-20 拍板后的行为约定：
 * 1、默认全部收起（含激活组）；
 * 2、折叠状态只在内存里，仅由点击驱动，路由变化不改写；
 * 3、激活组高亮（默认全收起时用户仍能判断自己在哪）；
 * 4、组内无可见项时整组不渲染。
 */

/** 被 mock 的当前路由：测试中直接改写 name 模拟导航 */
const routeState = reactive<{ name: string | null }>({ name: 'dashboard' })
const pushMock = vi.fn()

/** 被 mock 的登录态：只保留侧边栏真正读取的两个字段 */
const authState = reactive<{ userArchive: { identity: string; permission: number } | null }>({
  userArchive: { identity: 'MANAGER', permission: 63 },
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/app/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/app/stores/permissions', () => ({
  usePermissionStore: () => ({
    // 复用真实判定函数，保证"权限过滤"断言与生产语义一致
    canAccessRoute: (
      meta: { requiresManager?: boolean; minManagerPermission?: number; minManagerPermissionBit?: number },
      user: { identity?: string; permission?: number } | null,
    ): boolean => {
      if (meta.requiresManager && user?.identity !== 'MANAGER') {
        return false
      }

      if (meta.minManagerPermissionBit !== undefined) {
        return hasManagerPermissionBit(user?.permission, meta.minManagerPermissionBit)
      }

      return hasMinimumManagerPermission(user?.permission, meta.minManagerPermission)
    },
  }),
}))

/** 找到指定分组标签对应的 section；找不到直接抛错，避免断言静默通过 */
function groupSection(wrapper: VueWrapper, label: string): DOMWrapper<Element> {
  const matched = wrapper
    .findAll('.app-sidebar__group')
    .find((section) => section.find('.app-sidebar__group-label').text() === label)

  if (!matched) {
    throw new Error(`未找到分组：${label}`)
  }

  return matched
}

function groupLabels(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.app-sidebar__group-label').map((node) => node.text())
}

function groupToggles(wrapper: VueWrapper): DOMWrapper<Element>[] {
  return wrapper.findAll('.app-sidebar__group-toggle')
}

async function mountSidebar(): Promise<VueWrapper> {
  const wrapper = mount(AppSidebar)
  await nextTick()
  return wrapper
}

describe('AppSidebar 分组折叠', () => {
  beforeEach(() => {
    routeState.name = 'dashboard'
    authState.userArchive = { identity: 'MANAGER', permission: 63 }
    pushMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('按权限过滤子项，组内无可见项时整组不渲染', async () => {
    authState.userArchive = { identity: 'MANAGER', permission: 2 }
    const wrapper = await mountSidebar()

    const labels = groupLabels(wrapper)
    expect(labels).toContain('留言管理')
    // 「用户」组四项分别需要 ≥8 / 1匹配 bit8 / 1匹配 bit32，只有 bit2 时整组不出现
    expect(labels).not.toContain('用户')
    expect(wrapper.text()).not.toContain('官方账号')

    wrapper.unmount()
  })

  it('默认全部收起（含当前激活路由所在组），不渲染任何菜单项', async () => {
    routeState.name = 'users-list'
    const wrapper = await mountSidebar()

    const expandedStates = groupToggles(wrapper).map((node) => node.attributes('aria-expanded'))
    expect(expandedStates.length).toBeGreaterThan(0)
    expect(expandedStates.every((value) => value === 'false')).toBe(true)
    expect(wrapper.findAll('.app-sidebar__link')).toHaveLength(0)

    wrapper.unmount()
  })

  it('高亮当前激活路由所在组；无分组归属的隐藏页不高亮任何组', async () => {
    routeState.name = 'users-list'
    const wrapper = await mountSidebar()

    expect(groupSection(wrapper, '用户').classes()).toContain('is-active-group')
    expect(groupSection(wrapper, '作品管理').classes()).not.toContain('is-active-group')

    // 公告编辑页属于 hiddenInMenu 且没有 menuGroup，不应高亮任何组
    routeState.name = 'notices-new'
    await nextTick()

    expect(
      wrapper.findAll('.app-sidebar__group').some((section) => section.classes().includes('is-active-group')),
    ).toBe(false)

    wrapper.unmount()
  })

  it('点击组标签展开与收起，并同步 aria 状态与面板 id', async () => {
    const wrapper = await mountSidebar()

    const toggle = groupSection(wrapper, '用户').find('.app-sidebar__group-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.attributes('aria-controls')).toBe('sidebar-group-users')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')

    const panel = wrapper.find('#sidebar-group-users')
    expect(panel.exists()).toBe(true)
    expect(panel.findAll('.app-sidebar__link')).toHaveLength(4)

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('#sidebar-group-users').exists()).toBe(false)

    wrapper.unmount()
  })

  it('单项组同样可以折叠（概览 / 公告 / 留言管理）', async () => {
    const wrapper = await mountSidebar()

    const toggle = groupSection(wrapper, '公告').find('.app-sidebar__group-toggle')
    await toggle.trigger('click')
    expect(groupSection(wrapper, '公告').find('.app-sidebar__link').text()).toBe('公告列表')

    await toggle.trigger('click')
    expect(groupSection(wrapper, '公告').findAll('.app-sidebar__link')).toHaveLength(0)

    wrapper.unmount()
  })

  it('路由变化不改变折叠状态（不做激活联动）', async () => {
    const wrapper = await mountSidebar()

    await groupSection(wrapper, '作品管理').find('.app-sidebar__group-toggle').trigger('click')
    expect(groupSection(wrapper, '作品管理').findAll('.app-sidebar__link').length).toBeGreaterThan(0)

    routeState.name = 'users-list'
    await nextTick()

    // 作品管理保持展开，用户组保持收起，同时用户组被高亮
    expect(groupSection(wrapper, '作品管理').findAll('.app-sidebar__link').length).toBeGreaterThan(0)
    expect(groupSection(wrapper, '用户').find('.app-sidebar__group-toggle').attributes('aria-expanded')).toBe('false')
    expect(groupSection(wrapper, '用户').classes()).toContain('is-active-group')

    wrapper.unmount()
  })

  it('权限未加载时不渲染分组，加载完成后仍保持全部收起', async () => {
    authState.userArchive = null
    const wrapper = await mountSidebar()

    expect(wrapper.findAll('.app-sidebar__group')).toHaveLength(0)

    authState.userArchive = { identity: 'MANAGER', permission: 63 }
    await nextTick()

    expect(wrapper.findAll('.app-sidebar__group').length).toBeGreaterThan(0)
    expect(groupToggles(wrapper).every((node) => node.attributes('aria-expanded') === 'false')).toBe(true)
    expect(wrapper.findAll('.app-sidebar__link')).toHaveLength(0)

    wrapper.unmount()
  })

  it('点击菜单项按 route name 跳转', async () => {
    const wrapper = await mountSidebar()

    await groupSection(wrapper, '概览').find('.app-sidebar__group-toggle').trigger('click')
    await groupSection(wrapper, '概览').find('.app-sidebar__link').trigger('click')

    expect(pushMock).toHaveBeenCalledWith({ name: 'dashboard' })

    wrapper.unmount()
  })
})
