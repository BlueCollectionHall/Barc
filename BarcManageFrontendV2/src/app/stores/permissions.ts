import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { hasMinimumManagerPermission, isManagerIdentity } from '@/shared/constants/permissions'
import type { PermissionOption } from '@/shared/types/user'
import type { IdentityOption, UserArchive, UserIdentity } from '@/shared/types/user'
import { fetchAllIdentities, fetchMyPermissionNearMax, fetchPermissionsByIdentity } from '@/modules/users/api/users.service'

type PermissionCatalog = Record<UserIdentity, PermissionOption[]>

const emptyCatalog = (): PermissionCatalog => ({
  USER: [],
  MANAGER: [],
})

export const usePermissionStore = defineStore('permission-store', () => {
  const identities = ref<IdentityOption[]>([])
  const permissionCatalog = ref<PermissionCatalog>(emptyCatalog())
  const myPermissionNearMax = ref<PermissionOption | null>(null)
  const catalogLoaded = ref(false)
  const catalogPromise = ref<Promise<void> | null>(null)
  const managerContextPromise = ref<Promise<void> | null>(null)

  async function ensureCatalog(): Promise<void> {
    if (catalogLoaded.value) {
      return
    }

    if (catalogPromise.value) {
      return catalogPromise.value
    }

    catalogPromise.value = (async () => {
      const [identityOptions, userOptions, managerOptions] = await Promise.all([
        fetchAllIdentities(),
        fetchPermissionsByIdentity('USER'),
        fetchPermissionsByIdentity('MANAGER'),
      ])

      identities.value = identityOptions
      permissionCatalog.value = {
        USER: userOptions,
        MANAGER: managerOptions,
      }
      catalogLoaded.value = true
    })()

    try {
      await catalogPromise.value
    } finally {
      catalogPromise.value = null
    }
  }

  async function bootstrapManagerContext(): Promise<void> {
    if (managerContextPromise.value) {
      return managerContextPromise.value
    }

    managerContextPromise.value = (async () => {
      await ensureCatalog()
      myPermissionNearMax.value = await fetchMyPermissionNearMax()
    })()

    try {
      await managerContextPromise.value
    } finally {
      managerContextPromise.value = null
    }
  }

  function optionsForIdentity(identity: UserIdentity): PermissionOption[] {
    return permissionCatalog.value[identity] ?? []
  }

  function labelFor(identity: UserIdentity, permission: number | null | undefined): string {
    if (permission === null || permission === undefined) {
      return '未设置'
    }

    const options = optionsForIdentity(identity)
    const exact = options.find((option) => option.value === permission)
    if (exact) {
      return exact.label
    }

    const sorted = [...options].sort((a, b) => b.value - a.value)
    for (const option of sorted) {
      if ((permission & option.value) === option.value) {
        return option.label
      }
    }

    return `权限值 ${permission}`
  }

  function canAccessRoute(meta: { requiresManager?: boolean; minManagerPermission?: number }, user: UserArchive | null): boolean {
    if (meta.requiresManager && !isManagerIdentity(user?.identity)) {
      return false
    }

    return hasMinimumManagerPermission(user?.permission, meta.minManagerPermission)
  }

  function reset(): void {
    myPermissionNearMax.value = null
  }

  const managerPermissionOptions = computed(() => permissionCatalog.value.MANAGER)

  return {
    identities,
    myPermissionNearMax,
    managerPermissionOptions,
    ensureCatalog,
    bootstrapManagerContext,
    optionsForIdentity,
    labelFor,
    canAccessRoute,
    reset,
  }
})
