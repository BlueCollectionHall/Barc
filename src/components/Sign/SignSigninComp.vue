<script setup lang="ts">
import {ref} from "vue";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {useRouter} from "vue-router";
const router = useRouter();
import {useUserPinia} from "@/stores/UserPinia.ts";
const userPinia = useUserPinia();
import {CheckOutlined} from "@ant-design/icons-vue";
import {errorMessage} from "@/utils/MessageAlert.ts";

interface SigninFormImpl {
  type: string;
  account: string;
  password: string;
}

const functionSelect = ref<string>("signin");
const loginSource = ref<string>("barc");
const signinForm = ref<SigninFormImpl>({type: "username", account: "", password: ""});

const signinFormFinish = async () => {
  try {
    let response;
    if (loginSource.value === "barc") {
      response = await baseHttp("/user/sign/in", {method: "GET", params: signinForm.value});
    } else {
      if (signinForm.value.type === 'username') {
        signinForm.value.type = 'uid';
      }
      response = await baseHttp("/user/sign/in_by_naigos", {method: "GET", params: signinForm.value});
    }
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      const token: string = data.data as string;
      window.localStorage.setItem("token", token);
      await userPinia.fetchUserInfo(token)
      router.back();
    } else errorMessage(data.data);
  } catch {
    errorMessage("登录失败");
  }
}
</script>

<template>
  <div class="sign_in" v-if="functionSelect === 'signin'">
    <div class="select_login">
      <button :class="loginSource === 'barc'? 'button_select': null" @click="loginSource = 'barc'">
        <CheckOutlined v-if="loginSource === 'barc'" />
        B.A.R.C.登录</button>
      <button :class="loginSource === 'naigos'? 'button_select': null" @click="loginSource = 'naigos'">
        <CheckOutlined v-if="loginSource === 'naigos'" />
        Naigos登录</button>
    </div>
    <hr/>
    <p style="font-size: .9rem; color: brown; text-align: center" v-if="loginSource === 'naigos'">首次使用Naigos登录，将自动注册B.A.R.C.</p>
    <a-form :model="signinForm"
            name="basic"
            style="width: 20rem"
            @finish="signinFormFinish"
            autocomplete="off">
      <a-form-item
        label="账号"
        name="account"
        :rules="[{ required: true, message: '输入账号，要记得选择是用什么方式登录' }]">
        <a-input v-model:value="signinForm.account"/>
      </a-form-item>
      <a-form-item label="登录方式">
        <a-radio-group v-model:value="signinForm.type">
          <a-radio value="username">用户名</a-radio>
          <a-radio value="email">邮箱</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item
        label="密码"
        name="password"
        :rules="[{ required: true, message: 'Please input your password!' }]">
        <a-input-password v-model:value="signinForm.password"/>
      </a-form-item>
      <a-form-item :wrapper-col="{ span: 14, offset: 4 }">
        <a-button type="primary" html-type="submit">登录</a-button>
        <a-button
          v-if="loginSource !== 'naigos'"
          style="background-color: darkred; color: white; border: red 1px solid; margin-left: 1rem" >忘记密码</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<style scoped>
.sign_in {
  border-radius: 2rem;
  padding: 3rem;
  background-color: white;
}
.select_login {
  display: flex;
  justify-content: space-between;
  button {
    background: none;
    border: none;
    transition: .3s ease;
  }
  .button_select {
    color: brown;
  }
  button:hover {
    cursor: pointer;
    transform: translateY(-.15rem);
  }
}

</style>
