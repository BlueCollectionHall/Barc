<script setup lang="ts">
import HeaderComp from "@/Components/HeaderFooter/HeaderComp.vue";
import {useUserStore} from "@/Stores/UserStore.ts";
import {onBeforeMount} from "vue";
import {useRouter, useRoute} from "vue-router";
import {infoMessage} from "@/Utils/MessageAlert.ts";
import AppBgComp from "@/Components/App/AppBgComp.vue";
import AppContainerComp from "@/Components/App/AppContainerComp.vue";
import AppSideMenuComp from "@/Components/App/AppSideMenuComp.vue";
import {storeToRefs} from "pinia";

const userStore = useUserStore();
const router = useRouter();
const route = useRoute();

const {userArchive} = storeToRefs(userStore);

const logout = () => {
  window.localStorage.removeItem("token");
  userStore.userLogout();
  router.replace({name: "Sign"});
}

onBeforeMount(() => {
  // 初始化登录状态
  const token = localStorage.getItem("token");
  if (token) {
    userStore.fetchUserInfo(token);
  } else {
    if (route.path === "/") {
      infoMessage("请您先登录！");
      router.push({name: 'Sign'});
    }
  }
})

</script>

<template>
<!--  已经登录-->
  <div v-if="userArchive">
    <HeaderComp/>
    <div class="root_container" v-if="userArchive.identity === 'MANAGER'">
      <AppBgComp/>
      <el-container>
        <AppSideMenuComp/>
        <AppContainerComp/>
      </el-container>
    </div>
    <div class="no_permission_container" v-else>
      <div class="title">
        <img class="title_icon" src="https://static.kivo.wiki/images/gallery/16/ClanChat_Emoji_Dummy03.png" alt="img"/>
        <div>No Permission!</div>
      </div>
      <div class="content">
        <div class="tip">非常抱歉</div>
        <div class="tip">您的账号权限不足以访问B.A.R.C.管理平台</div>
        <p>您可以在<a href="https://www.barc.work">B.A.R.C.用户端</a>找到[加入我们] -> [站内协助]提交成为管理员的表单</p>
        <p>收到表单后将尽快为您处理，期待与您相遇</p>
        <div class="func">
          您可以
          <el-button @click="logout" native-type="button">退出登录</el-button>
        </div>
      </div>
    </div>
  </div>
<!--  未登录-->
  <div v-else>
    <RouterView/>
  </div>
</template>

<style scoped>
.no_permission_container {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2rem;
  flex-direction: column;
  .title {
    .title_icon {
      width: 10rem;
    }
    text-align: center;
    font-size: 2rem;
    font-weight: bold;
  }
  .content {
    text-align: center;
    .tip {
      font-size: 1.5rem;
      font-weight: bold;
    }
    p {
      font-size: 1.1rem;
      font-weight: bold;
    }
    .func {
      color: #9b9b9b;
      font-weight: bold;
      display: flex;
      justify-content: center;
      align-items: center;
      flex-direction: column;
      gap: .5rem;
    }
  }
}
</style>
