<!--
    通用组件
    当存在query时所有Work的列表子路由通用该组件
 -->
<script setup lang="ts">
import {onMounted} from "vue";
import WorkItemListComp from "@/components/Work/WorkItemListComp.vue";
import {useWorkItemListPinia} from "@/stores/WorkItemListPinia.ts";
import {storeToRefs} from "pinia";
import {useRouter, useRoute} from "vue-router";

const workItemListPinia = useWorkItemListPinia();
const router = useRouter();
const route = useRoute();

const {pageResult, pageRequest} = storeToRefs(workItemListPinia);

// 分页切换处理
const handlePageChange = (pageNum: number) => {
  if (pageNum === 1) {
    const query = {...route.query};
    delete query.page_num;
    router.push({query})
  } else {
    router.push({query: {...route.query, page_num: pageNum}});
  }
}

onMounted(() => {
  console.log("Component mounted. WorkGeneralComp.vue");
  workItemListPinia.fetchWorkList("PUBLIC");
})


</script>

<template>
  <WorkItemListComp/>
  <el-pagination
    class="pagination"
    background
    layout="prev, pager, next"
    :total="pageResult.total"
    :page-size="pageResult.page_size"
    :pager-count="pageResult.total_page"
    :current-page="pageRequest.page_num"
    @current-change="handlePageChange"
    v-if="pageResult" />
</template>

<style scoped>
.pagination {
  margin-top: 2rem;
  margin-bottom: 2rem;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
