<script setup lang="ts">
import {computed, onMounted, ref, watch} from "vue";
import {getUserPermissionOptions} from "@/Utils/UserPermission.ts";
import {getManagerPermissionOptions} from "@/Utils/ManagerPermission.ts";
import type {UserInfoImpl} from "@/Interfaces/UserImpl.ts";
import type {PageRequestImpl, PageResultImpl} from "@/Interfaces/PageImpl.ts";
import {baseHttp} from "@/Utils/Request.ts";
import type {ResponseImpl} from "@/Interfaces/ResponseImpl.ts";
import {errorMessage, infoMessage, successMessage} from "@/Utils/MessageAlert.ts";

interface SelectFormImpl {
  username: string; nickname: string; identity: "USER" | "MANAGER"; permission?: number;
}
interface PermissionChangeFormImpl {
  uuid: string; permission_list: Array<number>; identity: "USER" | "MANAGER"; nickname: string; username: string; permission: number;
}

const identityOptions = [
  {value: "USER", label: "用户"},
  {value: "MANAGER", label: "管理员"},
];

const selectForm = ref<SelectFormImpl>({username: '', nickname: '', identity: "USER", permission: 1});
const pageResult = ref<PageResultImpl<UserInfoImpl> | null>(null);
const pageRequest = ref<PageRequestImpl>({page_num: 1, page_size: 20});
const isChangePermissionDialogShow = ref<boolean>(false);
const changePermissionForm = ref<PermissionChangeFormImpl>({uuid: '', permission_list: [], identity: "USER", nickname: '', username: '', permission: 0});

// 计算属性：根据身份动态获取权限选项（搜索栏用）
const permissionOptions = computed(() => {
  return selectForm.value.identity === 'MANAGER'? getManagerPermissionOptions() : getUserPermissionOptions();
})
// 计算属性：根据身份动态获取权限选项（dialog用）
const dialogPermissionOptions = computed(() => {
  return changePermissionForm.value.identity === 'MANAGER'? getManagerPermissionOptions() : getUserPermissionOptions();
})

// 格式化显示权限文本
const formatPermissionText = (permission: number): string => {
  const options = permissionOptions.value;
  const selectedOption = options.find(option => option.value === permission);
  return selectedOption ? selectedOption.label : `未知权限${permission}`;
}

// 分页获取用户信息
const fetchUserInfo = async () => {
  try {
    pageRequest.value.params = selectForm.value;
    const response = await baseHttp.post("/user/query/all_by_page", pageRequest.value);
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      pageResult.value = null;
      pageResult.value = data.data as PageResultImpl<UserInfoImpl>;
    } else {
      infoMessage(data.data);
    }
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

// 重置按钮点击操作
const resetClicked = () => {
  selectForm.value = {
    username: '',
    nickname: '',
    identity: 'USER',
    permission: 1,
  }
  fetchUserInfo();
}

// 分页切换处理
const handlePageChange = (pageNum: number) => {
  pageRequest.value.page_num = pageNum; // 更新当前页码
  fetchUserInfo(); // 重新获取数据
}

// 显示修改权限弹窗
const changePermissionShow = (uuid: string, nickname: string, username: string) => {
  changePermissionForm.value.uuid = uuid;
  changePermissionForm.value.nickname = nickname;
  changePermissionForm.value.username = username;
  isChangePermissionDialogShow.value = true;
}

// 确认修改用户权限
const changePermission = async () => {
  try {
    const response = await baseHttp.post("/user/permission/change_permission", changePermissionForm.value, {headers: {Authorization: localStorage.getItem("token")}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      isChangePermissionDialogShow.value = false;
      successMessage(data.data);
    } else errorMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

onMounted(() => {
   fetchUserInfo();
})

// 监听身份变化，重置权限为默认值
watch(() => selectForm.value.identity, () => {
  selectForm.value.permission = 1;
})
watch(() => changePermissionForm.value.identity, () => {
  changePermissionForm.value.permission_list = [];
})
// 监听权限值变化，计算权限值
watch(() => changePermissionForm.value.permission_list, (newVal: Array<number>) => {
  changePermissionForm.value.permission = 0;
  newVal.map((value: number) => {
    changePermissionForm.value.permission += value;
  });
})
// 监听dialog显示状态
watch(() => isChangePermissionDialogShow.value, (newVal: boolean) => {
  if (!newVal) {
    changePermissionForm.value.identity = "USER";
    changePermissionForm.value.permission_list = [];
    changePermissionForm.value.permission = 0;
  }
})
</script>

<template>
  <el-dialog :title="`修改【${changePermissionForm.nickname} #${changePermissionForm.username}】的权限 -> ${changePermissionForm.permission}`" v-model="isChangePermissionDialogShow">
    <el-form label-width="auto" :model="changePermissionForm" @submit.prevent="changePermission">
      <el-form-item label="角色：">
        <el-select v-model="changePermissionForm.identity">
          <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="权限：">
        <el-checkbox-group v-model="changePermissionForm.permission_list">
          <el-checkbox v-for="item in dialogPermissionOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">确定</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
  <div class="container">
    <el-form class="select_bar" label-width="auto" :model="selectForm" @submit.prevent="fetchUserInfo">
      <el-form-item label="用户名：">
        <el-input v-model="selectForm.username"/>
      </el-form-item>
      <el-form-item label="昵称：">
        <el-input v-model="selectForm.nickname"/>
      </el-form-item>
      <el-form-item label="角色：">
        <el-select v-model="selectForm.identity" placeholder="选择角色" style="width: 10rem">
          <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select>
      </el-form-item>
      <el-form-item label="权限：">
        <el-select v-model="selectForm.permission" placeholder="选择权限" style="width: 12rem">
          <el-option v-for="item in permissionOptions" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button native-type="submit" type="primary">搜索</el-button>
        <el-button native-type="reset" type="default" @click="resetClicked">重置</el-button>
        <el-button native-type="button" type="warning">以当前检索刷新</el-button>
      </el-form-item>
    </el-form>
    <div class="user_cards">
      <div class="no_list" v-if="!pageResult || pageResult.list.length === 0">
        <img class="icon" src="https://static.kivo.wiki/images/gallery/4.%E5%AE%98%E6%96%B9-%E5%9B%BD%E6%9C%8D%E7%B4%A0%E6%9D%90/%E4%BC%98%E9%A6%99_11.png" alt="icon"/>
        <h1>没有数据</h1>
      </div>
      <div class="user_card" v-else v-for="item in pageResult.list" :key="item.uuid">
        <div class="head">
          <img class="avatar" :src="item.avatar" alt="avatar"/>
          <div class="nickname_username">
            <span class="nickname">{{item.nickname}}</span>
            <span class="username">#{{item.username}}</span>
          </div>
          <div class="buttons">
            <el-button @click="changePermissionShow(item.uuid, item.nickname, item.username)" native-type="button">修改</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-pagination class="pagination"
                   background v-if="pageResult"
                   :page-size="pageResult.page_size"
                   :pager-count="pageResult.total_page"
                   layout="prev, pager, next"
                   :total="pageResult.total"
                   :current-page="pageRequest.page_num"
                    @current-change="handlePageChange"/>
  </div>
</template>

<style scoped>
.container :deep(.el-form-item__label) {
  color: #fff;
}
.container {
  animation: container_in 1s ease forwards;
  height: 100%;
  overflow: auto;
  background-color: rgba(84, 92, 100, .5);
  backdrop-filter: blur(5px);
  box-shadow: white 0 0 5px;
  display: flex;
  flex-direction: column;
  .pagination {
    margin-top: auto;
    margin-bottom: 2rem;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .select_bar {
    border-bottom: #deefff solid 1px;
    padding: 2rem 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 1rem;
  }
  .user_cards {
    display: grid;
    padding: 1rem;
    grid-template-columns: repeat(4, 1fr); /* 每行4列 */
    gap: 1rem; /* 卡片之间的间距 */
    .user_card {
      padding: 1rem;
      background-color: rgba(222, 239, 255, 0.5);
      .head {
        display: flex;
        align-items: center;
        flex-direction: row;
        gap: 1rem;
        .avatar {
          width: 5rem;
        }

        .buttons {
          margin-left: auto;
        }
      }

    }
    .no_list {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      color: white;
      text-shadow: #deefff 0 0 5px;
    }
  }
}
.nickname_username {
  display: flex;
  flex-direction: column;
  .nickname {
    font-size: 1.2rem;
  }
}
</style>