import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/app/stores/auth'
import { usePermissionStore } from '@/app/stores/permissions'
import { ROUTE_REASON } from '@/app/router/meta'
import { routes } from '@/app/router/routes'
import { ManagerOnlyError } from '@/shared/types/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const permissionStore = usePermissionStore()

  if (to.meta.requiresAuth || authStore.token) {
    try {
      await authStore.bootstrap()
    } catch (error) {
      if (to.name !== 'login' && to.meta.requiresAuth) {
        return {
          name: 'login',
          query: {
            redirect: to.fullPath,
            reason: error instanceof ManagerOnlyError ? ROUTE_REASON.MANAGER_ONLY : ROUTE_REASON.SESSION_EXPIRED,
          },
        }
      }
    }
  }

  if (to.name === 'login') {
    if (authStore.isAuthenticated && authStore.isManager) {
      const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : undefined
      return redirect || { name: 'dashboard' }
    }

    return true
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
        reason: ROUTE_REASON.AUTH_REQUIRED,
      },
    }
  }

  if (to.meta.requiresManager && !authStore.isManager) {
    authStore.clearSession()
    return {
      name: 'login',
      query: {
        reason: ROUTE_REASON.MANAGER_ONLY,
      },
    }
  }

  if (to.name !== 'forbidden' && !permissionStore.canAccessRoute(to.meta, authStore.userArchive)) {
    return { name: 'forbidden' }
  }

  return true
})

export default router
