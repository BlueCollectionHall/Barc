<script setup lang="ts">
import "@/Styles/Header.css";
import {onMounted, ref, watch} from "vue";
import {useUserStore} from "@/Stores/UserStore.ts";
import {storeToRefs} from "pinia";
import {useRouter} from "vue-router";

const router = useRouter();
const userStore = useUserStore();
const {userArchive} = storeToRefs(userStore);
// const userLoginStatus = ref<number>(window.localStorage.getItem("token")? 1: 0);
const timeLine = ref<string>('');

const logout = () => {
  window.localStorage.removeItem("token");
  userStore.userLogout();
  router.go(0);
}

onMounted(() => {
  const now = new Date().getHours();
  if (21 <= now || now < 5) {
    timeLine.value = '夜深了，要注意休息！';
  } else if (5 <= now && now < 8) {
    timeLine.value = '早安！这么早就要来B.A.R.C.处理事务了吗？';
  } else if (17 <= now && now < 20) {
    timeLine.value = '今天过的如何呢？'
  }
})

</script>

<template>
  <header class="header">
    <div class="logo">
      <router-link style="text-decoration: none; color: #000" to="/">B.A.R.C</router-link>
    </div>
    <!--      <router-link class="menu-item" to="/" active-class="active">主页</router-link>-->
<!--    <router-link class="menu-item" to="/sign" active-class="active" v-if="userArchive === null || userBasic === null">登录 | 注册</router-link>-->
    <span class="welcome_title">
      <span>欢迎访问B.A.R.C.后台管理系统</span>
      <span v-if="timeLine.length !== 0">{{timeLine}}</span>
    </span>
    <span class="menu-item" v-if="!userArchive">等待登录…</span>
    <img @click="logout" v-else v-if="userArchive.avatar" :src="userArchive.avatar" alt="avatar" style="width: 48px; border: #deefff 2px solid; border-radius: 20px"/>
  </header>
</template>

<style scoped>
.welcome_title {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
}
</style>