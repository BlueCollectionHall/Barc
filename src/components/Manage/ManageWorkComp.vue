<script setup lang="ts">
import {onMounted, ref} from "vue";
import {EyeOutlined, EyeInvisibleOutlined, ExclamationCircleOutlined, AlertOutlined, HeartOutlined} from "@ant-design/icons-vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
const userPinia = useUserPinia();

const {userArchive} = storeToRefs(userPinia);
const workList = ref<Array<WorkImpl>>([]);
const menuStatus = ref<string>("PUBLIC");

const fetchWorkList = async (status: string) => {
  menuStatus.value = status;
  try {
    const response = await baseHttp("/api/work/works_by_uuid", {params: {uuid: userArchive.value?.uuid, status}})
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      workList.value = data.data;
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误");
  }
}

onMounted(async () => {
  const token: string | null = window.localStorage.getItem("token")
  if (token) {
    await userPinia.fetchUserInfo(token);
    await fetchWorkList(menuStatus.value);
  }

})

</script>

<template>
  <el-container class="manage_work_box">
    <el-aside class="side_box box">
      <el-menu :default-active="menuStatus" class="side_menu">
        <el-menu-item index="PUBLIC" class="side_menu_item" @click="fetchWorkList('PUBLIC')">
          <el-icon><EyeOutlined /></el-icon>
          <span>公开作品</span>
        </el-menu-item>
        <el-menu-item index="PRIVATE" class="side_menu_item" @click="fetchWorkList('PRIVATE')">
          <el-icon><EyeInvisibleOutlined /></el-icon>
          <span>私有作品</span>
        </el-menu-item>
        <el-menu-item index="OFF" class="side_menu_item" @click="fetchWorkList('OFF')">
          <el-icon><ExclamationCircleOutlined /></el-icon>
          <span>下架作品</span>
        </el-menu-item>
        <el-menu-item index="BAN" class="side_menu_item" @click="fetchWorkList('BAN')">
          <el-icon><AlertOutlined /></el-icon>
          <span>封禁作品</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="container box">
      <div class="search_bar">
        <el-input class="input"/>
        <el-button class="search_button" type="primary">作品名搜索</el-button>
      </div>
      <div class="work_item_box">
        <div class="work_item" v-for="item in workList" :key="item.id">
          <div class="cover_image_box">
            <img class="cover_image" :src="item.cover_image" alt="cover_image"/>
          </div>
          <div class="info">
            <span class="title">{{item.title}}</span>
            <span class="updated_at">{{timestampToCn(item.updated_at)}}</span>
            <div class="data_bar">
              <div class="view_count_box">
                <EyeOutlined />&nbsp;{{item.view_count}}
              </div>
              <div class="like_count_box">
                <HeartOutlined/>&nbsp;{{item.like_count}}
              </div>
            </div>
          </div>
          <div class="button_box">
            <el-button class="button">编辑</el-button>
          </div>
        </div>
      </div>
    </el-main>
  </el-container>
</template>

<style scoped>
.manage_work_box {
  padding: 4rem 4rem;
  gap: 1rem;
}
.box {
  border-radius: .5rem;
  box-shadow: #9b9b9b 0 0 .5rem;
}

.side_menu {
  background-color: #ffffff;
  height: calc(100vh - 8rem - 70px);
}
.container {
  background-color: #ffffff;
  display: flex;
  flex-direction: column;
  padding: 2rem 2rem;
  overflow: auto;
  max-height: calc(100vh - 8rem - 70px);
}
.search_bar {
  display: flex;
  flex-direction: row;
  width: 80%;
  margin: 0 auto;
}
.work_item_box {
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.work_item {
  padding: 2rem;
  display: grid;
  grid-template-columns: 1fr 3fr 1fr;
  border-bottom: #d1d1d1 1px solid;
}
.work_item:last-child {
  border-bottom: none;
}
.cover_image_box {
  overflow: hidden;
  width: calc(16 * 1rem);
  height: calc(9 * 1rem);

  .cover_image {
    width: 100%;
    border-radius: .5rem;
  }
}
.info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  .title {
    font-size: 1.2rem;
  }
  .data_bar {
    display: flex;
    flex-direction: row;
    gap: 1rem;
  }
}
.button_box {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
}
</style>
