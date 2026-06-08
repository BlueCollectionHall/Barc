<script setup lang="ts">
import {onMounted, ref} from "vue";
import {
  AlertOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  ExclamationCircleOutlined,
  HeartOutlined,
  SearchOutlined,
} from "@ant-design/icons-vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage} from "@/utils/MessageAlert.ts";
import {timestampToCn} from "@/utils/TimeToCn.ts";
import {useRouter} from "vue-router";
import {
  type ManageWorkFilterState,
  buildManageWorkFilterParams,
} from "@/utils/manageWorkEditHelpers.ts";
const userPinia = useUserPinia();
const router = useRouter();

const {userArchive} = storeToRefs(userPinia);
const workList = ref<Array<WorkImpl>>([]);
const menuStatus = ref<string>("PUBLIC");
const filterState = ref<ManageWorkFilterState>({type: "keyword", value: ""});

const fetchWorkList = async (status: string) => {
  menuStatus.value = status;
  if (!userArchive.value?.uuid) return;
  try {
    const response = await baseHttp("/api/work/works_by_uuid", {
      params: buildManageWorkFilterParams(userArchive.value.uuid, status, filterState.value),
    })
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      workList.value = data.data;
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误");
  }
}

const searchCurrentStatus = async () => {
  // 搜索只作用于当前状态列表，避免切换公开/私有时误带旧状态。
  await fetchWorkList(menuStatus.value);
}

const resetSearch = async () => {
  filterState.value.value = "";
  await fetchWorkList(menuStatus.value);
}

const editWork = (workId: string) => {
  router.push({name: "ManageWorkEdit", query: {work_id: workId}});
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
        <div class="search_hint">在“{{menuStatus}}”里找作品</div>
        <el-select class="filter_select" v-model="filterState.type">
          <el-option label="关键词" value="keyword" />
          <el-option label="学园" value="school" />
          <el-option label="部团" value="club" />
          <el-option label="学生" value="student" />
        </el-select>
        <el-input
          class="input"
          v-model="filterState.value"
          placeholder="输入一点线索就好～"
          clearable
          @keyup.enter="searchCurrentStatus" />
        <el-button class="search_button" type="primary" @click="searchCurrentStatus"><SearchOutlined />搜索</el-button>
        <el-button class="reset_button" @click="resetSearch">清空</el-button>
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
            <el-button class="button" type="primary" @click="editWork(item.id)"><EditOutlined />编辑</el-button>
          </div>
        </div>
        <div class="empty_box" v-if="workList.length === 0">
          <img class="empty_icon" src="https://static.kivo.wiki/images/gallery/E1.%E5%AE%98%E6%96%B9%E8%A1%A8%E6%83%85%E5%8C%85/Default/cafabb328c6564d3445ebaa00e1c510f.gif" alt="empty" />
          <span>这个状态里暂时没有匹配作品</span>
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
  align-items: center;
  width: 90%;
  margin: 0 auto;
  gap: .6rem;
  padding: .8rem 1rem;
  border-radius: .8rem;
  background-color: #f8fcff;
  border: #d9f3ff 1px solid;
}
.search_hint {
  white-space: nowrap;
  color: #00AEEC;
  font-weight: bold;
}
.filter_select {
  width: 7rem;
}
.search_button, .reset_button {
  min-width: 5rem;
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
.empty_box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  color: #787878;
  padding: 3rem;
  .empty_icon {
    width: 8rem;
  }
}
</style>
