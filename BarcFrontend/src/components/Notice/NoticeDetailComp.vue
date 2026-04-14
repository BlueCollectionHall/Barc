<script setup lang="ts">
import {useRoute} from "vue-router";
import {onMounted, ref} from "vue";
import type {NoticeImpl} from "@/interfaces/NoticeImpl.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import {errorMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";

const route = useRoute();

const notice = ref<NoticeImpl | null>(null);
const userArchive = ref<UserArchiveImpl | null>(null);

// 获取公告详情
const fetchNotice = async (noticeId: string) => {
  try {
    const response = await baseHttp.get("/notice/only", {params: {notice_id: noticeId}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      notice.value = data.data;
      if (notice.value) await fetchUserArchive(notice.value.author);
    } else errorMessage(data.msg);
  } catch (error) {
    console.error(error);
    errorMessage("网络错误");
  }
}

// 获取发布者信息
const fetchUserArchive = async (uuid: string) => {
  try {
    const response = await baseHttp.get("/user/current_by_uuid", {params: {uuid}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      userArchive.value = data.data;
    } else errorMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络错误");
  }
}

onMounted(() => {
  const noticeId = route.query.notice_id as string | undefined;
  if (noticeId) {
    fetchNotice(noticeId);
  } else {
    errorMessage("没有公告ID");
  }
})
</script>

<template>
  <div class="notice_detail_container" v-if="notice">
    <div class="title">
      <h1>{{notice.title}}</h1>
      <div class="avatar_nickname_time">
        <img v-if="userArchive" class="avatar" :src="userArchive.avatar || ''" alt="avatar"/>
        <span v-if="userArchive" class="nickname">{{userArchive.nickname}}</span>
        <span>编辑于：{{timestampToCn(notice.updated_at)}}</span>
        <span>发布于：{{timestampToCn(notice.created_at)}}</span>
      </div>
    </div>
    <div class="content" v-html="notice.content"></div>
  </div>
  <div class="no_data" v-else>
    <img class="icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="icon"/>
    <span class="text">加载中…</span>
  </div>
</template>

<style scoped>
.notice_detail_container {
  padding: 2rem;
  margin: 2rem auto 0;
  width: 60%;
  background-color: rgba(255, 255, 255, 0.6);
  box-shadow: #deefff 0 0 5px;
  .title {
    text-align: center;
    padding: 0 0 1rem 0;
    font-size: 1.3rem;
    border-bottom: #9b9b9b 1px solid;
  }
  .avatar_nickname_time {
    justify-content: center;
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
    .nickname:hover {
      color: #deefff;
      cursor: pointer;
    }
    .nickname {
      color: #114059;
      transition: .3s ease;
    }
  }
  .content {
    padding: 2rem;
  }
}
.no_data {
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
