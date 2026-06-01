<script setup lang="ts">
import AppHeader from '@/app/components/AppHeader.vue'
import AppSidebar from '@/app/components/AppSidebar.vue'
</script>

<template>
  <div class="admin-layout">
    <AppSidebar class="admin-layout__sidebar" />
    <section class="admin-layout__content">
      <AppHeader class="admin-layout__header" />
      <main class="admin-layout__main">
        <router-view v-slot="{ Component, route }">
          <div class="route-transition-stage">
            <Transition name="fade-slide" mode="out-in">
              <div :key="route.fullPath" class="route-transition-view">
                <component :is="Component" />
              </div>
            </Transition>
          </div>
        </router-view>
      </main>
    </section>
  </div>
</template>

<style scoped>
.admin-layout {
  display: grid;
  height: 100vh;
  height: 100dvh;
  align-items: stretch;
  grid-template-columns: minmax(240px, var(--barc-sidebar-width)) minmax(0, 1fr);
  grid-template-areas: 'sidebar content';
  gap: var(--barc-space-4);
  padding: var(--barc-space-4);
  overflow: hidden;
}

.admin-layout__sidebar {
  grid-area: sidebar;
  min-width: 0;
  min-height: 0;
}

.admin-layout__content {
  grid-area: content;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  gap: var(--barc-space-4);
}

.admin-layout__header {
  min-width: 0;
  flex-shrink: 0;
}

.admin-layout__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.route-transition-stage {
  position: relative;
  min-height: 100%;
}

.route-transition-view {
  min-height: 100%;
}

@media (max-width: 1080px) {
  .admin-layout {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 3fr) minmax(0, 2fr);
    grid-template-areas:
      'content'
      'sidebar';
  }
}
</style>
