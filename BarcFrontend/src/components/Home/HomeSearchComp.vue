<script setup lang="ts">
import {ref} from "vue";
import {SearchOutlined} from "@ant-design/icons-vue";
import {useRouter} from "vue-router";

const router = useRouter();

const keyword = ref<string>("");

const search = () => {
  if (keyword.value.length === 0) {
    return;
  }
  router.push({name: "WorkAll", query: {keyword: keyword.value}});
};
</script>

<template>
  <div class="search_box">
    <div class="title">
      <SearchOutlined class="icon" />
      综合搜索
    </div>
    <div class="search_bar">
      <el-input class="input" placeholder="请输入关键词以搜索" v-model="keyword"/>
      <el-button class="button" type="primary" native-type="button" @click="search">搜索</el-button>
    </div>

  </div>
</template>

<style scoped>
.search_box {
  background: linear-gradient(rgba(255, 255, 255, .8) 30%, rgba(255, 255, 255, 0) 80%);
  padding: 1rem;
  .title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: .5rem;
    font-size: 1.2rem;
    margin-bottom: 1rem;
  }
  .search_bar {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: row;
    gap: 1rem;
    .button {
      min-width: 8rem;
    }
  }
}
</style>
