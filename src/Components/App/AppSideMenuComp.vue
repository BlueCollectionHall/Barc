<script setup lang="ts">
import {ManagerPermission} from "@/Utils/ManagerPermission.ts";
import {useUserStore} from "@/Stores/UserStore.ts";
import {storeToRefs} from "pinia";

const userStore = useUserStore();

const {userArchive} = storeToRefs(userStore);
</script>

<template>
  <el-aside width="20rem" style="background-color: #545c6490; border-right: #fff 1px solid">
    <el-menu
        style="min-height: calc(100vh - 90px); overflow: auto; border-right: none"
        active-text-color="#ffd04b"
        background-color="#545c6400"
        class="el-menu-vertical-demo"
        text-color="#fff"
        v-if="userArchive && userArchive.identity === 'MANAGER'"
        router
    >
      <el-sub-menu index="User" v-if="userArchive && userArchive.identity === 'MANAGER'">
        <template #title>
          <span>用户管理</span>
        </template>
        <!-- 不低于副馆长权限-->
        <el-menu-item-group title="管理员、用户" v-if="ManagerPermission.ADMINISTRATOR <= userArchive.permission">
          <el-menu-item index="UserPermission" :route="{name: 'UserPermission'}">裁撤、更换权限</el-menu-item>
        </el-menu-item-group>
        <!-- 拥有三级管理员权限-->
        <el-menu-item-group title="用户" v-if="(userArchive.permission & ManagerPermission.THI_MAINTAINER) === ManagerPermission.THI_MAINTAINER">
          <el-menu-item index="UserManage" :route="{name: 'UserManage'}">冻结、管理用户</el-menu-item>
        </el-menu-item-group>
        <!-- 拥有风纪委员权限-->
        <el-menu-item
            index="UserWarn"
            v-if="(userArchive.permission & ManagerPermission.DISCIPLINARY_COMMITTEE) === ManagerPermission.DISCIPLINARY_COMMITTEE"
            :route="{name: 'UserWarn'}">
          向用户、管理员发出警告
        </el-menu-item>
        <!-- 拥有三级管理员权限-->
        <el-menu-item
            index="UserFeedback"
            v-if="(userArchive.permission & ManagerPermission.THI_MAINTAINER) === ManagerPermission.THI_MAINTAINER"
            :route="{name: 'UserFeedback'}">
          用户反馈单
        </el-menu-item>
      </el-sub-menu>

      <!-- 拥有二级管理员权限 或者 风纪委员权限-->
      <el-sub-menu index="Work" v-if="((userArchive.permission & ManagerPermission.SEC_MAINTAINER) === ManagerPermission.SEC_MAINTAINER) || ((userArchive.permission & ManagerPermission.DISCIPLINARY_COMMITTEE) === ManagerPermission.DISCIPLINARY_COMMITTEE)">
        <template #title>
          <span>作品管理</span>
        </template>
        <el-menu-item index="WorkClaim" :route="{name: 'WorkClaim'}">认领、原著</el-menu-item>
        <!-- 拥有二级管理员权限-->
        <el-menu-item
            index="WorkBan"
            v-if="(userArchive.permission & ManagerPermission.SEC_MAINTAINER) === ManagerPermission.SEC_MAINTAINER"
            :route="{name: 'WorkBan'}">
          下架、封禁
        </el-menu-item>
        <el-menu-item
            index="WorkInspection"
            v-if="(userArchive.permission & ManagerPermission.SEC_MAINTAINER) === ManagerPermission.SEC_MAINTAINER"
            :route="{name: 'WorkInspection'}">
          人工巡检
        </el-menu-item>
        <!-- 风纪委员/二级管理员都能浏览，但风纪委员仅能处理职责内反馈单-->
        <el-menu-item index="WorkFeedback" :route="{name: 'WorkFeedback'}">作品反馈单</el-menu-item>
      </el-sub-menu>
      <!-- 拥有一级管理员权限-->
      <el-sub-menu index="Message" v-if="(userArchive.permission & ManagerPermission.FIR_MAINTAINER) === ManagerPermission.FIR_MAINTAINER">
        <template #title>
          <span>评论、留言管理</span>
        </template>
        <el-menu-item index="MessageNonCompliant" :route="{name: 'MessageNonCompliant'}">敏感、非法、违禁、色情</el-menu-item>
        <el-menu-item index="MessageInspection" :route="{name: 'MessageInspection'}">人工巡检</el-menu-item>
        <el-menu-item index="MessageFeedback" :route="{name: 'MessageFeedback'}">评论、留言反馈单</el-menu-item>
      </el-sub-menu>
      <!-- 任何管理员都能浏览-->
      <el-menu-item index="AutoWarn">自动预警</el-menu-item>
      <el-menu-item index="Notice" :route="{name: 'Notice'}">公告通知</el-menu-item>
      <el-menu-item index="GeneralFeedback" :route="{name: 'GeneralFeedback'}">通用反馈单</el-menu-item>
    </el-menu>
  </el-aside>
</template>

<style scoped>

</style>