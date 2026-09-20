<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { adminChildren } from '@/app/router/routes'
import { useAuthStore } from '@/app/stores/auth'
import { usePermissionStore } from '@/app/stores/permissions'

/**
 * 管理台侧边栏
 *
 * 职责：
 * 1、把 adminChildren 的路由 meta 聚合成分组菜单（组本身没有权限字段，是否渲染由组内可见项派生）；
 * 2、提供分组的展开 / 收起交互；
 * 3、标记当前激活菜单项所属的组，避免收起状态下用户丢失位置感。
 */

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

/**
 * 已展开的分组 key 集合。
 *
 * 设计约定（2026-09-20 拍板）：
 * 1、初始为空集合 = 全部收起（包含当前激活路由所在的那一组）；
 * 2、只由用户点击改写，不随路由变化联动；
 * 3、不写入 sessionStorage / localStorage，刷新即回到默认。
 */
const expandedGroups = ref<Set<string>>(new Set())

/**
 * 菜单项排序权重索引。
 *
 * 组内排序原先在比较函数里对 adminChildren 反复 find，这里预先建索引，
 * 排序结果与原先完全一致（找不到时同样回落到 999）。
 */
const menuOrderIndex = new Map<string, number>()

adminChildren.forEach((record) => {
  if (record.name) {
    menuOrderIndex.set(String(record.name), record.meta?.menuOrder ?? 999)
  }
})

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
      items: group.items.sort(
        (left, right) => (menuOrderIndex.get(left.name) ?? 999) - (menuOrderIndex.get(right.name) ?? 999),
      ),
    }))
    .sort((left, right) => left.order - right.order)
})

/**
 * 当前激活路由所属的分组 key。
 *
 * 无分组归属的二级页（公告发布 / 编辑、作品详情、部团 / 学生列表、403）返回 null，
 * 此时不会有任何分组被高亮——与"进入隐藏页不改变折叠状态"的约定保持一致。
 */
const activeGroupKey = computed<string | null>(() => {
  const matched = menuGroups.value.find((group) => group.items.some((item) => item.name === route.name))
  return matched ? matched.key : null
})

/** 折叠面板的 DOM id，供 aria-controls 引用 */
function panelId(groupKey: string): string {
  return `sidebar-group-${groupKey}`
}

function isGroupExpanded(groupKey: string): boolean {
  return expandedGroups.value.has(groupKey)
}

/** 切换分组展开状态：折叠状态的唯一写入口 */
function toggleGroup(groupKey: string): void {
  const next = new Set(expandedGroups.value)
  if (next.has(groupKey)) {
    next.delete(groupKey)
  } else {
    next.add(groupKey)
  }
  expandedGroups.value = next
}

async function handleSelect(routeName: string): Promise<void> {
  await router.push({ name: routeName })
}
</script>

<template>
  <aside class="app-sidebar">
    <div class="app-sidebar__surface">
      <div class="app-sidebar__groups">
        <section
          v-for="group in menuGroups"
          :key="group.key"
          class="app-sidebar__group"
          :class="{ 'is-active-group': group.key === activeGroupKey }"
        >
          <button
            class="app-sidebar__group-toggle"
            type="button"
            :aria-expanded="isGroupExpanded(group.key)"
            :aria-controls="panelId(group.key)"
            @click="toggleGroup(group.key)"
          >
            <span
              class="app-sidebar__group-arrow"
              :class="{ 'is-open': isGroupExpanded(group.key) }"
              aria-hidden="true"
            />
            <span class="app-sidebar__group-label">{{ group.label }}</span>
          </button>

          <div
            v-if="isGroupExpanded(group.key)"
            :id="panelId(group.key)"
            class="app-sidebar__group-panel"
            role="group"
          >
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
          </div>
        </section>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-md);
  background: var(--barc-surface-sidebar);
  box-shadow: var(--barc-shadow-md);
  color: var(--barc-text-inverse);
  isolation: isolate;
}

.app-sidebar__surface {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.app-sidebar__groups {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.22) transparent;
}

.app-sidebar__groups::-webkit-scrollbar {
  width: 6px;
}

.app-sidebar__groups::-webkit-scrollbar-track {
  background: transparent;
}

.app-sidebar__groups::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
}

.app-sidebar__group {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.app-sidebar__group-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.65rem;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  color: rgba(247, 251, 255, 0.75);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.22s ease, border-color 0.22s ease, color 0.22s ease;
}

.app-sidebar__group-toggle:hover {
  border-color: rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  color: var(--barc-text-inverse);
}

/* 无障碍：原有样式没有焦点态，这里补一个可见轮廓，保证键盘可用 */
.app-sidebar__group-toggle:focus-visible {
  outline: 2px solid rgba(255, 255, 255, 0.72);
  outline-offset: 2px;
}

.app-sidebar__group-arrow {
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 6px solid currentColor;
  opacity: 0.7;
  transition: transform 0.2s ease;
}

.app-sidebar__group-arrow.is-open {
  transform: rotate(90deg);
}

.app-sidebar__group-label {
  font-size: 0.82rem;
  letter-spacing: 0.08em;
}

/* 当前激活路由所在组：默认全收起时，这是"我在哪"的唯一指示 */
.app-sidebar__group.is-active-group .app-sidebar__group-toggle {
  color: var(--barc-text-inverse);
}

.app-sidebar__group.is-active-group .app-sidebar__group-label {
  font-weight: 600;
}

.app-sidebar__group.is-active-group::before {
  content: '';
  position: absolute;
  top: 0.15rem;
  bottom: 0.15rem;
  left: -0.35rem;
  width: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.6);
}

.app-sidebar__group-panel {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  animation: sidebar-group-panel-in 0.18s ease;
}

@keyframes sidebar-group-panel-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
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
  transition: background-color 0.22s ease, border-color 0.22s ease, transform 0.22s ease;
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
