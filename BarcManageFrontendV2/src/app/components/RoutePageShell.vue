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
        <div class="page-shell__title-row">
          <h1 class="surface-heading">{{ title }}</h1>
          <div v-if="eyebrow" class="page-shell__eyebrow">
            <span class="page-shell__divider" />
            {{ eyebrow }}
          </div>
        </div>
        <div v-if="subtitle || $slots.subtitle" class="surface-subtitle">
          <slot name="subtitle">{{ subtitle }}</slot>
        </div>
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
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.85rem 1.4rem;
}

.page-shell__title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-shell__header .surface-heading {
  font-size: clamp(1.15rem, 0.85rem + 1vw, 1.6rem);
}

.page-shell__eyebrow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.15em;
  color: var(--barc-accent-strong);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.page-shell__divider {
  width: 2px;
  height: 1.1em;
  background-color: var(--barc-accent-strong);
  border-radius: 1px;
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
