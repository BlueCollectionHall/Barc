<script setup lang="ts">
import {EyeOutlined, HeartOutlined} from "@ant-design/icons-vue";
import {useAccountWorkItemPinia} from "@/stores/AccountWorkItemListPinia.ts";
import {storeToRefs} from "pinia";
import {ref} from "vue";
import type {PageRequestImpl} from "@/interfaces/PageImpl.ts";
import {useRoute, useRouter} from "vue-router";

const router = useRouter();
const route = useRoute();
const accountContentPinia = useAccountWorkItemPinia();

const {pageResult, pageRequest} = storeToRefs(accountContentPinia);

const handlePageChange = (pageNum: number) => {
  if (pageNum === 1) {
    const query = {...route.query};
    delete query.page_num;
    router.push({query})
  } else {
    router.push({query: {...route.query, page_num: pageNum}});
  }
}

const itemClicked = (workId: string) => {
  router.push({name: "WorkDetail", query: {work_id: workId}});
}
</script>

<template>
  <div class="work_list_box" v-if="pageResult && pageResult.list.length > 0">
    <div class="item" v-for="item in pageResult.list" :key="item.id" @click="itemClicked(item.id)">
      <div class="cover_box">
        <img class="cover_image" :src="item.cover_image" alt="cover"/>
        <div class="cover_z">
          <EyeOutlined /> {{item.view_count}} &nbsp;&nbsp; <HeartOutlined /> {{item.like_count}}
        </div>
      </div>
      {{item.title}}
    </div>
  </div>
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
.work_list_box {
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.5rem;
  justify-content: space-between;
  padding: 2rem 10%;
  row-gap: 4rem;
}
@media (min-width: 1340px) {
  .work_list_box {
    grid-template-columns: repeat(5, 1fr);
  }
}
.work_list_box::after {
  content: "";
  flex: auto;
}
.item:hover {
  cursor: pointer;
  transform: translateY(-.5rem);
}
.item {
  transition: .3s ease;
  flex: 0 0 calc(20% - 1.5rem);
}
.cover_box {
  border-radius: .5rem;
  width: calc(16 * .9vw);
  height: calc(9 * .9vw);
  overflow: hidden;
  position: relative;
  .cover_image {
    width: 100%;
    z-index: -2;
  }
}
.cover_z {
  color: white;
  width: 100%;
  background: linear-gradient(180deg,rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, .8) 150%);
  position: absolute;
  bottom: 0;
  padding: .4rem 0 .4rem .8rem;
}
.pagination {
  padding-bottom: 3rem;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
