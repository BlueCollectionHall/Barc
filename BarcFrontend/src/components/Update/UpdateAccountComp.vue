<script setup lang="ts">
/** 设置页只组合各独立表单；改密逻辑集中在密码组件，避免重复旧请求链路。 */
import {onMounted} from 'vue';
import {useRouter} from 'vue-router';
import {errorMessage} from '@/utils/MessageAlert.ts';
import UpdateAccountAvatarComp from '@/components/Update/UpdateAccountAvatarComp.vue';
import UpdateAccountBasicComp from '@/components/Update/UpdateAccountBasicComp.vue';
import UpdateAccountArchiveComp from '@/components/Update/UpdateAccountArchiveComp.vue';
import UpdateAccountPasswordComp from '@/components/Update/UpdateAccountPasswordComp.vue';
const router = useRouter();
onMounted(() => { if (!window.localStorage.getItem('token')) { errorMessage('请先登录'); router.replace({name: 'Sign'}); } });
</script>

<template>
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
          <p>删除账号：账号将被硬删除，无法找回，名下所有作品将划归官方账号名下；</p>
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
