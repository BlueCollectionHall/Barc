import { beforeEach, expect, it, vi } from 'vitest'
import { ManagerOnlyError } from '../src/shared/types/api'
import { ROUTE_REASON } from '../src/app/router/meta'
import router from '../src/app/router'

const auth = vi.hoisted(() => ({ token: null as string | null, isAuthenticated: false, isManager: false, userArchive: null, bootstrap: vi.fn(), clearSession: vi.fn() }))
const permission = vi.hoisted(() => ({ canAccessRoute: vi.fn() }))
vi.mock('../src/app/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('../src/app/stores/permissions', () => ({ usePermissionStore: () => permission }))
vi.mock('../src/app/router/routes', () => ({ routes: [
  { path: '/login', name: 'login', component: { template: '<p>登录</p>' } },
  { path: '/dashboard', name: 'dashboard', component: { template: '<p>概览</p>' }, meta: { requiresAuth: true, requiresManager: true } },
  { path: '/secure', name: 'secure', component: { template: '<p>受控</p>' }, meta: { requiresAuth: true, requiresManager: true } },
  { path: '/manager', name: 'manager', component: { template: '<p>身份受控</p>' }, meta: { requiresManager: true } },
  { path: '/forbidden', name: 'forbidden', component: { template: '<p>无权</p>' } },
  { path: '/public', name: 'public', component: { template: '<p>公开</p>' } },
] }))
beforeEach(async () => {
  vi.resetAllMocks(); vi.spyOn(window, 'scrollTo').mockImplementation(() => {}); Object.assign(auth, { token: null, isAuthenticated: false, isManager: false }); permission.canAccessRoute.mockReturnValue(true)
  await router.push('/public')
})
it('匿名访问受控页面回登录，并保留原地址', async () => {
  await router.push('/secure'); expect(router.currentRoute.value.name).toBe('login')
  expect(router.currentRoute.value.query).toEqual({ redirect: '/secure', reason: ROUTE_REASON.AUTH_REQUIRED })
})
it('普通身份和失效令牌分别给出原因，公开页仍可访问', async () => {
  auth.bootstrap.mockRejectedValueOnce(new ManagerOnlyError()); await router.push('/secure')
  expect(router.currentRoute.value.query.reason).toBe(ROUTE_REASON.MANAGER_ONLY)
  auth.bootstrap.mockRejectedValueOnce(new Error('过期')); await router.push('/secure')
  expect(router.currentRoute.value.query.reason).toBe(ROUTE_REASON.SESSION_EXPIRED)
  await router.push('/manager'); expect(auth.clearSession).toHaveBeenCalled(); expect(router.currentRoute.value.query.reason).toBe(ROUTE_REASON.MANAGER_ONLY)
})
it('已登录管理员从登录页回原地址，无权限页面进入403页', async () => {
  Object.assign(auth, { token: 'jwt', isAuthenticated: true, isManager: true })
  await router.push('/login?redirect=/secure'); expect(router.currentRoute.value.name).toBe('secure')
  await router.push('/login'); expect(router.currentRoute.value.name).toBe('dashboard')
  permission.canAccessRoute.mockReturnValue(false); await router.push('/secure'); expect(router.currentRoute.value.name).toBe('forbidden')
})
