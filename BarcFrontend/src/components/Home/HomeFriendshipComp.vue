<script setup lang="ts">
import { onMounted, ref } from "vue";
import HomeFriendLinkListComp from "@/components/Home/HomeFriendLinkListComp.vue";
import { fetchFriendLinks, type FriendLinkItem } from "@/utils/friendLinkApi.ts";

/**
 * 首页「友情链接&特别感谢」区块
 *
 * 数据来自管理端「系统 → 友情链接」维护的友链（公开接口，无需登录）。
 * 三条约定：
 * 1、区块标题始终渲染，与是否有数据无关；
 * 2、接口异常或没有数据时，不渲染任何卡片、也不抛出可见错误
 *    —— 与接入前的纯标题形态完全一致，不影响首页其它区块；
 * 3、取数只发生在这里，列表渲染交给 HomeFriendLinkListComp（纯展示、便于测试）。
 *
 * 取数放在 onMounted（而不是 setup 顶层 await）：本组件由 HomeContainerComp 直接引入，
 * 外层没有任何 <Suspense>，异步 setup 会直接失效，因此这里保持同步 setup。
 */

const links = ref<FriendLinkItem[]>([]);

/** 拉取友链；失败时由 fetchFriendLinks 内部兜底为空数组，这里不再抛错 */
async function loadLinks(): Promise<void> {
  links.value = await fetchFriendLinks();
}

onMounted(loadLinks);
</script>

<template>
  <div class="friendship_box">
    <div class="title">
      <img class="icon" src="https://static.kivo.wiki/images/gallery/D1.%E6%B8%B8%E6%88%8F%E5%86%85%E7%B4%A0%E6%9D%90/%E5%8A%A0%E8%BD%BD%E8%BF%87%E5%9C%BA/Event_Photo_LoadingImage_836_01_A.png" alt="icon"/>
      <h3>友情链接&特别感谢</h3>
    </div>

    <HomeFriendLinkListComp :links="links" />
  </div>
</template>

<style scoped>
.friendship_box {
  background-color: white;
  padding-top: 1rem;
}
.title {
  padding: 0 0 1rem 0;
  display: flex;
  flex-direction: row;
  align-items: end;
  justify-content: center;
  .icon {
    height: 4rem;
  }
}
</style>
