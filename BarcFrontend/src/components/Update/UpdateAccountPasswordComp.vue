<script setup lang="ts">
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {ref} from "vue";
import {errorMessage, successMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
const userPinia = useUserPinia();

interface ResetPasswordFormImpl {
  unique_id: string;
  email: string;
  password: string;
  code: string;
}

const {userBasic} = storeToRefs(userPinia);

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
  <div class="change">
    <div class="title">
      <h3>修改密码</h3>
      <span>通过输入原密码以及验证码，更换新密码</span>
    </div>
    <el-form :model="form" label-width="auto" @submit.prevent="resetPassword">
      <el-form-item label="新密码：">
        <div style="display: flex; width: 100%; gap: 1rem">
          <el-input v-model="form.password" :disabled="countdown !== 60" type="password"/>
          <el-button :disabled="countdown !== 60" @click="fetchCode" native-type="button">{{countdown === 60? '获取验证码': `${countdown}秒再试`}}</el-button>
        </div>
      </el-form-item>
      <el-form-item label="验证码：">
        <el-input placeholder="6位验证码" v-model="form.code"/>
      </el-form-item>
      <el-button native-type="submit" type="primary">确认修改</el-button>
    </el-form>
  </div>

</template>

<style scoped>
.title {
  margin-bottom: 2rem;
  span {
    font-size: .9rem;
  }
}
.change {
  margin-bottom: 4rem;
}
</style>
