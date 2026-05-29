import type { ValueLabel } from '@/shared/types/api'

export type UserIdentity = 'USER' | 'MANAGER'
export type BarcLoginType = 'username' | 'email'
export type NaigosLoginType = 'uid' | 'email'

export interface UserArchive {
  uuid: string
  nickname: string
  avatar: string | null
  gender: number | null
  birthday: string | null
  age: number | null
  permission: number
  identity: UserIdentity
  updated_at: string | null
}

export interface UserBasic {
  uuid: string
  username: string
  password: string
  password_version: number | null
  email: string
  email_verified: boolean
  telephone: string | null
  safe_level: number | null
  created_at: string | null
  updated_at: string | null
}

export interface UserListItem {
  uuid: string
  username: string
  nickname: string
  avatar: string | null
  age: number | null
  birthday: string | null
  gender: number | null
  identity: UserIdentity
  permission: number
  safe_level: number | null
}

export interface UserListFilters extends Record<string, unknown> {
  username?: string
  nickname?: string
  identity?: UserIdentity
  permission?: number
}

export interface UserQuickCreatePayload {
  username: string
  password: string
  email: string
  telephone: string
  safe_level: number
  email_verified: boolean
}

export interface UserPermissionChangePayload {
  uuid: string
  identity: UserIdentity
  permission: number
}

export type IdentityOption = ValueLabel<UserIdentity>
export type PermissionOption = ValueLabel<number>

// 封号相关类型
export type BanType = 0 | 1 | 2 | 3

export interface BanRequest {
  userId: string
  banType: BanType
  reason: string
  durationDays?: number
}

export interface UnbanRequest {
  userId: string
  reason: string
}

export interface BanRecord {
  id: number
  userId: string
  banType: BanType
  banReason: string
  banDurationDays: number | null
  bannedAt: string
  unbannedAt: string | null
  unbanReason: string | null
  operatorId: string
  operatorType: number
  safeLevelBeforeBan: number
  safeLevelAfterBan: number
  createdAt: string
  updatedAt: string
}

export interface UserBanStatus {
  userId: string
  safeLevel: number | null
  safeLevelBeforeBan: number | null
  banStatus: string
  banReason: string | null
  banTime: string | null
  unbanTime: string | null
}

export const BAN_TYPE_LABELS: Record<BanType, string> = {
  0: '风险冻结',
  1: '临时封号',
  2: '违规封号',
  3: '软删除',
}

export const SAFE_LEVEL_STATUS: Record<number, string> = {
  0: '风险冻结',
  [-1]: '临时封号',
  [-2]: '违规封号',
  [-3]: '软删除',
}
