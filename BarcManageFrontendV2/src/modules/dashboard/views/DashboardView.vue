<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import { adminChildren } from '@/app/router/routes'
import { useAuthStore } from '@/app/stores/auth'
import { usePermissionStore } from '@/app/stores/permissions'

const router = useRouter()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

const quickLinks = computed(() =>
  adminChildren.filter(
    (route) => route.meta?.menuLabel && !route.meta.hiddenInMenu && permissionStore.canAccessRoute(route.meta, authStore.userArchive),
  ),
)

const managerSummary = computed(() => ({
  nickname: authStore.userArchive?.nickname ?? '未登录',
  username: authStore.userBasic?.username ?? '—',
  identity: authStore.userArchive?.identity ?? '—',
  permission: permissionStore.labelFor('MANAGER', authStore.userArchive?.permission ?? null),
}))
</script>

<template>
  <RoutePageShell
    eyebrow="Phase 0"
    title="控制台"
    subtitle="先把后台壳、权限路径和核心模块站稳，再在后续阶段继续扩展。"
  >
    <div class="dashboard-grid">
      <section class="glass-panel dashboard-card dashboard-card--hero">
        <div>
          <div class="dashboard-card__eyebrow">当前会话</div>
          <h2 class="dashboard-card__title">{{ managerSummary.nickname }}</h2>
          <p class="dashboard-card__copy">
            你正在以 {{ managerSummary.identity }} 身份进入后台，用户名为 {{ managerSummary.username }}，当前能力层级是
            {{ managerSummary.permission }}。
          </p>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ managerSummary.username }}</el-descriptions-item>
          <el-descriptions-item label="身份">{{ managerSummary.identity }}</el-descriptions-item>
          <el-descriptions-item label="权限">{{ managerSummary.permission }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="glass-panel dashboard-card">
        <div class="dashboard-card__eyebrow">入口</div>
        <h2 class="dashboard-card__title">可访问模块</h2>
        <div class="quick-links">
          <button
            v-for="item in quickLinks"
            :key="String(item.name)"
            class="quick-link"
            type="button"
            @click="router.push({ name: item.name })"
          >
            <strong>{{ item.meta?.menuLabel }}</strong>
            <span>{{ item.meta?.subtitle }}</span>
          </button>
        </div>
      </section>

      <section class="glass-panel dashboard-card">
        <div class="dashboard-card__eyebrow">边界</div>
        <h2 class="dashboard-card__title">Phase 0 约束</h2>
        <ul class="dashboard-list">
          <li>不虚构统计接口，首页只承接已有模块与当前身份信息。</li>
          <li>菜单与模块来源统一收敛到 route meta，避免壳层重复维护。</li>
          <li>鉴权只使用已确认的登录与当前用户接口，MANAGER 之外一律清会话返回登录页。</li>
        </ul>
      </section>
    </div>
  </RoutePageShell>
</template>

<style scoped>
.dashboard-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 1rem;
}

.dashboard-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.4rem;
}

.dashboard-card--hero {
  grid-row: span 2;
}

.dashboard-card__eyebrow {
  color: var(--barc-accent-strong);
  font-size: 0.8rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.dashboard-card__title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: 1.6rem;
}

.dashboard-card__copy {
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.8;
}

.quick-links {
  display: grid;
  gap: 0.8rem;
}

.quick-link {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 1rem;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.58);
  text-align: left;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.quick-link:hover {
  transform: translateY(-2px);
  box-shadow: var(--barc-shadow-sm);
}

.quick-link span,
.dashboard-list {
  color: var(--barc-text-soft);
}

.dashboard-list {
  display: grid;
  gap: 0.8rem;
  margin: 0;
  padding-left: 1.1rem;
  line-height: 1.8;
}

@media (max-width: 980px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
