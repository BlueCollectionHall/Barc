<script setup lang="ts">
import {onMounted, ref, watch} from "vue";
import type {FeedBackImpl} from "@/interfaces/FeedbackImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import type {UserBasicImpl} from "@/interfaces/UserImpl.ts";
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
const userPinia = useUserPinia();

interface ItemImpl {label: string; value: string; icon: string;}

const items: Array<ItemImpl> = [
  {label: "BUG问题", value: "BUG", icon: "https://static.kivo.wiki/images/gallery/D1.%E6%B8%B8%E6%88%8F%E5%86%85%E7%B4%A0%E6%9D%90/%E5%8A%A0%E8%BD%BD%E8%BF%87%E5%9C%BA/Event_Photo_CollectionCG_817_03_Full.png"},
  {label: "意见反馈", value: "SUGGESTION", icon: "https://static.kivo.wiki/images/gallery/D1.%E6%B8%B8%E6%88%8F%E5%86%85%E7%B4%A0%E6%9D%90/%E5%8A%A0%E8%BD%BD%E8%BF%87%E5%9C%BA/Event_Photo_CollectionCG_824_03_Full.png"},
  {label: "其他问题", value: "OTHER", icon: "https://static.kivo.wiki/images/gallery/D1.%E6%B8%B8%E6%88%8F%E5%86%85%E7%B4%A0%E6%9D%90/%E5%8A%A0%E8%BD%BD%E8%BF%87%E5%9C%BA/Event_Photo_CollectionCG_818_02_Full.png"}
]

const feedbackForm = ref<FeedBackImpl>({
  id: "", target_id: "", ipv4: null, ipv6: null, author: null, email: null, content: "", echo: null, type: "OTHER", status: "PENDING", created_at: new Date(), updated_at: new Date()
});
const {userBasic} = storeToRefs(userPinia);
const nowItem = ref<string>("OTHER");

const uploadFeedback = async () => {
  if (feedbackForm.value.content === "") {
    infoMessage("投诉内容不得为空！"); return;
  }
  if (userBasic.value) {
    feedbackForm.value.author = userBasic.value.uuid;
  }
  feedbackForm.value.target_id = crypto.randomUUID().toString();
  try {
    const response = await baseHttp.post("/feedback/upload", feedbackForm.value);
    const data: ResponseImpl = response.data;
    if (data.code === 0) successMessage(data.data);
    else errorMessage(data.msg);
  } catch {
    errorMessage("网络错误！");
  }
}
</script>

<template>
  <div class="container">
    <div class="title">
      <h1>投诉/反馈关于B.A.R.C.的常规问题</h1>
      <p>若您希望投诉反馈评论、留言、作品等客观主体的内容，请移步至其详情界面</p>
    </div>
    <div class="item_box">
      <div :class="`item${nowItem === item.value? ' item_select': ''}`" v-for="item in items" :key="item.value" @click="nowItem = item.value">
        <img class="icon" :src="item.icon" alt="icon"/>
        {{item.label}}
      </div>
    </div>
    <a-input class="text_input" v-model:value="feedbackForm.email" v-if="userBasic === null" placeholder="您未登录，若希望跟踪反馈状态，可以选择填写电子邮箱"/>
    <a-textarea class="textarea text_input" v-model:value="feedbackForm.content"/>
    <a-button class="button" @click="uploadFeedback" type="primary">发送表单</a-button>
  </div>
</template>

<style scoped>
.container {
  width: 90%;
  margin: 2rem auto 0;
  background-color: #00000030;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: white;
  text-shadow: #383838 0 0 .3rem;
  padding: 1rem;
  gap: 1rem;
  .text_input {
    width: 70%;
  }
  .textarea {
    height: 10rem;
  }
}
.title {
  padding: 3rem;
  text-align: center;
}
.item_box {
  display: flex;
  gap: 2rem;
}
.item {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  transition: .8s ease;
  .icon {
    width: 10rem;
  }
}
.item:hover {
  cursor: pointer;
  background-color: #ffffff50;
}
.item_select {
  background-color: #ffffff90;
}
.button {
  font-size: 1rem;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1.5rem 3rem;
}
</style>
