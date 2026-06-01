import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchBasicMe, fetchCurrentMe, signIn, signInByNaigos } from '@/modules/auth/api/auth.service'
import { SESSION_TOKEN_KEY } from '@/shared/constants/auth'
import { getManagerPermissionLabel, isManagerIdentity } from '@/shared/constants/permissions'
import { AuthExpiredError, ManagerOnlyError } from '@/shared/types/api'
import type { BarcLoginType, NaigosLoginType, UserArchive, UserBasic } from '@/shared/types/user'
import { usePermissionStore } from '@/app/stores/permissions'

type SessionReason = 'signin' | 'restore'

let sessionListenersBound = false

function readStoredToken(): string | null {
  return window.localStorage.getItem(SESSION_TOKEN_KEY)
}

export const useAuthStore = defineStore('auth-store', () => {
  const token = ref<string | null>(readStoredToken())
  const userArchive = ref<UserArchive | null>(null)
  const userBasic = ref<UserBasic | null>(null)
  const bootstrapped = ref(false)
  const authenticating = ref(false)
  const bootstrapPromise = ref<Promise<boolean> | null>(null)

  const permissionStore = usePermissionStore()

  function bindSessionListeners(): void {
    if (sessionListenersBound) {
      return
    }

    const syncFromStorage = (): void => {
      token.value = readStoredToken()
      if (!token.value) {
        userArchive.value = null
        userBasic.value = null
        permissionStore.reset()
        bootstrapped.value = true
      }
    }

    window.addEventListener('storage', syncFromStorage)
    window.addEventListener('barc:auth-changed', syncFromStorage)
    sessionListenersBound = true
  }

  bindSessionListeners()

  function persistToken(nextToken: string): void {
    window.localStorage.setItem(SESSION_TOKEN_KEY, nextToken)
    token.value = nextToken
    window.dispatchEvent(new CustomEvent('barc:auth-changed'))
  }

  function clearSession(): void {
    window.localStorage.removeItem(SESSION_TOKEN_KEY)
    token.value = null
    userArchive.value = null
    userBasic.value = null
    permissionStore.reset()
    bootstrapped.value = true
    window.dispatchEvent(new CustomEvent('barc:auth-changed'))
  }

  async function bootstrap(reason: SessionReason = 'restore'): Promise<boolean> {
    token.value = readStoredToken()

    if (!token.value) {
      clearSession()
      return false
    }

    if (bootstrapPromise.value) {
      return bootstrapPromise.value
    }

    authenticating.value = true
    bootstrapped.value = false
    bootstrapPromise.value = (async () => {
      try {
        const [archive, basic] = await Promise.all([fetchCurrentMe(), fetchBasicMe()])

        if (!isManagerIdentity(archive.identity)) {
          clearSession()
          throw new ManagerOnlyError()
        }

        userArchive.value = archive
        userBasic.value = basic
        await permissionStore.bootstrapManagerContext()
        return true
      } catch (error) {
        clearSession()
        if (reason === 'signin' && error instanceof ManagerOnlyError) {
          throw error
        }

        if (error instanceof AuthExpiredError || error instanceof ManagerOnlyError) {
          throw error
        }

        throw error
      } finally {
        authenticating.value = false
        bootstrapped.value = true
        bootstrapPromise.value = null
      }
    })()

    return bootstrapPromise.value
  }

  async function signInWithBarc(payload: { type: BarcLoginType; account: string; password: string }): Promise<void> {
    authenticating.value = true

    try {
      const nextToken = await signIn(payload)
      persistToken(nextToken)
      await bootstrap('signin')
    } catch (error) {
      clearSession()
      throw error
    } finally {
      authenticating.value = false
    }
  }

  async function signInWithNaigos(payload: { type: NaigosLoginType; account: string; password: string }): Promise<void> {
    authenticating.value = true

    try {
      const nextToken = await signInByNaigos(payload)
      persistToken(nextToken)
      await bootstrap('signin')
    } catch (error) {
      clearSession()
      throw error
    } finally {
      authenticating.value = false
    }
  }

  const isAuthenticated = computed(() => Boolean(token.value && userArchive.value && userBasic.value))
  const isManager = computed(() => isManagerIdentity(userArchive.value?.identity))
  const managerPermissionLabel = computed(() => getManagerPermissionLabel(userArchive.value?.permission))

  return {
    token,
    userArchive,
    userBasic,
    bootstrapped,
    authenticating,
    isAuthenticated,
    isManager,
    managerPermissionLabel,
    bootstrap,
    signInWithBarc,
    signInWithNaigos,
    clearSession,
  }
})
