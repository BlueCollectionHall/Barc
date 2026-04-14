<script setup lang="ts">
import {onMounted, ref} from "vue";
import {useRouter, useRoute} from "vue-router";

const router = useRouter();
const route = useRoute();

const keyword = ref<string>("");

const searchClicked = () => {
  if (keyword.value.length === 0) {
    const query = {...route.query};
    delete query.keyword;
    router.push({ query });
  } else {
    router.push({query: {keyword: keyword.value}});
  }
}

onMounted(() => {
  const routerKeyword: string | undefined | null = route.query.keyword as string | undefined | null;
  if (routerKeyword) {
    keyword.value = routerKeyword;
  }
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
