<script setup lang="ts">
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
const userPinia = useUserPinia();

const {userArchive} = storeToRefs(userPinia);
</script>

<template>
  <div class="edit_archive_box">
    <div class="title">
      <h2>档案信息</h2>
      <span>存储着您对账号的自定义信息</span>
    </div>
    <el-form v-if="userArchive" :model="userArchive" label-width="auto">
      <el-form-item label="昵称：" required>
        <el-input v-model="userArchive.nickname"/>
      </el-form-item>
      <el-form-item label="性别：" required>
        <el-radio-group v-model="userArchive.gender">
          <el-radio :value="0">男</el-radio>
          <el-radio :value="1">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="生日：" required>
        <el-date-picker
          v-model="userArchive.birthday"
          type="date"
          placeholder="选择日期"
          :size="'default'"
        />
      </el-form-item>
      <el-form-item label="QQ号：">
        <el-input placeholder="QQ号，仅用于社交展示与头像设置"/>
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
