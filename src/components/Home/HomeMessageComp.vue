<script setup lang="ts">
import {ArrowRightOutlined} from "@ant-design/icons-vue";
import {onMounted, ref} from "vue";
import type {MessageBoardWithUserImpl} from "@/interfaces/MessageBoardImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {
  errorCatchMessage,
  errorMessage,
  infoMessage,
  successMessage
} from "@/utils/MessageAlert.ts";

const messageList = ref<Array<MessageBoardWithUserImpl>>([]);
const message = ref<string | null>(null);

const fetchMessage = async (limit: number) => {
  await baseHttp({
    url: "/comment/message_board/new",
    method: "GET",
    params: {limit}
  }).then(res => {
    const data: ResponseImpl = res.data;
    if (data.code === 0) {
      messageList.value = data.data;
      messageList.value = messageList.value?.map(msg => ({...msg, userInfo: undefined}));
      messageList.value.forEach(message => {fetchUserInfo(message.author)});
    } else errorMessage(data.msg);
  }).catch(() => {errorCatchMessage();})
}

const fetchUserInfo = async (uuid: string): Promise<void> => {
  try {
    const response = await baseHttp("/user/current_by_uuid", {
      method: "GET",
      params: {uuid}
    });
    const userInfo: UserArchiveImpl = response.data.data;
    updateMessageUserInfo(uuid, userInfo);
  }
  catch (error) {
    console.error(`获取用户 ${uuid} 信息失败:`, error)
  }
}
const updateMessageUserInfo = (userId: string, userInfo: UserArchiveImpl): void => {
  messageList.value = messageList.value.map(msg =>
    msg.author === userId ? { ...msg, userInfo } : msg
  )
}

const uploadNewMessage = async (): Promise<void> => {
  const token = window.localStorage.getItem("token");
  if (!token) {
    infoMessage("请先登录");
    return;
  }
  if (message.value === null || message.value === "") {
    infoMessage("留言不得为空");
    return;
  }
  try {
    const response = await baseHttp("/comment/message_board/upload", {
      method: "GET",
      headers: {Authorization: token},
      params: {content: message.value}
    });
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      successMessage(data.data);
      await fetchMessage(5);
    }
    else errorMessage(data.msg);
  } catch (error) {
    console.error(error);
    errorCatchMessage();
  }
}

import {useRouter} from "vue-router";
const router = useRouter();
const clickedToView = () => {
  router.push({name: "MessageBoard"});
}

onMounted(async () => {
  await fetchMessage(5);
})
</script>

<template>
  <div class="message_board_box">
    <div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem">
      <img style="width: 5rem" src="https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/FX_TEX_CH0071_Prop_03.png" alt="logo"/>
      <div style="display: flex; gap: .3rem; flex-direction: column; flex: 1">
        <span style="font-size: 1.3rem; font-weight: bold; color: #fe4b7b; display: flex; align-items: center;">
          留言
        </span>
        <input id="input" type="text" placeholder="30字以内" v-model="message"/>
      </div>
      <button id="b" @click="uploadNewMessage"><ArrowRightOutlined /></button>
      <a-button type="primary" style="background-color: #fda5bc; color: white; height: 2rem" @click="clickedToView">更多</a-button>
    </div>
    <div class="messages" v-if="messageList">
      <div class="message" v-for="item in messageList" :key="item.id">
        <img class="avatar" :src="item.userInfo?.avatar || ''" alt="avatar"/>
        <div class="nickname_content">
          <span class="nickname">{{item.userInfo?.nickname || '未知昵称'}}</span>
          <span class="content">{{item.content}}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message_board_box {
  margin-top: 1rem;
}
#input {
  background-color: #ffffff80;
  border: 1px solid #fda5bc;
  height: 2rem;
  border-radius: .4rem;
}
#b {
  background-color: #fda5bc;
  border: none;
  width: 2rem;
  height: 2rem;
  font-size: 1.2rem;
  color: white;
  border-radius: .4rem;
}
.message {
  margin-top: .5rem;
  display: flex;
  align-items: center;
  gap: .5rem;
  .avatar {
    width: 3.4rem;
    border: #fda5bc 2px solid;
    border-radius: 1rem;
  }
  .nickname_content {
    display: flex;
    flex-direction: column;
    gap: .3rem;
    .nickname {
      font-weight: bold;
      color: #fe4b7b;
    }
    .content:hover {
      background-color: #fda5bc;
      cursor: default;
    }
    .content {
      transition: .3s ease;
      background-color: #fda5bc90;
      padding: .3rem;
      border-radius: .5rem;
      color: white;
    }
  }
}
</style>
