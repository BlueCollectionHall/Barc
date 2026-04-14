<script setup lang="ts">
import "@/Styles/ContainerAni.css";
import {onMounted, ref} from "vue";
import type {NoticeImpl} from "@/Interfaces/NoticeImpl.ts";
import {baseHttp} from "@/Utils/Request.ts";
import type {PageRequestImpl, PageResultImpl} from "@/Interfaces/PageImpl.ts";
import type {UserArchiveImpl} from "@/Interfaces/UserImpl.ts";
import type {ResponseImpl} from "@/Interfaces/ResponseImpl.ts";
import {errorMessage, successMessage, warningMessage} from "@/Utils/MessageAlert.ts";
import {timestampToCn} from "@/Utils/TimeToCn.ts";
import {useRouter} from "vue-router";

const router = useRouter();

interface NoticeUserArchiveImpl extends NoticeImpl {
  user_archive: UserArchiveImpl
}

const noticeList = ref<Array<NoticeUserArchiveImpl>>([]);
const pageRequest = ref<PageRequestImpl>({page_num: 1, page_size: 5});
const pageResult = ref<PageResultImpl<NoticeUserArchiveImpl> | null>(null);

const fetchNoticeList = async () => {
  try {
    const response = await baseHttp.post("/notice/notices_by_page", pageRequest.value);
    const data: ResponseImpl = response.data;
    // 当返回不正常时
    if (data.code !== 0) {
      errorMessage(data.msg);
      return;
    }
    pageResult.value = data.data;
    // 当不存在分页信息 以及 其内部列表长度时
    if (!pageResult.value?.list?.length) return;
    noticeList.value = pageResult.value.list;
    // 并行请求，提高性能
    const promises = noticeList.value.map(async (notice) => {
      try {
        const res = await baseHttp.get("/user/current_by_uuid", {
          params: { uuid: notice.author }
        });
        notice.user_archive = res.data.data;
      } catch (error) {
        console.error(`Failed to fetch user for author ${notice.author}:`, error);
      }
    });
    await Promise.all(promises);
  } catch (error) {
    console.error(error);
    errorMessage("网络错误");
  }
}

// 分页切换处理
const handlePageChange = (pageNum: number) => {
  pageRequest.value.page_num = pageNum; // 更新当前页码
  fetchNoticeList(); // 重新获取数据
}

// 新增公告跳转
const uploadClicked = () => {
  router.push({name: "NoticeUpload"});
}

// 点击删除公告
const deleteClicked = async (noticeId: string) => {
  try {
    const response = await baseHttp.delete("/notice/delete", {params: {notice_id: noticeId}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) successMessage(data.data);
    else warningMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

// 点击修改公告
const editClicked = (noticeId: string) => {
  router.push({name: "NoticeEdit", query: {notice_id: noticeId}});
}

onMounted(() => {
  fetchNoticeList();
})
</script>

<template>
  <div class="container">
    <div class="select_bar">
      <el-button native-type="button" type="primary" @click="uploadClicked">新增公告</el-button>
    </div>
    <div class="notices">
      <div class="no_list" v-if="noticeList.length === 0">
        <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E5%9B%BD%E6%9C%8D%E7%B4%A0%E6%9D%90/%E4%BC%98%E9%A6%99_11.png" alt="icon"/>
        <h1>没有数据</h1>
      </div>
      <div class="notice" v-else v-for="item in noticeList" :key="item.id">
        <h3 class="title">{{item.title}}</h3>
        <div class="avatar_nickname_time_buttons">
          <img class="avatar" :src="item.user_archive.avatar || ''" alt="avatar"/>
          <span class="nickname">发布者：{{item.user_archive.nickname}}</span>
          <span class="time">{{timestampToCn(item.updated_at)}}</span>
          <div class="buttons">
            <el-button native-type="button" type="primary" @click="editClicked(item.id)">修改</el-button>
            <el-button native-type="button" type="danger" @click="deleteClicked(item.id)">删除</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-pagination class="pagination"
                   background v-if="pageResult"
                   :page-size="pageResult.page_size"
                   :pager-count="pageResult.total_page"
                   layout="prev, pager, next"
                   :total="pageResult.total"
                   :current-page="pageRequest.page_num"
                   @current-change="handlePageChange"/>
  </div>
</template>

<style scoped>
.container {
  animation: container_in 1s ease forwards;
  height: 100%;
  overflow: auto;
  background-color: rgba(84, 92, 100, .5);
  backdrop-filter: blur(5px);
  box-shadow: white 0 0 5px;
  display: flex;
  flex-direction: column;
  .pagination {
    margin-top: auto;
    margin-bottom: 2rem;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .select_bar {
    border-bottom: #deefff solid 1px;
    padding: 2rem 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 1rem;
  }
}
.notices {
  padding: 2rem;
}
.notice {
  transition: .3s ease;
  backdrop-filter: blur(.1rem);
  box-shadow: #deefff 0 0 5px;
  background-color: rgba(255, 255, 255, 0.6);
  padding: .5rem 2rem;
  margin-bottom: 1rem;
  .title {
    padding: 0 0 .5rem 0;
    font-size: 1.3rem;
    border-bottom: #9b9b9b 1px solid;
  }
  .avatar_nickname_time_buttons {
    display: flex;
    align-items: center;
    gap: 1rem;
    font-size: 1.1rem;
    .avatar {
      border-radius: 40%;
      width: 3rem;
      height: 3rem;
      border: #deefff 2px solid;
    }
    .time {
      margin-left: auto;
    }
  }
}
</style>