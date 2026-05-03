<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  subtitle?: string
}>()
</script>

<template>
  <section class="page-shell">
    <header class="page-shell__header glass-panel">
      <div>
        <div v-if="eyebrow" class="page-shell__eyebrow">{{ eyebrow }}</div>
        <h1 class="surface-heading">{{ title }}</h1>
        <p v-if="subtitle" class="surface-subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="page-shell__actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="page-shell__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-shell {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 1rem;
}

.page-shell__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.4rem;
}

.page-shell__eyebrow {
  margin-bottom: 0.45rem;
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.page-shell__actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-shell__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 1rem;
}

@media (max-width: 900px) {
  .page-shell__header {
    flex-direction: column;
  }
}
</style>
