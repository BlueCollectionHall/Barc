<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/app/stores/auth'
import { formatRelativeGreeting } from '@/shared/utils/date'
import { showSuccess } from '@/shared/utils/message'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const greeting = computed(() => formatRelativeGreeting())
const title = computed(() => route.meta.title ?? 'BARC Manage V2')
const subtitle = computed(() => route.meta.subtitle ?? '更清晰的 Phase 0 管理入口')
const displayName = computed(() => authStore.userArchive?.nickname ?? '未命名管理员')
const avatar = computed(() => authStore.userArchive?.avatar ?? '')

async function logout(): Promise<void> {
  authStore.clearSession()
  showSuccess('已安全退出后台。')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="app-header glass-panel">
    <div class="app-header__brand">
      <div class="app-header__logo">B.A.R.C</div>
      <div class="app-header__copy">
        <div class="app-header__title">{{ title }}</div>
        <div class="app-header__subtitle">{{ subtitle }}</div>
      </div>
    </div>

    <div class="app-header__user glass-panel">
      <div class="app-header__meta">
        <span class="app-header__greeting">{{ greeting }}</span>
        <strong>{{ displayName }}</strong>
        <span>{{ authStore.managerPermissionLabel }}</span>
      </div>
      <img v-if="avatar" class="app-header__avatar" :src="avatar" alt="avatar" />
      <div v-else class="app-header__avatar app-header__avatar--fallback">{{ displayName.slice(0, 1) }}</div>
      <el-button plain @click="logout">退出登录</el-button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  min-height: var(--barc-header-height);
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.95rem 1.1rem;
}

.app-header__brand,
.app-header__user {
  display: flex;
  align-items: center;
  gap: 0.9rem;
}

.app-header__logo {
  font-family: var(--barc-font-display);
  font-size: 1.6rem;
  letter-spacing: 0.18em;
}

.app-header__copy {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.app-header__title {
  font-weight: 600;
}

.app-header__subtitle,
.app-header__greeting,
.app-header__meta span {
  color: var(--barc-text-soft);
  font-size: 0.9rem;
}

.app-header__user {
  padding: 0.45rem 0.55rem 0.45rem 0.8rem;
  background: var(--barc-surface-strong);
}

.app-header__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.app-header__avatar {
  width: 48px;
  height: 48px;
  border: 2px solid rgba(255, 255, 255, 0.56);
  border-radius: 18px;
  object-fit: cover;
  box-shadow: var(--barc-shadow-sm);
}

.app-header__avatar--fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(145deg, rgba(95, 136, 173, 0.85), rgba(170, 193, 214, 0.92));
  color: white;
  font-weight: 700;
}

@media (max-width: 960px) {
  .app-header {
    flex-direction: column;
    align-items: stretch;
  }

  .app-header__user {
    justify-content: space-between;
  }

  .app-header__meta {
    align-items: flex-start;
  }
}
</style>
