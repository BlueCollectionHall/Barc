import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../src/app/stores/auth'
import { usePermissionStore } from '../src/app/stores/permissions'
import { SESSION_TOKEN_KEY } from '../src/shared/constants/auth'
import { AuthExpiredError, ManagerOnlyError } from '../src/shared/types/api'
import type { UserArchive } from '../src/shared/types/user'

const api = vi.hoisted(() => ({ signIn: vi.fn(), signInByNaigos: vi.fn(), fetchCurrentMe: vi.fn(), fetchBasicMe: vi.fn(), fetchAllIdentities: vi.fn(), fetchMyPermissionNearMax: vi.fn(), fetchPermissionsByIdentity: vi.fn() }))
vi.mock('../src/modules/auth/api/auth.service', () => api)
vi.mock('../src/modules/users/api/users.service', () => api)
const manager = { uuid: 'm', identity: 'MANAGER', permission: 5 } as UserArchive
beforeEach(() => {
  vi.resetAllMocks(); localStorage.clear(); setActivePinia(createPinia())
  api.signIn.mockResolvedValue('jwt'); api.signInByNaigos.mockResolvedValue('external-exchanged-jwt')
  api.fetchCurrentMe.mockResolvedValue(manager); api.fetchBasicMe.mockResolvedValue({ uuid: 'm', username: 'owner' })
  api.fetchAllIdentities.mockResolvedValue([{ value: 'USER', label: '会员' }, { value: 'MANAGER', label: '管理员' }])
  api.fetchPermissionsByIdentity.mockImplementation(async identity => identity === 'MANAGER' ? [{ value: 1, label: '风纪委员' }, { value: 4, label: '二级管理员' }] : [{ value: 1, label: '会员' }])
  api.fetchMyPermissionNearMax.mockResolvedValue({ value: 4, label: '二级管理员' })
})
it('无令牌时不请求资料，登录成功建立会话，清理同时清除权限上下文', async () => {
  const auth = useAuthStore(); expect(await auth.bootstrap()).toBe(false); expect(api.fetchCurrentMe).not.toHaveBeenCalled()
  await auth.signInWithBarc({ type: 'username', account: 'm', password: 'pass' })
  expect(auth.isAuthenticated).toBe(true); expect(auth.isManager).toBe(true); expect(auth.managerPermissionLabel).toBe('二级管理员')
  expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBe('jwt'); expect(usePermissionStore().myPermissionNearMax).toMatchObject({ value: 4 })
  auth.clearSession(); expect(auth.isAuthenticated).toBe(false); expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBeNull(); expect(usePermissionStore().myPermissionNearMax).toBeNull()
})
it('并发恢复只请求一次，Naigos 凭据换取站内令牌', async () => {
  localStorage.setItem(SESSION_TOKEN_KEY, 'jwt'); const auth = useAuthStore()
  const [first, second] = await Promise.all([auth.bootstrap(), auth.bootstrap()]); expect(first && second).toBe(true)
  expect(api.fetchCurrentMe).toHaveBeenCalledTimes(1)
  await auth.signInWithNaigos({ type: 'uid', account: 'external', password: 'pass' })
  expect(api.signInByNaigos).toHaveBeenCalledWith({ type: 'uid', account: 'external', password: 'pass' })
  expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBe('external-exchanged-jwt')
})
it('普通用户与失效会话均清除令牌，错误原样交给路由', async () => {
  const auth = useAuthStore(); api.fetchCurrentMe.mockResolvedValue({ ...manager, identity: 'USER' })
  await expect(auth.signInWithBarc({ type: 'email', account: 'u@example.test', password: 'pass' })).rejects.toBeInstanceOf(ManagerOnlyError)
  expect(auth.isAuthenticated).toBe(false); expect(auth.bootstrapped).toBe(true)
  api.fetchCurrentMe.mockRejectedValue(new AuthExpiredError()); localStorage.setItem(SESSION_TOKEN_KEY, 'old')
  await expect(auth.bootstrap()).rejects.toBeInstanceOf(AuthExpiredError); expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBeNull()
  api.signInByNaigos.mockRejectedValueOnce(new Error('外部失败'))
  await expect(auth.signInWithNaigos({ type: 'uid', account: 'u', password: 'pass' })).rejects.toThrow('外部失败'); expect(auth.authenticating).toBe(false)
})
it('网络失败允许重试，权限目录缓存与并发初始化不会重复加载', async () => {
  const auth = useAuthStore(); localStorage.setItem(SESSION_TOKEN_KEY, 'jwt'); api.fetchCurrentMe.mockRejectedValueOnce(new Error('断网'))
  await expect(auth.bootstrap()).rejects.toThrow('断网'); expect(auth.bootstrapped).toBe(true)
  localStorage.setItem(SESSION_TOKEN_KEY, 'new'); await auth.bootstrap(); expect(auth.isAuthenticated).toBe(true)
  const permissions = usePermissionStore()
  await Promise.all([permissions.ensureCatalog(), permissions.ensureCatalog(), permissions.bootstrapManagerContext(), permissions.bootstrapManagerContext()])
  expect(api.fetchAllIdentities).toHaveBeenCalledTimes(1); expect(permissions.identities).toHaveLength(2); expect(permissions.managerPermissionOptions).toHaveLength(2)
  expect(permissions.optionsForIdentity('USER')).toEqual([{ value: 1, label: '会员' }])
  expect(permissions.labelFor('MANAGER', undefined)).toBe('未设置'); expect(permissions.labelFor('MANAGER', 4)).toBe('二级管理员'); expect(permissions.labelFor('MANAGER', 5)).toBe('二级管理员'); expect(permissions.labelFor('MANAGER', 64)).toBe('权限值 64')
})
it('路由同时检查身份和权限，认领的任一位匹配不等同数值大小', async () => {
  const permissions = usePermissionStore(); await Promise.all([permissions.ensureCatalog(), permissions.ensureCatalog()])
  expect(api.fetchAllIdentities).toHaveBeenCalledTimes(1)
  expect(permissions.canAccessRoute({ requiresManager: true, minManagerPermissionBit: 5 }, { ...manager, identity: 'USER' })).toBe(false)
  expect(permissions.canAccessRoute({ requiresManager: true, minManagerPermissionBit: 5 }, { ...manager, permission: 1 })).toBe(true)
  expect(permissions.canAccessRoute({ requiresManager: true, minManagerPermissionBit: 5 }, { ...manager, permission: 8 })).toBe(false)
  expect(permissions.canAccessRoute({ requiresManager: true, minManagerPermission: 16 }, { ...manager, permission: 32 })).toBe(true)
  expect(permissions.canAccessRoute({ requiresManager: true }, null)).toBe(false)
})
