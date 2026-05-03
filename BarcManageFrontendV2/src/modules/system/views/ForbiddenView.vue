<script setup lang="ts">
import { useRouter } from 'vue-router'

import RoutePageShell from '@/app/components/RoutePageShell.vue'

const router = useRouter()
</script>

<template>
  <RoutePageShell
    eyebrow="403"
    title="无权限访问"
    subtitle="你已经通过登录校验，但当前账号没有进入这个模块的前端能力。"
  >
    <section class="glass-panel forbidden-card">
      <div class="forbidden-card__icon">✦</div>
      <div class="forbidden-card__copy">
        <strong>这个页面仍然受后台能力边界保护。</strong>
        <p>
          V2 的路由守卫会区分未登录、非 MANAGER、以及 MANAGER 但不具备模块能力三种状态。
          你现在处于第三种情况，因此被引导到了 403 页面而不是直接回到登录页。
        </p>
      </div>

      <div class="forbidden-card__actions">
        <el-button @click="router.back()">返回上一页</el-button>
        <el-button type="primary" @click="router.push({ name: 'dashboard' })">回到控制台</el-button>
      </div>
    </section>
  </RoutePageShell>
</template>

<style scoped>
.forbidden-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
  padding: 2rem;
  text-align: center;
}

.forbidden-card__icon {
  width: 72px;
  height: 72px;
  display: grid;
  place-items: center;
  border-radius: 26px;
  background: linear-gradient(145deg, rgba(95, 136, 173, 0.15), rgba(255, 246, 235, 0.75));
  font-size: 2rem;
}

.forbidden-card__copy {
  max-width: 52ch;
  color: var(--barc-text-soft);
  line-height: 1.8;
}

.forbidden-card__copy strong {
  color: var(--barc-text);
}

.forbidden-card__actions {
  display: flex;
  gap: 0.75rem;
}
</style>
