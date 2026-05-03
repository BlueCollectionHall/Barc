<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminChildren } from '@/app/router/routes'
import { useAuthStore } from '@/app/stores/auth'
import { usePermissionStore } from '@/app/stores/permissions'

interface MenuItem {
  name: string
  label: string
}

interface MenuGroup {
  key: string
  label: string
  order: number
  items: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()

const menuGroups = computed<MenuGroup[]>(() => {
  const grouped = new Map<string, MenuGroup>()

  adminChildren.forEach((record) => {
    if (record.meta?.hiddenInMenu || !record.name || !record.meta?.menuLabel) {
      return
    }

    if (!permissionStore.canAccessRoute(record.meta, authStore.userArchive)) {
      return
    }

    const groupKey = record.meta.menuGroup ?? 'default'
    const currentGroup = grouped.get(groupKey) ?? {
      key: groupKey,
      label: record.meta.menuGroupLabel ?? '导航',
      order: record.meta.groupOrder ?? 999,
      items: [],
    }

    currentGroup.items.push({
      name: String(record.name),
      label: record.meta.menuLabel,
    })
    grouped.set(groupKey, currentGroup)
  })

  return [...grouped.values()]
    .map((group) => ({
      ...group,
      items: group.items.sort((left, right) => {
        const leftRoute = adminChildren.find((record) => String(record.name) === left.name)
        const rightRoute = adminChildren.find((record) => String(record.name) === right.name)
        return (leftRoute?.meta?.menuOrder ?? 999) - (rightRoute?.meta?.menuOrder ?? 999)
      }),
    }))
    .sort((left, right) => left.order - right.order)
})

async function handleSelect(routeName: string): Promise<void> {
  await router.push({ name: routeName })
}
</script>

<template>
  <aside class="app-sidebar glass-panel">
    <div class="app-sidebar__surface">
      <div class="app-sidebar__intro">
        <div class="app-sidebar__kicker">Manage V2</div>
        <strong>Phase 0 导航</strong>
        <span>菜单直接由 route meta 生成，避免壳层和路由配置脱节。</span>
      </div>

      <el-scrollbar>
        <div class="app-sidebar__groups">
          <section v-for="group in menuGroups" :key="group.key" class="app-sidebar__group">
            <div class="app-sidebar__group-label">{{ group.label }}</div>
            <button
              v-for="item in group.items"
              :key="item.name"
              class="app-sidebar__link"
              :class="{ 'is-active': route.name === item.name }"
              type="button"
              @click="handleSelect(item.name)"
            >
              <span>{{ item.label }}</span>
            </button>
          </section>
        </div>
      </el-scrollbar>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  min-height: calc(100vh - var(--barc-header-height) - 2rem);
  padding: 0;
  overflow: hidden;
  background: var(--barc-surface-sidebar);
  color: var(--barc-text-inverse);
}

.app-sidebar__surface {
  display: flex;
  height: 100%;
  flex-direction: column;
}

.app-sidebar__intro {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 1.4rem 1.2rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.app-sidebar__intro span,
.app-sidebar__group-label {
  color: rgba(247, 251, 255, 0.75);
}

.app-sidebar__kicker {
  font-size: 0.78rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.app-sidebar__groups {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}

.app-sidebar__group {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.app-sidebar__group-label {
  padding-inline: 0.65rem;
  font-size: 0.82rem;
  letter-spacing: 0.08em;
}

.app-sidebar__link {
  position: relative;
  width: 100%;
  padding: 0.88rem 0.9rem;
  border: 1px solid transparent;
  border-radius: 18px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.22s ease;
}

.app-sidebar__link:hover,
.app-sidebar__link.is-active {
  border-color: rgba(255, 255, 255, 0.18);
  background: rgba(255, 255, 255, 0.12);
  transform: translateX(4px);
}

.app-sidebar__link.is-active::before {
  content: '';
  position: absolute;
  top: 18%;
  bottom: 18%;
  left: -0.25rem;
  width: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
}
</style>
