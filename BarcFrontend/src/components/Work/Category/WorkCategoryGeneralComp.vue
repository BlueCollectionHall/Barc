<script setup lang="ts">
import {onBeforeMount, onMounted, ref, watch} from "vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import type {PageRequestImpl, PageResultImpl} from "@/interfaces/PageImpl.ts";
import {type LocationQuery, useRoute, useRouter} from "vue-router";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {EyeOutlined, HeartOutlined, UserOutlined} from "@ant-design/icons-vue";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";

const router = useRouter();
const route = useRoute();

const workItems = ref<Array<WorkImpl>>([]);
const pageRequest = ref<PageRequestImpl>({page_num: 1, page_size: 24});
const pageResult = ref<PageResultImpl<WorkImpl> | null>(null);
const categoryId = ref<string | undefined>(undefined);

const fetchWorks = async () => {
  try {
    const response = await baseHttp.post("/api/work/works_by_category", pageRequest.value, {
      params: {
        status: "PUBLIC",
        category_id: route.params.category_id,
      }
    });
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      pageResult.value = data.data;
      workItems.value = pageResult.value?.list || [];
      console.log(pageResult.value?.list);
      await handleWorkListAuthorNickname();
    } else infoMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常！");
  }
}

const handleWorkListAuthorNickname = async () => {
  if (workItems.value.length === 0) return;
  for (let i = 0; i < workItems.value.length; i++) {
    if (workItems.value[i].is_claim) {
      await baseHttp.get("/user/current_by_uuid", {params: {uuid: workItems.value[i].author}})
        .then(res => {
          const data: ResponseImpl = res.data;
          if (data.code === 0) {
            const userArchive: UserArchiveImpl = data.data;
            workItems.value[i].author_nickname = userArchive.nickname;
          } else workItems.value[i].author_nickname = "is_claim";
        }).catch(error => {
          console.error(error);
          errorMessage("网络异常！");
          workItems.value[i].author_nickname = "is_claim";
        })
    }
  }
}

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


// 页面组件加载前取出路由器中分类ID信息
onBeforeMount(() => {
  categoryId.value = route.params.category_id as string | undefined;
})

// 页面内容加载完成后，识别路由器中的query参数，是否符合逻辑
onMounted(() => {
  if (Object.keys(route.query).length === 0) {
    fetchWorks();
  } else {
    if (route.query.keyword) pageRequest.value.params = {keyword: route.query.keyword};
    if (route.query.page_num) pageRequest.value.page_num = Number(route.query.page_num);
    fetchWorks();
  }
})

watch(() => route.query, () => {
  if (route.query.keyword) {
    pageRequest.value.params = {keyword: route.query.keyword};
  } else {
    if (pageRequest.value.params && pageRequest.value.params.keyword) {
      delete pageRequest.value.params.keyword;
    }
  }
  if (route.query.page_num) {
    pageRequest.value.page_num = Number(route.query.page_num);
  } else pageRequest.value.page_num = 1;
  fetchWorks();
})
</script>

<template>
  <div class="work_list_box" v-if="workItems.length > 0">
    <div class="item" v-for="item in workItems" :key="item.id" @click="itemClicked(item.id)">
      <div class="cover_box">
        <img class="cover_image" :src="item.cover_image" alt="cover"/>
        <div class="cover_z">
          <EyeOutlined /> {{item.view_count}} &nbsp;&nbsp; <HeartOutlined /> {{item.like_count}}
        </div>
      </div>
      <div class="title_nickname">
        <span class="title">{{item.title}}</span>
        <span class="nickname">
          <UserOutlined />
          {{item.author_nickname}}
        </span>
      </div>
    </div>
  </div>
  <div class="no_list" v-else>
    <img class="icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="icon"/>
    <span class="text">暂无数据</span>
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
  width: 80%;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1.5rem;
  justify-content: space-between;
  row-gap: 1rem;
}
@media (min-width: 1040px) {
  .work_list_box {
    grid-template-columns: repeat(5, 1fr);
  }
}
@media (min-width: 1540px) {
  .work_list_box {
    grid-template-columns: repeat(6, 1fr);
  }
}
.work_list_box::after {
  content: "";
  flex: auto;
}
.item:hover {
  transform: translateY(-.5rem);
  cursor: pointer;
}
.item {
  transition: .3s ease;
  flex: 0 0 calc(20% - 1.5rem);
  .title_nickname {
    display: flex;
    flex-direction: column;
    gap: .5rem;
    .title {
      font-size: 1.1rem;
    }
    .nickname {
      color: #787878;
      font-size: .9rem;
    }
  }
}
.cover_box {
  border-radius: .5rem;
  width: calc(16 * .7vw);
  height: calc(9 * .7vw);
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
.no_list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rem;
  .icon {
    width: 10rem;
  }
  .text {
    color: white;
    text-shadow: #9b9b9b 0 0 5px;
    font-weight: bold;
    font-size: 2rem;
  }
}
.pagination {
  margin-top: 2rem;
  margin-bottom: 2rem;
  padding-bottom: 3rem;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
