<script setup lang="ts">
import "@/Styles/ContainerAni.css";
import "@/Styles/DefaultContainer.css";
import {cnByPermission} from "@/Utils/ManagerPermission.ts";
import {useUserStore} from "@/Stores/UserStore.ts";
import {storeToRefs} from "pinia";

const userStore = useUserStore();

const {userArchive} = storeToRefs(userStore);

</script>

<template>
  <div class="default_container">
    <div class="title">
      <img src="https://static.kivo.wiki/images/gallery/D2.%E6%B8%B8%E6%88%8F%E5%86%85%E6%9D%82%E9%A1%B9/Category/Q-862c2944.png" alt="icon"/>
      <h1>请在左侧选择您要执行的管理任务吧！</h1>
    </div>
    <div class="permission_content">
      <span v-if="userArchive">您的权限是：
        <span v-for="(item, index) in cnByPermission(userArchive.permission)" :key="index">
          {{`${item} `}}
        </span>
      </span>
      <span v-else>加载中…</span>
    </div>
  </div>
</template>

<style scoped>
</style>