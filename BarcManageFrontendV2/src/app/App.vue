<script setup lang="ts">
import { computed, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const shouldShowLoadingMask = computed(
  () => route.meta.requiresAuth && !authStore.bootstrapped && authStore.authenticating,
)

watch(
  () => authStore.isAuthenticated,
  async (isAuthenticated) => {
    if (!isAuthenticated && route.meta.requiresAuth) {
      await router.replace({ name: 'login' })
    }
  },
)
</script>

<template>
  <RouterView v-slot="{ Component, route: currentRoute }">
    <Transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="currentRoute.fullPath" />
    </Transition>
  </RouterView>

  <Transition name="fade-slide">
    <div v-if="shouldShowLoadingMask" class="app-loading-mask">
      <div class="app-loading-card glass-panel">
        <div class="app-loading-label">正在恢复后台会话…</div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.app-loading-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(236, 242, 247, 0.58);
  backdrop-filter: blur(10px);
}

.app-loading-card {
  min-width: 260px;
  padding: 1rem 1.4rem;
}

.app-loading-label {
  color: var(--barc-text-soft);
  letter-spacing: 0.08em;
}
</style>
