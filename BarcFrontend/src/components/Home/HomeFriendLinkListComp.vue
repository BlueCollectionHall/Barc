<script setup lang="ts">
import type { FriendLinkItem } from "@/utils/friendLinkApi.ts";

/**
 * 友情链接列表（纯展示组件）
 *
 * 只负责把父级传入的友链渲染成卡片，不做任何数据请求：
 * 1、链接一律新标签页打开（外链跳转不打断本站浏览），并带 rel=noopener noreferrer 防 tabnabbing；
 * 2、logo_url 为空时不渲染 img，退化为纯文字卡片（不裂图）；
 * 3、links 为空时不渲染任何卡片（父级仍保留区块标题）。
 *
 * 拆成纯展示组件的原因：父级 HomeFriendshipComp 负责取数，本组件只吃 props，
 * 这样在 node 环境下也能用 vue 自带的 @vue/server-renderer 直接渲染并断言产物。
 */

defineProps<{
  links: FriendLinkItem[];
}>();
</script>

<template>
  <div v-if="links.length > 0" class="link_list">
    <a
      v-for="link in links"
      :key="link.url"
      class="link_card"
      :href="link.url"
      target="_blank"
      rel="noopener noreferrer"
    >
      <img v-if="link.logo_url" class="logo" :src="link.logo_url" :alt="link.title" loading="lazy" />
      <div class="info">
        <h4 class="name">{{ link.title }}</h4>
        <p v-if="link.intro" class="intro">{{ link.intro }}</p>
      </div>
    </a>
  </div>
</template>

<style scoped>
.link_list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
  padding: 0 1rem 1rem 1rem;
}
.link_card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  background-color: #ffffff;
  color: inherit;
  text-decoration: none;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
  &:hover {
    box-shadow: 0 6px 16px rgba(31, 58, 95, 0.12);
    transform: translateY(-2px);
  }
  .logo {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    border-radius: 8px;
    object-fit: cover;
    background-color: #f2f4f8;
  }
  .info {
    min-width: 0;
    .name {
      margin: 0;
      font-size: 0.95rem;
      font-weight: 600;
      word-break: break-all;
    }
    .intro {
      margin: 0.25rem 0 0 0;
      color: #6b7683;
      font-size: 0.82rem;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }
}
</style>
