<script setup lang="ts">
import {ArrowRightOutlined} from "@ant-design/icons-vue";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {
  errorCatchMessage,
  errorMessage,
  infoMessage,
  successMessage
} from "@/utils/MessageAlert.ts";
import {onMounted, ref, watch} from "vue";
import type {MessageBoardWithUserImpl} from "@/interfaces/MessageBoardImpl.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import type {FeedBackImpl} from "@/interfaces/FeedbackImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();

const {userBasic} = storeToRefs(userPinia);
const messageList = ref<Array<MessageBoardWithUserImpl>>([]);
const message = ref<string | null>(null);
const open = ref<boolean>(false);
const feedbackForm = ref<FeedBackImpl>({
  id: "", target_id: "", ipv4: null, ipv6: null, author: null, email: null, content: "", echo: null, type: "MESSAGE_BOARD", status: "PENDING", created_at: new Date(), updated_at: new Date()
});

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
      await fetchMessage(3);
    }
    else errorMessage(data.msg);
  } catch (error) {
    console.error(error);
    errorCatchMessage();
  }
}
const showModal = async (message: MessageBoardWithUserImpl) => {
  infoMessage(`该留言来自：${message.userInfo?.nickname || '加载中'}`);
  feedbackForm.value.target_id = message.id;
  if (userBasic.value !== null) feedbackForm.value.author = userBasic.value.uuid;
  open.value = true;
};

const handleOk = async (e: MouseEvent) => {
  console.log(e);
  if (feedbackForm.value.content !== "") {
    try {
      const response = await baseHttp.post("/feedback/upload", feedbackForm.value);
      const data: ResponseImpl = response.data;
      if (data.code === 0) {
        successMessage(data.data);
        feedbackForm.value.content = "";
        feedbackForm.value.email = ""
      }
      else errorMessage(data.msg);
    } catch {
      errorMessage("网络错误！");
    }
    open.value = false;
  } else {
    errorMessage("投诉内容不能为空！");
  }
};
onMounted(async () => {
  await fetchMessage(50);
})


</script>

<template>
  <h1 class="title">留言板</h1>
  <a-modal v-model:open="open" title="您要投诉这条留言吗？" ok-text="投诉" cancel-text="关闭" @ok="handleOk">
    <div>目标内容ID：{{feedbackForm.target_id}}</div>
    <div v-if="userBasic === null">
      您未登录，若要跟踪进度，可以输入您的电子邮箱：
      <a-input v-model:value="feedbackForm.email" type="email" placeholder="电子邮箱"/>
    </div>
    <br/>
    <a-textarea v-model:value="feedbackForm.content"/>
  </a-modal>
  <div class="container">
    <div class="input_bar">
      <img class="momotalk_logo" src="https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/FX_TEX_CH0071_Prop_03.png" alt="logo"/>
      <input class="input" type="text" placeholder="30字以内"/>
      <button class="button" @click="uploadNewMessage"><ArrowRightOutlined /></button>
    </div>
    <div class="messages" v-if="messageList">
      <div class="message_item" v-for="item in messageList" :key="item.id" style="margin-top: .5rem; display: flex; align-items: center; gap: .5rem;" @click="showModal(item)">
        <img style="width: 3.4rem; border: #fda5bc 2px solid; border-radius: 1rem" :src="item.userInfo?.avatar || ''" alt="avatar"/>
        <div style="display: flex; flex-direction: column; gap: .3rem;">
          <span style="font-weight: bold; color: #fe4b7b">{{item.userInfo?.nickname || '未知昵称'}}</span>
          <span style="background-color: #fda5bc; padding: .3rem; border-radius: .5rem; color: white">{{item.content}}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.title {
  font-size: 2rem;
  font-weight: bold;
  color: #fe4b7b;
  text-align: center;
  padding: .5rem 0;
}
.container {
  width: 50%;
  margin: 0 auto;
}
.input_bar {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: .5rem;
}
.momotalk_logo {
  width: 3rem;
}
.input {
  flex: 1;
  height: 2rem;
  border-radius: .5rem;
  border: #fe4b7b solid 1px;
}
.button {
  width: 2rem;
  height: 2rem;
  background-color: #fda5bc;
  color: white;
  border: #fe4b7b solid 1px;
  border-radius: .5rem;
}
.message_item:hover {
  cursor: pointer;
}
</style>
