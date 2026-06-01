import type { UserIdentity } from '@/shared/types/user'

export const MANAGER_PERMISSION = {
  DISCIPLINARY_COMMITTEE: 1,
  FIR_MAINTAINER: 1 << 1,
  SEC_MAINTAINER: 1 << 2,
  THI_MAINTAINER: 1 << 3,
  ADMINISTRATOR: 1 << 4,
  ADVANCED_ADMINISTRATOR: 1 << 5,
} as const

export const MANAGER_PERMISSION_LABELS: Record<number, string> = {
  [MANAGER_PERMISSION.DISCIPLINARY_COMMITTEE]: '风纪委员',
  [MANAGER_PERMISSION.FIR_MAINTAINER]: '一级管理员',
  [MANAGER_PERMISSION.SEC_MAINTAINER]: '二级管理员',
  [MANAGER_PERMISSION.THI_MAINTAINER]: '三级管理员',
  [MANAGER_PERMISSION.ADMINISTRATOR]: '副馆长',
  [MANAGER_PERMISSION.ADVANCED_ADMINISTRATOR]: '馆长',
}

export function isManagerIdentity(identity: UserIdentity | string | null | undefined): identity is 'MANAGER' {
  return identity === 'MANAGER'
}

export function hasMinimumManagerPermission(currentPermission: number | null | undefined, required: number | undefined): boolean {
  if (required === undefined) {
    return true
  }

  return (currentPermission ?? 0) >= required
}

/** 位运算检查：判断权限值中是否包含指定位 */
export function hasManagerPermissionBit(currentPermission: number | null | undefined, requiredBit: number): boolean {
  if (requiredBit === undefined || currentPermission === null || currentPermission === undefined) {
    return false
  }
  return (currentPermission & requiredBit) !== 0
}

export function getManagerPermissionLabel(permission: number | null | undefined): string {
  if (permission === null || permission === undefined) {
    return '未设置'
  }

  const exact = MANAGER_PERMISSION_LABELS[permission]
  if (exact) {
    return exact
  }

  const sorted = Object.entries(MANAGER_PERMISSION_LABELS)
    .map(([value, label]) => ({ value: Number(value), label }))
    .sort((a, b) => b.value - a.value)

  for (const item of sorted) {
    if ((permission & item.value) === item.value) {
      return item.label
    }
  }

  return `权限值 ${permission}`
}
