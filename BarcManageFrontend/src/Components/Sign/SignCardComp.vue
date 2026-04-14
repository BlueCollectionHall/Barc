<script setup lang="ts">
import {ref} from "vue";
import {errorCatchMessage, errorMessage, successMessage, warningMessage} from "@/Utils/MessageAlert.ts";
import {baseHttp} from "@/Utils/Request.ts";
import type {ResponseImpl} from "@/Interfaces/ResponseImpl.ts";
import {useRouter} from "vue-router";
import {useUserStore} from "@/Stores/UserStore.ts";

const userStore = useUserStore();
const router = useRouter();

const token = ref<string | null>(window.localStorage.getItem("token"));

interface Form {
  account: string;
  password: string;
  type: string;
}

const form = ref<Form>({
  account: "",
  password: "",
  type: "username",
});

// 登录
const signin = async () => {
  if (form.value.account.length === 0 || form.value.password === "") {
    errorMessage("缺少必填信息！");
    return;
  }
  try {
    const response = await baseHttp.get('/user/sign/in', {params: form.value});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      const token = data.data;
      // 写入token
      window.localStorage.setItem("token", token);
      // 刷新个人信息状态
      await userStore.fetchUserInfo(token);
      successMessage("登录成功！");
      // 返回主页
      await router.replace({name: "Home"});
    } else {
      warningMessage(data.data);
    }
  } catch {
    errorCatchMessage();
  }
}
// 去BARC
const toBarc = () => {
  window.location.href = "https://www.barc.work";
}


</script>

<template>
  <div class="card_box" ref="cardBox">
    <div class="title">
      <span>B.A.R.C.管理平台</span>
    </div>
    <el-form class="form_box" label-width="auto" :model="form" @submit.prevent="signin">
      <el-form-item label="账号：" required>
        <el-input v-model="form.account" :disabled="token"/>
      </el-form-item>
      <el-form-item label="密码：" required>
        <el-input type="password" v-model="form.password" :disabled="token"/>
      </el-form-item>
      <el-form-item label="登录方式：" required>
        <el-radio-group v-model="form.type" :disabled="token">
          <el-radio value="username">用户名</el-radio>
          <el-radio value="email">电子邮箱</el-radio>
        </el-radio-group>
      </el-form-item>
      <div class="button_box">
        <el-button id="to_barc" type="default" native-type="button" @click="toBarc">去客户端</el-button>
        <el-button id="signin" type="primary" native-type="submit" :disabled="token">登录</el-button>
        <el-button id="reset_pwd" type="warning" native-type="button">找回密码</el-button>
      </div>
    </el-form>

  </div>
</template>

<style scoped>
@keyframes card_in {
  0% {
    opacity: 0;
    transform: translateY(-3rem);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}
.card_box:hover {
  background-color: white;
}
.card_box {
  position: absolute;
  top: 30%;
  left: 15%;
  animation: card_in .8s ease forwards;
  transition: .3s ease;
  border-radius: 3rem;
  padding: 3rem 4rem;
  background-color: rgba(255, 255, 255, 0.9);
  width: 40rem;
  &:hover .title {
    color: black;
    border-bottom: #9b9b9b 1px solid;
  }
  .title {
    font-weight: bold;
    transition: .3s ease;
    letter-spacing: .2rem;
    text-align: center;
    color: #9b9b9b;
    font-size: 1.5rem;
    border-bottom: white 1px solid;
  }
  .form_box {
    padding: 1rem;
  }
  .button_box {
    display: flex;
    justify-content: center;
    #signin {
      width: 10rem;
    }
  }
}
</style>