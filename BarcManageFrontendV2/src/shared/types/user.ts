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
