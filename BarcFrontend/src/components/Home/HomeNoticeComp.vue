<script setup lang="ts">
import {onMounted, ref} from "vue";
import {SoundOutlined} from '@ant-design/icons-vue';
import type {NoticeImpl} from "@/interfaces/NoticeImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage} from "@/utils/MessageAlert.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
import {useRouter} from "vue-router";

const router = useRouter();

const noticeList = ref<Array<NoticeImpl>>([]);
const userArchive = ref<UserArchiveImpl | null>(null);

const fetchLatestNotice = async () => {
  try {
    const response = await baseHttp.get("/notice/latest", {params: {day: 30}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      noticeList.value = data.data;
      if (noticeList.value.length !== 0) {
        await fetchUserArchive(noticeList.value[0].author);
      }
    }
  } catch (error) {
    console.error(error);
    errorMessage("公告组件出现异常");
  }
}

const fetchUserArchive = async (uuid: string) => {
  try {
    const response = await baseHttp.get("/user/current_by_uuid", {params: {uuid}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      userArchive.value = data.data;
    }
  } catch (error) {
    console.error(error);
    errorMessage("公告组件出现异常:USER");
  }
}

const moreClicked = () => {
  router.push({name: "Notice"});
}

onMounted(() => {
  fetchLatestNotice();
})
</script>

<template>
  <div class="notice_box">
    <div class="title">
<!--      <img class="icon" src="https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/Emoticon_Exclamation.png" alt="icon"/>-->
      <SoundOutlined />
      最新公告
      <a-button class="more_button" ghost type="primary" @click="moreClicked">更多</a-button>
    </div>
    <div class="notices no_list" v-if="noticeList.length === 0">
      <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E8%A1%A8%E6%83%85%E5%8C%85/%E7%8E%A9%E6%89%8B%E6%9C%BA_09.gif" alt="icon"/>
      <span>暂无数据</span>
    </div>
    <div class="notices" v-else>
      <div class="notice" v-for="item in noticeList" :key="item.id">
        <div class="title">
          <h3>{{item.title}}</h3>
          <div class="nickname_time">
            <span class="nickname" v-if="userArchive">发布者：{{userArchive.nickname}}</span>
            <span class="nickname" v-else>未知发布者</span>
            <span class="updated_at">编辑于：{{timestampToCn(item.updated_at)}}</span>
          </div>
        </div>
        <div class="content" v-html="item.content">
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notice_box {
  background-color: white;
  padding: 1rem;
  min-height: 10rem;
  max-height: 20rem;
  overflow: auto;
  .notices {
    padding: 1rem;
    .notice {
      .title {
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        border-bottom: #9b9b9b 1px solid;
        padding: 0 0 1rem 0;
        .nickname_time {
          .nickname {
            margin-right: 1rem;
          }
          font-size: 1rem;
        }
      }
    }
  }
}
.title {
  display: flex;
  align-items: center;
  gap: .5rem;
  font-size: 1.2rem;
  .icon {
    height: 2rem;
  }
  .more_button {
    margin-left: auto;
  }
}
.no_list {
  display: flex;
  gap: .5rem;
  font-size: 1.1rem;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  .icon {
    width: 5rem;
  }
}
</style>
