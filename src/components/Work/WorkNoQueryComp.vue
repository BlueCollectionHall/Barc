<script setup lang="ts">
import {useRouter, useRoute} from "vue-router";
import {onMounted, ref} from "vue";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";

const router = useRouter();
const route = useRoute();

const keyword = ref<string>("");
const routerName = ref<string>("");

const seeRoute = () => {
  const name: string | null | undefined | symbol = route.name;
  if (typeof name === 'string') {
    routerName.value = name;
  } else {
    errorMessage("路由异常");
  }
}

const search = () => {
  if (keyword.value.length === 0 || routerName.value.length === 0) {
    console.error("关键字或路由为空置");
    return;
  }
  router.push({name: routerName.value, query: {keyword: keyword.value}});
  setTimeout(() => {
    router.go(0);
  }, 100);

}

onMounted(() => {
  seeRoute();
})
</script>

<template>
  <div class="container">
    <div class="title">
      <h2>B.A.R.C. (BA.RecreateCollection)</h2>
    </div>
    <div class="search_bar">
      <el-input class="input" placeholder="请输入关键词以搜索" v-model="keyword"/>
      <el-button class="button" type="primary" @click="search" native-type="button">搜索</el-button>
    </div>
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  width: 30%;
  .title {
    text-align: center;
  }
}
.search_bar {
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
