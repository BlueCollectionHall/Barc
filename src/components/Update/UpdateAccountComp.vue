<script setup lang="ts">
import {onMounted, ref} from "vue";
import {errorMessage, successMessage} from "@/utils/MessageAlert.ts";
import {useRouter} from "vue-router";
import {storeToRefs} from "pinia";
const router = useRouter();

interface ResetPasswordFormImpl {
  unique_id: string;
  email: string;
  password: string;
  code: string;
}

const menuStatus = ref<string>("UpdateAccountBasic");

const menuClicked = (routerName: string) => {
  menuStatus.value = routerName;
  router.replace({name: routerName});
}

onMounted(() => {
  const token = window.localStorage.getItem("token");
  if ( !token ) {
    errorMessage("未检测到登录状态！");
    router.back();
    return;
  }
  router.replace({name: menuStatus.value});
})

import {useUserPinia} from "@/stores/UserPinia.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import UpdateAccountAvatarComp from "@/components/Update/UpdateAccountAvatarComp.vue";
import UpdateAccountBasicComp from "@/components/Update/UpdateAccountBasicComp.vue";
import UpdateAccountArchiveComp from "@/components/Update/UpdateAccountArchiveComp.vue";
import UpdateAccountPasswordComp from "@/components/Update/UpdateAccountPasswordComp.vue";
const userPinia = useUserPinia();
const {userBasic, userArchive} = storeToRefs(userPinia);

// 修改密码相关
const form = ref<ResetPasswordFormImpl>({
  unique_id: "", password: "", code: "", email: ""
});
const countdown = ref<number>(60);

const fetchCode = async () => {
  if (form.value.password.length < 8) {
    errorMessage("新密码至少8个字符！");
    return;
  }
  if (userBasic.value) {
    form.value.email = userBasic.value.email;
    try {
      const response = await baseHttp.get("/email/send/reset_password", {
        params: {email: form.value.email}
      });
      const data: ResponseImpl = response.data;
      if (data.code === 0) {
        form.value.unique_id = data.data;
        const time = setInterval(() => {
          if (countdown.value === 0) {
            countdown.value = 60;
            clearInterval(time);
          } else countdown.value--;}, 1000)
      }
    } catch {
      errorMessage("网络错误！");
    }
  } else {
    errorMessage("未检测到登录状态！");
    return;
  }
}

const resetPassword = async () => {
  if (form.value.code.length === 0) {
    errorMessage("请输入6位验证码");
    return;
  }
  if (form.value.password.length < 8) {
    errorMessage("密码至少为8位数");
    return;
  }
  if (form.value.unique_id.length === 0 || form.value.email.length === 0) {
    errorMessage("程序出现错误");
    return;
  }
  try {
    const response = await baseHttp.get("/user/reset_password", {
      params: form.value,
      headers: {Authorization: window.localStorage.getItem("token")}
    });
    const data: ResponseImpl = response.data;
    if (data.code === 0) successMessage(data.data);
    else errorMessage(data.msg);
  } catch {
    errorMessage("网络错误！");
  }
}
</script>

<template>
<!--  <el-container class="container">-->
<!--    <el-aside class="side_box box">-->
<!--      <el-menu :default-active="menuStatus" class="side_menu">-->
<!--        <el-menu-item index="UpdateAccountAvatar" class="side_menu_item" @click="menuClicked('UpdateAccountAvatar')">-->
<!--          <el-icon><EyeOutlined /></el-icon>-->
<!--          <span>个人头像</span>-->
<!--        </el-menu-item>-->
<!--        <el-menu-item index="UpdateAccountArchive" class="side_menu_item" @click="menuClicked('UpdateAccountPassword')">-->
<!--          <el-icon><ExclamationCircleOutlined /></el-icon>-->
<!--          <span>修改密码</span>-->
<!--        </el-menu-item>-->
<!--        <el-menu-item index="UpdateAccountBasic" class="side_menu_item" @click="menuClicked('UpdateAccountBasic')">-->
<!--          <el-icon><EyeInvisibleOutlined /></el-icon>-->
<!--          <span>基础资料</span>-->
<!--        </el-menu-item>-->
<!--        <el-menu-item index="UpdateAccountArchive" class="side_menu_item" @click="menuClicked('UpdateAccountArchive')">-->
<!--          <el-icon><ExclamationCircleOutlined /></el-icon>-->
<!--          <span>自定资料</span>-->
<!--        </el-menu-item>-->
<!--      </el-menu>-->
<!--    </el-aside>-->
<!--    <el-main class="container box">-->
<!--      <RouterView/>-->
<!--    </el-main>-->
<!--  </el-container>-->
<!--  <div class="container">-->
<!--    <div class="edit_avatar_box box">-->
<!--      <h2>个人头像</h2>-->
<!--    </div>-->
<!--    <div class="edit_basic_box box">-->
<!--      <h2>基础资料</h2>-->
<!--      <el-form v-if="userBasic" :model="userBasic" label-width="auto">-->
<!--        <el-form-item label="用户名：" required>-->
<!--          <el-input placeholder="用户名唯一性，用于查找到您" v-model="userBasic.username"/>-->
<!--        </el-form-item>-->
<!--        <el-form-item label="电话：">-->
<!--          <el-input v-model="userBasic.telephone" placeholder="社交展示，非必须"/>-->
<!--        </el-form-item>-->
<!--        <el-button native-type="submit" type="primary">确认修改</el-button>-->
<!--      </el-form>-->
<!--    </div>-->
<!--    <div class="edit_archive_box box">-->
<!--      <h2>自定资料</h2>-->
<!--      <el-form v-if="userArchive" :model="userArchive" label-width="auto">-->
<!--        <el-form-item label="昵称：" required>-->
<!--          <el-input v-model="userArchive.nickname"/>-->
<!--        </el-form-item>-->
<!--        <el-form-item label="性别：" required>-->
<!--          <el-radio-group v-model="userArchive.gender">-->
<!--            <el-radio :value="0">男</el-radio>-->
<!--            <el-radio :value="1">女</el-radio>-->
<!--          </el-radio-group>-->
<!--        </el-form-item>-->
<!--        <el-form-item label="生日：" required>-->
<!--          <el-date-picker-->
<!--            v-model="userArchive.birthday"-->
<!--            type="date"-->
<!--            placeholder="选择日期"-->
<!--            :size="'default'"-->
<!--          />-->
<!--        </el-form-item>-->
<!--        <el-button native-type="submit" type="primary">确认修改</el-button>-->
<!--      </el-form>-->
<!--    </div>-->
<!--    <div class="edit_password_box box">-->
<!--      <h2>修改密码</h2>-->
<!--      <el-form :model="form" label-width="auto" @submit.prevent="resetPassword">-->
<!--        <el-form-item label="新密码：">-->
<!--          <div style="display: flex; width: 100%; gap: 1rem">-->
<!--            <el-input v-model="form.password" :disabled="countdown !== 60" type="password"/>-->
<!--            <el-button :disabled="countdown !== 60" @click="fetchCode" native-type="button">{{countdown === 60? '获取验证码': `${countdown}秒再试`}}</el-button>-->
<!--          </div>-->
<!--        </el-form-item>-->
<!--        <el-form-item label="验证码：">-->
<!--          <el-input placeholder="6位验证码" v-model="form.code"/>-->
<!--        </el-form-item>-->
<!--        <el-button native-type="submit" type="primary">确认修改</el-button>-->
<!--      </el-form>-->
<!--    </div>-->
<!--  </div>-->
  <div class="container">
    <div class="c1">
      <UpdateAccountAvatarComp/>
      <UpdateAccountBasicComp/>
    </div>
    <div class="c2">
      <UpdateAccountArchiveComp/>
      <UpdateAccountPasswordComp/>
    </div>
    <div class="c3">
      <div class="reset">
        <div class="title">
          <h3>重置密码/账号冻结</h3>
          <span>若您账号目前高危，需要B.A.R.C.紧急介入保护，请在这里做出选择！</span>
        </div>
        <br/>
        <div class="button_box">
          <p>紧急冻结：通过提交投诉反馈的方式，由管理员为您手动解冻账号；</p>
          <p>重置密码：自动将您的密码进行更换为一个随机密码，新密码将发送到您的电子邮箱，若没有收到，可联系管理员；</p>
          <p>全机下线：系统将解除账号在所有设备和页面的登录状态；</p>
          <el-button type="danger">紧急冻结</el-button>
          <el-button type="danger">重置密码</el-button>
          <el-button type="warning">全机下线</el-button>
        </div>
      </div>
      <div class="stop">
        <div class="title">
          <h3>账号处置</h3>
          <span>若您放弃该账号，可以由您自行处置这个账号，这通常是不可逆的操作，也可以不处置这个账号！</span>
        </div>
        <br/>
        <div class="button_box">
          <p>纪念账号：账号将会禁止被登录，可以联系管理员解除；</p>
          <p>关闭账号：账号仅能被浏览，无法再登录；</p>
          <p>注销账号：账号将被软删除，您可以在1个月内重启账号，否则将进入删除账号环节；</p>
          <p>删除账号：账号将被硬删除，无法找回，名下所有作品将划归“蔚蓝收录助手”账号下；</p>
          <el-button type="default">纪念账号</el-button>
          <el-button type="warning">关闭账号</el-button>
          <el-button type="danger">注销账号</el-button>
          <el-button type="danger">删除账号</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.container {
  min-height: calc(100vh - 70px);
  width: 60%;
  margin: 0 auto;
  padding: 4rem 4rem;
  background-color: rgba(255, 255, 255, 0.8);
  display: grid;
  grid-template-columns: 20rem 1fr 1fr;
  gap: 4rem;
}
.c1, .c2, .c3{
  display: flex;
  flex-direction: column;
  gap: 2rem;
}
.box {
  box-shadow: #9b9b9b 0 0 3px;
  margin-bottom: 1rem;
  padding: 2rem 2rem;
}
.box:last-child {
  margin-bottom: 0;
}
.box h2 {
  color: #333333;
  font-size: 1rem;
  text-align: center;
}
</style>
