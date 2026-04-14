<script setup lang="ts">
import {onMounted, ref} from "vue";
import type {NoticeImpl} from "@/interfaces/NoticeImpl.ts";
import type {PageRequestImpl, PageResultImpl} from "@/interfaces/PageImpl.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage} from "@/utils/MessageAlert.ts";
import {useRouter} from "vue-router";

const router = useRouter();

interface NoticeNicknameImpl extends NoticeImpl {nickname: string; avatar: string}

const noticeList = ref<Array<NoticeNicknameImpl>>([]);
const pageRequest = ref<PageRequestImpl>({page_size: 5, page_num: 1});
const pageResult = ref<PageResultImpl<NoticeNicknameImpl> | null>(null);

// 分页获取公告
const fetchNotice = async () => {
  try {
    const response = await baseHttp.post("/notice/notices_by_page", pageRequest.value);
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      pageResult.value = data.data;
      if (pageResult.value) {
        noticeList.value = pageResult.value.list;
        if (noticeList.value.length !== 0) {
          for (let i = 0; i < noticeList.value.length; i++) {
            await baseHttp.get("/user/current_by_uuid", {params: {uuid: noticeList.value[i].author}})
              .then(res => {
                const data: ResponseImpl = res.data;
                const userArchive = data.data;
                if (data.code === 0) {
                  noticeList.value[i].nickname = userArchive.nickname;
                  noticeList.value[i].avatar = userArchive.avatar;
                }
              }
            ).catch(() => {errorMessage("网络错误")});
          }
        }
      }
    }
  } catch (error) {
    console.log(error);
    errorMessage("网络错误");
  }
}

// 跳转公告详情
const itemClicked = (noticeId: string) => {
  router.push({name: "NoticeDetail", query: {notice_id: noticeId}});
}

// 分页切换处理
const handlePageChange = (pageNum: number) => {
  pageRequest.value.page_num = pageNum; // 更新当前页码
  fetchNotice(); // 重新获取数据
}

onMounted(() => {
  fetchNotice();
})
</script>

<template>
  <div class="notice_list_container" v-if="0 < noticeList.length">
    <div class="notice" v-for="item in noticeList" :key="item.id" @click="itemClicked(item.id)">
      <h3 class="title">{{item.title}}</h3>
      <div class="avatar_nickname_time">
        <img class="avatar" :src="item.avatar" alt="avatar"/>
        <span class="nickname">发布者：{{item.nickname}}</span>
        <span class="time">{{timestampToCn(item.updated_at)}}</span>
      </div>
    </div>
  </div>
  <div class="notice_list_container no_list" v-else>
    <img class="icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="icon"/>
    <span class="text">暂无数据</span>
  </div>
  <el-pagination class="pagination"
                 background v-if="pageResult"
                 :page-size="pageResult.page_size"
                 :pager-count="pageResult.total_page"
                 layout="prev, pager, next"
                 :total="pageResult.total"
                 :current-page="pageRequest.page_num"
                 @current-change="handlePageChange"/>
</template>

<style scoped>
.pagination {
  margin-top: auto;
  margin-bottom: 2rem;
  display: flex;
  justify-content: center;
  align-items: center;
}
.notice_list_container {
  margin: 0 auto;
  width: 50%;
  .notice:first-child {
    margin-top: 2rem;
  }
  .notice:last-child {
    margin: 0 0 2rem 0;
  }
  .notice:hover {
    background-color: rgba(255, 255, 255, 0.8);
    box-shadow: #deefff 0 0 10px;
    cursor: pointer;
  }
  .notice {
    transition: .3s ease;
    backdrop-filter: blur(.1rem);
    box-shadow: #deefff 0 0 5px;
    width: 100%;
    background-color: rgba(255, 255, 255, 0.6);
    padding: 2rem;
    margin-bottom: 2rem;
    .title {
      padding: 0 0 1rem 0;
      font-size: 1.3rem;
      border-bottom: #9b9b9b 1px solid;
    }
    .avatar_nickname_time {
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
}
.no_list {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2rem;
  .text {
    color: white;
    text-shadow: #9b9b9b 0 0 5px;
    font-weight: bold;
    font-size: 2rem;
  }
}
</style>
