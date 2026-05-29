import { http } from '@/shared/api/http'
import type { PageRequest, PageResult, ValueLabel } from '@/shared/types/api'
import type {
  BanRecord,
  BanRequest,
  IdentityOption,
  PermissionOption,
  UnbanRequest,
  UserBanStatus,
  UserIdentity,
  UserListFilters,
  UserListItem,
  UserPermissionChangePayload,
  UserQuickCreatePayload,
} from '@/shared/types/user'

export function queryUsersByPage(payload: PageRequest<UserListFilters>): Promise<PageResult<UserListItem>> {
  return http.post<PageResult<UserListItem>>('/user/query/all_by_page', payload)
}

export function uploadNewUser(payload: UserQuickCreatePayload): Promise<string> {
  return http.post<string>('/user/manage/upload_new_user', payload)
}

export function fetchAllIdentities(): Promise<IdentityOption[]> {
  return http.get<IdentityOption[]>('/user/permission/all_identities')
}

export function fetchPermissionsByIdentity(identity: UserIdentity): Promise<PermissionOption[]> {
  return http.get<PermissionOption[]>('/user/permission/permissions_by_identity', {
    params: { identity },
  })
}

export function fetchMyPermissionNearMax(): Promise<ValueLabel<number>> {
  return http.get<ValueLabel<number>>('/user/permission/me_permission_near_max')
}

export function changePermission(payload: UserPermissionChangePayload): Promise<string> {
  return http.post<string>('/user/permission/change_permission', payload)
}

// 封号相关API
export function banUser(payload: BanRequest): Promise<string> {
  return http.post<string>('/user/ban', payload)
}

export function unbanUser(payload: UnbanRequest): Promise<string> {
  return http.post<string>('/user/ban/unban', payload)
}

export function getBanHistory(userId: string, page: number = 1, size: number = 10): Promise<PageResult<BanRecord>> {
  return http.get<PageResult<BanRecord>>('/user/ban/history', {
    params: { userId, page, size },
  })
}

export function getUserBanStatus(userId: string): Promise<UserBanStatus> {
  return http.get<UserBanStatus>('/user/ban/status', {
    params: { userId },
  })
}
