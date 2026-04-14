<script setup lang="ts">
import {useRoute} from "vue-router";
import {onMounted, ref} from "vue";
const route = useRoute();
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
const userPinia = useUserPinia();
import {ManOutlined, WomanOutlined} from "@ant-design/icons-vue";

const {userArchive, userBasic} = storeToRefs(userPinia);
const accountArchive = ref<UserArchiveImpl | null>(null);
const accountUsername = ref<string | null>(null);

const fetchAccountArchive = async (username: string) => {
  try {
    const responseArchive = await baseHttp.get("/user/current_by_username", {params: {username: username}});
    const dataArchive: ResponseImpl = responseArchive.data;
    if (dataArchive.code === 0) accountArchive.value = dataArchive.data;
    else infoMessage(dataArchive.msg);
  } catch {
    errorMessage("网络错误！");
  }
}

const judgeAccountArchive = async (username: string) => {
  if (!username) {
    errorMessage("页面没有正确的数值！")
    return;
  }
  accountUsername.value = username;
  if (userBasic.value) {
    if (username === userBasic.value.username) {
      accountArchive.value = userArchive.value;
      return;
    }
  }
  await fetchAccountArchive(username);
}

onMounted(async () => {
  const username: string = route.query.username as string;
  await judgeAccountArchive(username);
})
</script>

<template>
  <div class="user_card_box" v-if="accountArchive">
    <div class="bgz"/>
    <img class="bgi" src="https://static.kivo.wiki/images/gallery/1.%E5%89%A7%E6%83%85-%E5%9B%9E%E5%BF%86%E5%A4%A7%E5%8E%85%EF%BC%88AI%E8%B6%85%E5%88%86%E8%BE%A8%E7%8E%87%E5%A4%84%E7%90%86%EF%BC%89/CH0275_home_Idle_01_1.3332999999999888_waifu2x_2x_3n_png.png" alt="bg"/>
    <img class="avatar" :src="accountArchive.avatar || ''" alt="avatar"/>
    <div class="nickname">{{accountArchive.nickname}}</div>
    <div class="info">
      <ManOutlined style="color: #1677ff" v-if="accountArchive.gender"/>
      <WomanOutlined style="color: #fe4b7b" v-else/>
      ·
      {{accountArchive.age}}岁
      ·
      {{accountUsername}}
    </div>
  </div>
  <div class="user_card_box" v-else></div>
</template>

<style scoped>
.user_card_box {
  width: 100%;
  height: 17rem;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  gap: .4rem;
  position: relative;
  overflow: hidden;
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  .bgz {
    background: linear-gradient(rgba(0, 0, 0, .5) -15%, rgba(0, 0, 0, 0) 60%);
    position: absolute;
    width: 100%;
    height: 100%;
    z-index: -1;
  }
  .bgi {
    position: absolute;
    top: -15rem;
    width: 100%;
    z-index: -2;
    opacity: .8;
  }
}
.avatar {
  width: 128px;
  height: 128px;
  border-radius: 50%;
  border: rgba(255, 255, 255, .4) 3px solid;
}
.nickname {
  color: white;
  text-shadow: 0 1px 2px rgba(0, 0, 0, .4);
  font-size: 1.5rem;
  font-weight: 700;
}
.info {
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: 400;
  color: white;
  text-shadow: 0 1px 2px rgba(0, 0, 0, .4);
}
</style>
