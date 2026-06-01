<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ROUTE_REASON } from '@/app/router/meta'
import { useAuthStore } from '@/app/stores/auth'
import { getErrorMessage } from '@/shared/types/api'
import { showError, showInfo, showSuccess, showWarning } from '@/shared/utils/message'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref<'barc' | 'naigos'>('barc')

const barcForm = reactive({
  type: 'username' as const,
  account: '',
  password: '',
})

const naigosForm = reactive({
  type: 'uid' as const,
  account: '',
  password: '',
})

const reasonMap: Record<string, { type: 'info' | 'warning'; message: string }> = {
  [ROUTE_REASON.AUTH_REQUIRED]: {
    type: 'info',
    message: '请先登录，再进入管理后台。',
  },
  [ROUTE_REASON.MANAGER_ONLY]: {
    type: 'warning',
    message: '只有 identity 为 MANAGER 的账号才能进入这个后台。',
  },
  [ROUTE_REASON.SESSION_EXPIRED]: {
    type: 'warning',
    message: '登录状态已失效，请重新验证身份。',
  },
}

const currentReason = computed(() => {
  const reason = route.query.reason
  return typeof reason === 'string' ? reasonMap[reason] : undefined
})

watch(
  () => route.query.reason,
  (reason) => {
    if (typeof reason !== 'string') {
      return
    }

    const matched = reasonMap[reason]
    if (!matched) {
      return
    }

    if (matched.type === 'warning') {
      showWarning(matched.message)
      return
    }

    showInfo(matched.message)
  },
  { immediate: true },
)

async function afterLogin(): Promise<void> {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
  showSuccess('欢迎回来，已进入 V2 管理后台。')
  await router.replace(redirect)
}

async function submitBarc(): Promise<void> {
  if (!barcForm.account.trim() || !barcForm.password.trim()) {
    showError('请填写完整的账号与密码。')
    return
  }

  try {
    await authStore.signInWithBarc({
      type: barcForm.type,
      account: barcForm.account.trim(),
      password: barcForm.password,
    })
    await afterLogin()
  } catch (error) {
    showError(getErrorMessage(error))
  }
}

async function submitNaigos(): Promise<void> {
  if (!naigosForm.account.trim() || !naigosForm.password.trim()) {
    showError('请填写完整的 Naigos 凭据。')
    return
  }

  try {
    await authStore.signInWithNaigos({
      type: naigosForm.type,
      account: naigosForm.account.trim(),
      password: naigosForm.password,
    })
    await afterLogin()
  } catch (error) {
    showError(getErrorMessage(error))
  }
}
</script>

<template>
  <div class="login-view">
    <section class="login-hero">
      <div class="login-hero__copy">
        <div class="login-hero__eyebrow">BarcManageFrontendV2 · Phase 0</div>
        <h1 class="login-hero__title">保留 B.A.R.C. 后台的轻透气质，重做成更清晰的管理骨架。</h1>
        <p class="login-hero__subtitle">
          这里不是通用中台模板，而是面向当前真实后端契约的 MANAGER 专用入口。
          登录、鉴权、菜单、路由与响应处理都围绕已确认的后台接口收束到同一套结构里。
        </p>

        <div class="login-hero__chips">
          <span class="login-chip">Vue 3 + Vite + TypeScript</span>
          <span class="login-chip">Pinia + Vue Router</span>
          <span class="login-chip">Element Plus</span>
          <span class="login-chip">J 响应外壳统一解包</span>
        </div>
      </div>
    </section>

    <section class="login-panel glass-panel">
      <div class="login-panel__header">
        <div>
          <div class="login-panel__eyebrow">Manager Access Only</div>
          <h2 class="surface-heading">登录管理后台</h2>
        </div>
        <el-alert
          :closable="false"
          type="warning"
          title="只有 current_me.identity === MANAGER 的账号允许进入后台壳。"
        />
      </div>

      <el-alert
        v-if="currentReason"
        :closable="false"
        :type="currentReason.type"
        :title="currentReason.message"
      />

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="BARC 账号" name="barc">
          <el-form label-position="top" @submit.prevent="submitBarc">
            <el-form-item label="登录方式">
              <el-radio-group v-model="barcForm.type">
                <el-radio-button label="username" value="username">用户名</el-radio-button>
                <el-radio-button label="email" value="email">邮箱</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="账号">
              <el-input v-model="barcForm.account" autocomplete="off" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="barcForm.password" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <div class="login-panel__actions">
              <el-button type="primary" native-type="submit" :loading="authStore.authenticating">进入后台</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="Naigos" name="naigos">
          <el-form label-position="top" @submit.prevent="submitNaigos">
            <el-form-item label="登录方式">
              <el-radio-group v-model="naigosForm.type">
                <el-radio-button label="uid" value="uid">UID</el-radio-button>
                <el-radio-button label="email" value="email">邮箱</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="账号">
              <el-input v-model="naigosForm.account" autocomplete="off" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="naigosForm.password" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <div class="login-panel__actions">
              <el-button type="primary" native-type="submit" :loading="authStore.authenticating">用 Naigos 登录</el-button>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.login-view {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 520px);
  gap: 2rem;
  padding: 2rem;
}

.login-hero,
.login-panel {
  min-height: calc(100vh - 4rem);
}

.login-hero {
  display: flex;
  align-items: center;
  padding: 2rem;
}

.login-hero__copy {
  max-width: 720px;
}

.login-hero__eyebrow,
.login-panel__eyebrow {
  margin-bottom: 0.75rem;
  color: var(--barc-accent-strong);
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.login-hero__title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: clamp(2.4rem, 1.4rem + 2vw, 4.2rem);
  line-height: 1.1;
}

.login-hero__subtitle {
  max-width: 52ch;
  margin-top: 1.4rem;
  color: var(--barc-text-soft);
  line-height: 1.9;
  font-size: 1.02rem;
}

.login-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.75rem;
}

.login-chip {
  padding: 0.65rem 0.9rem;
  border: 1px solid var(--barc-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.48);
  box-shadow: var(--barc-shadow-sm);
}

.login-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  justify-content: center;
  padding: 1.6rem;
}

.login-panel__header {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.login-panel__actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1120px) {
  .login-view {
    grid-template-columns: 1fr;
  }

  .login-hero,
  .login-panel {
    min-height: auto;
  }
}
</style>
