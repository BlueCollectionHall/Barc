<script setup lang="ts">
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {onBeforeMount, ref} from "vue";
import type {CategoryImpl} from "@/interfaces/CategoryImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";

const categories = ref<Array<CategoryImpl>>([]);

const fetchTopCategories = async () => {
  await baseHttp.get("/api/category/all_top_categories")
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) categories.value = data.data;
      else infoMessage(data.msg);
    }).catch(err => {
      console.error(err);
      errorMessage("网络异常！");
    });
}

onBeforeMount(() => {
  fetchTopCategories();
})
</script>

<template>
  <div class="items" v-if="categories.length > 0">
    <div class="item" v-for="item in categories" :key="item.id">{{item.name}}</div>
  </div>
</template>

<style scoped>
.items {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 2rem;
  gap: 1rem;
  .item:hover {
    cursor: pointer;
  }
  .item {
    color: #364d79;
    font-size: 1.1rem;
  }
}
.selected {
  color: #bf61a1 !important;
}
</style>
