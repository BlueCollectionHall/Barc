<script setup lang="ts">
import type {UserBasicImpl} from "@/interfaces/UserImpl.ts";
import {ref, watch} from "vue";
import {baseHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";

interface SignupImpl extends UserBasicImpl{
  email: string;
  unique_id: string;
  code: string;
}

const signupForm = ref<SignupImpl>({
  email: "",
  unique_id: "",
  code: "",
  uuid: "", username: "", password: "", password_version: 0, email_verified: false, telephone: null, safe_level: 100, created_at: new Date(), updated_at: new Date()
});

const countDown = ref<number>(60);

const getCode = async () => {
  if (signupForm.value.email !== "") {
    try {
      const response = await baseHttp("/email/send/signup", {method: "GET", params: {email: signupForm.value.email}});
      const data: ResponseImpl = response.data;
      if (data.code === 0) {
        signupForm.value.unique_id = data.data;
        setInterval(() => {
          if (countDown.value < 0) {
            countDown.value = 60;
          }
          countDown.value--;
        }, 1000);
      }
    } catch (err) {
      console.log("获取验证码失败");
    }
  } else {
    alert("邮箱不得为空");
  }
}
const signup = async () => {
  try {
    const response = await baseHttp("/user/sign/up", {method: "POST", params: {
        unique_id: signupForm.value.unique_id,
        code: signupForm.value.code,
        email: signupForm.value.email,}, data: signupForm.value});
    const data: ResponseImpl = response.data;
    alert(data.msg);
  } catch (e) {
    alert("注册错误");
  }
}
</script>

<template>
  <div class="sign_up">
    <div>注册B.A.R.C.账号</div>
    <hr/>
    <a-form
      :model="signupForm"
      @finish="signup"
      style="width: 20rem">
      <a-form-item label="用户名：" name="username" :rules="[{required: true, message: '用户名是必填项'}]">
        <a-input v-model:value="signupForm.username"/>
      </a-form-item>
      <a-form-item label="电子邮箱：" name="email" :rules="[{required: true, message: '电子邮箱是必填项'}]">
        <a-input v-model:value="signupForm.email"/>
      </a-form-item>
      <a-form-item label="邮箱验证码：">
        <a-input v-model:value="signupForm.code"></a-input>
        <a-button @click="getCode">{{countDown === 60? '获取验证码': `${countDown}s后再试`}}</a-button>
      </a-form-item>
      <a-form-item label="密码：" name="password" :rules="[{required: true, message: '密码必须设置'}]">
        <a-input-password v-model:value="signupForm.password"/>
      </a-form-item>
      <a-form-item label="手机号码：" name="tel" :rules="[{required: false}]">
        <a-input v-model:value="signupForm.telephone"/>
      </a-form-item>
      <a-form-item :wrapper-col="{ span: 14, offset: 4 }">
        <a-button type="primary" html-type="submit">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<style scoped>
.sign_up {
  border-radius: 2rem;
  padding: 3rem;
  background-color: white;
}
</style>
