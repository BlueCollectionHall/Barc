<script setup lang="ts">
import {useRoute, useRouter} from "vue-router";
import {onMounted, ref, watch} from "vue";
import {useWorkItemListPinia} from "@/stores/WorkItemListPinia.ts";

const workItemListPinia = useWorkItemListPinia();
const route = useRoute();
const router = useRouter();

const keyword = ref<string>("");

const searchClicked = () => {
  // 确定keyword存在
  if (keyword.value.length === 0) {
    return;
  }
  // 得到当前的routerName便于追加query
  const routerName: string | undefined = route.name as string | undefined;
  if (!routerName) {
    return;
  }
  // 向当前url中追加query
  router.push({name: routerName, query: {keyword: keyword.value}});
  // 刷新列表
  workItemListPinia.fetchWorkList("PUBLIC");
}

onMounted(() => {
  // 从url中获取已存在的keyword
  const routeKeyword: string | null | undefined = route.query.keyword as string | null | undefined;
  if (routeKeyword) {
    keyword.value = routeKeyword;
  }
})

watch(() => route.query, () => {
  workItemListPinia.fetchWorkList("PUBLIC");
})
</script>

<template>
  <div class="search_bar">
    <el-input class="input" placeholder="请输入关键词以搜索" v-model="keyword" />
    <el-button class="button" type="primary" native-type="button" @click="searchClicked">搜索</el-button>
  </div>
</template>

<style scoped>
.search_bar {
  width: 50%;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
  .input, .button {
    height: 2.5rem;
  }
  .button {
    font-size: 1.1rem;
    width: 7rem;
  }
}
</style>
