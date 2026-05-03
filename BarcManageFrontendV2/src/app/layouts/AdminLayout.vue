<script setup lang="ts">
import AppHeader from '@/app/components/AppHeader.vue'
import AppSidebar from '@/app/components/AppSidebar.vue'
</script>

<template>
  <div class="admin-layout">
    <AppHeader />
    <div class="admin-layout__body">
      <AppSidebar class="admin-layout__sidebar" />
      <main class="admin-layout__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}

.admin-layout__body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(240px, var(--barc-sidebar-width)) minmax(0, 1fr);
  gap: 1rem;
}

.admin-layout__main {
  min-width: 0;
  min-height: calc(100vh - var(--barc-header-height) - 2rem);
}

@media (max-width: 1080px) {
  .admin-layout__body {
    grid-template-columns: 1fr;
  }
}
</style>
