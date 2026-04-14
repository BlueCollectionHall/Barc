<script setup lang="ts">
import {onMounted, ref, watch} from "vue";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();
import {useRouter, useRoute} from "vue-router";
import type {FeedBackImpl} from "@/interfaces/FeedbackImpl.ts";
import type {MessageBoardWithUserImpl} from "@/interfaces/MessageBoardImpl.ts";
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
const router = useRouter();
const route = useRoute();

interface ItemImpl {label: string; value: string; icon: string; is_self: boolean}

const items: Array<ItemImpl> = [
  {label: "账号修改", value: "UpdateAccount", icon: "SettingOutlined", is_self: true},
  {label: "上传作品", value: "UploadWork", icon: "ArrowUpOutlined", is_self: true},
  {label: "内容管理", value: "Manage", icon: "SlidersOutlined", is_self: true},
  {label: "投诉此人", value: "FeedbackUser", icon: "AlertOutlined", is_self: false},
  {label: "登出账号", value: "Logout", icon: "LogoutOutlined", is_self: true},
];

const {userBasic} = storeToRefs(userPinia);
const routeUsername = ref<string | null>(null);
const open = ref<boolean>(false);
const feedbackForm = ref<FeedBackImpl>({
  id: "", target_id: "", ipv4: null, ipv6: null, author: null, email: null, content: "", echo: null, type: "USER", status: "PENDING", created_at: new Date(), updated_at: new Date()
});

const itemClicked = (itemValue: string) => {
  switch (itemValue) {
    case "FeedbackUser": {
      showModal(); break;
    } case "Logout": {
      window.localStorage.removeItem("token");
      router.go(0);
      break;
    } case "UploadWork": {
      router.push({name: itemValue, query: {username: routeUsername.value}});
      break;
    } case "Manage": {
      router.push({name: itemValue});
      break;
    } case "UpdateAccount": {
      router.push({name: itemValue});
      break;
    }
  }
}

onMounted(() => {
  const username = route.query.username as string;
  if (username) {
    routeUsername.value = username;
  }
})
const showModal = async () => {
  if (userBasic.value !== null) feedbackForm.value.author = userBasic.value.uuid;
  open.value = true;
};

const handleOk = async (e: MouseEvent) => {
  console.log(e);
  if (routeUsername.value === null) {
    errorMessage("用户名数据获取异常");
    return;
  }
  if (feedbackForm.value.content !== "") {
    try {
      const responseTargetArchive = await baseHttp.get("/user/current_by_username", {params: {username: routeUsername.value}});
      const dataTargetArchive: ResponseImpl = responseTargetArchive.data;
      if (dataTargetArchive.code !== 0) {
        errorMessage(dataTargetArchive.msg);
        return;
      }
      const targetArchive: UserArchiveImpl = dataTargetArchive.data as UserArchiveImpl;
      feedbackForm.value.target_id = targetArchive.uuid;
      const response = await baseHttp.post("/feedback/upload", feedbackForm.value);
      const data: ResponseImpl = response.data;
      if (data.code === 0) {
        successMessage(data.data);
        feedbackForm.value.content = "";
        feedbackForm.value.email = "";
        feedbackForm.value.target_id = "";
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
</script>

<template>
  <a-modal v-model:open="open" title="您要投诉这位用户吗？" ok-text="投诉" cancel-text="关闭" @ok="handleOk">
    <h3>目标的用户名：{{routeUsername}}</h3>
    <p style="color: #fe4b7b">#请放心，对方无法知道您的信息！</p>
    <div v-if="userBasic === null">
      您未登录，若要跟踪进度，可以输入您的电子邮箱：
      <a-input v-model:value="feedbackForm.email" type="email" placeholder="电子邮箱"/>
    </div>
    <br/>
    <a-textarea v-model:value="feedbackForm.content"/>
  </a-modal>
  <header class="header">
    <!--  logo  -->
    <div class="logo">
      <router-link style="text-decoration: none; color: #000" to="/">B.A.R.C</router-link>
    </div>

    <!--  menu  -->
    <div class="menu-container">
      <div class="menu-item"
        v-for="item in items"
        :key="item.value"
        @click="itemClicked(item.value)">
        <div v-if="item.is_self && routeUsername === userBasic?.username">
          <component :is="item.icon"/>
          {{item.label}}
        </div>
        <div v-else-if="!item.is_self">
          <component :is="item.icon"/>
          {{item.label}}
        </div>
      </div>
    </div>

  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative; /* 定位参考基准 */
  height: 70px;
  background-color: #ffffff70;
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  overflow: hidden;
  padding: 10px;
  padding-inline: 30px;
  transition: all 0.5s;
  color: #000000;
}
.container:hover {
  background-color:  #ffffff;
}


/* -- menu-container style -- */
.menu-container {
  display: flex;
  flex-direction: row;

  .menu-item {
    margin-inline: 10px;
    font-family: "HarmonyOS Sans", sans-serif;
    color: #000000;
    text-decoration: none;
    font-size: 18px;
    cursor: pointer;
    /* 确保不影响点击事件 */
    pointer-events: auto;
    /* 禁止用户选中 */
    user-select: none;
    /* 下划线样式 */
    background: linear-gradient(to right, #9b9b9b, #9b9b9b) no-repeat right bottom;
    background-size: 0 2px;
    transition: background-size .2s;

  }

  .menu-item:hover {
    background-position: left bottom;
    background-size: 100% 2px;
  }

  /* 被激活时的样式 */

  .menu-item.active {
    /* 下划线样式 */
    background: linear-gradient(to right, #9b9b9b, #9b9b9b) no-repeat right bottom;
    background-size: auto 2px;
  }
}
</style>
