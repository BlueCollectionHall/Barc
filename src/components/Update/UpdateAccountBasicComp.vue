<script setup lang="ts">
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();

const {userBasic} = storeToRefs(userPinia);


</script>

<template>
  <div class="basic_box">
    <div class="title">
      <h2>基本信息</h2>
      <span>这里存储着您账号所有关键信息，涉及登录、业务以及如何找到您;</span>
    </div>
    <el-form v-if="userBasic" :model="userBasic" label-width="auto">
      <el-form-item label="用户名：" required>
        <el-input placeholder="用户名唯一性，用于查找到您" v-model="userBasic.username"/>
      </el-form-item>
      <el-form-item label="电话：">
        <el-input v-model="userBasic.telephone" placeholder="社交展示，非必须"/>
      </el-form-item>
    </el-form>
  </div>
  <div class="email">
    <div class="title">
      <h3>电子邮箱</h3>
      <span>是基本信息但不能轻易改，涉及到账号信息安全！</span>
    </div>
    <el-form v-if="userBasic" :model="userBasic" label-width="auto">
      <el-form-item label="原邮箱：">
        <el-input disabled v-model="userBasic.email"/>
      </el-form-item>
      <el-form-item label="原邮验证码：">
        <div style="display: flex; align-items: center;">
          <el-input placeholder="6位验证码"/>
          <el-button type="primary">获取</el-button>
        </div>
      </el-form-item>
      <el-form-item label="新邮箱">
        <el-input placeholder="请输入新的电子邮箱"/>
      </el-form-item>
      <el-form-item label="新邮验证码：">
        <div style="display: flex; align-items: center;">
          <el-input placeholder="6位验证码"/>
          <el-button type="primary">获取</el-button>
        </div>
      </el-form-item>
      <el-form-item>
        <el-button type="primary">确认修改</el-button>
      </el-form-item>
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
</style>
