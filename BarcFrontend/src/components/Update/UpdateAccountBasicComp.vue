<script setup lang="ts">
/** 基础资料与邮箱改绑分别提交；邮箱改绑验证当前口令和新邮箱，完成后撤销旧会话。 */
import {onUnmounted, reactive, ref, watch} from 'vue';
import {storeToRefs} from 'pinia';
import {useRouter} from 'vue-router';
import {useUserPinia} from '@/stores/UserPinia.ts';
import {changeAccountEmail, sendVerificationCode, updateAccountBasic} from '@/utils/accountApi.ts';
import {errorMessage, successMessage} from '@/utils/MessageAlert.ts';

const userPinia = useUserPinia();
const {userBasic} = storeToRefs(userPinia);
const router = useRouter();
const basic = reactive({username: '', telephone: '', current_password: ''});
const emailForm = reactive({email: '', current_password: '', unique_id: '', code: ''});
const busy = ref(false);
const countdown = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;
onUnmounted(() => { if (timer) clearInterval(timer); });
watch(userBasic, (user) => { if (user) { basic.username = user.username; basic.telephone = user.telephone || ''; } }, {immediate: true});
watch(() => emailForm.email, () => { emailForm.unique_id = ''; emailForm.code = ''; });

const saveBasic = async () => {
  if (!userBasic.value || busy.value) return;
  busy.value = true;
  try {
    await updateAccountBasic({uuid: userBasic.value.uuid, email: userBasic.value.email, ...basic});
    basic.current_password = '';
    await userPinia.fetchUserInfo(window.localStorage.getItem('token') || '');
    successMessage('基本信息已更新');
  } catch (error) { errorMessage(error instanceof Error ? error.message : '更新失败'); }
  finally { busy.value = false; }
};
const sendCode = async () => {
  if (busy.value || countdown.value > 0) return;
  busy.value = true;
  try {
    emailForm.unique_id = await sendVerificationCode('change_email', emailForm.email, emailForm.current_password);
    countdown.value = 60;
    timer = setInterval(() => { countdown.value--; if (countdown.value <= 0 && timer) clearInterval(timer); }, 1000);
    successMessage('验证码已发送至新邮箱');
  } catch (error) { errorMessage(error instanceof Error ? error.message : '发送失败'); }
  finally { busy.value = false; }
};
const saveEmail = async () => {
  if (busy.value) return;
  if (!emailForm.unique_id || !/^\d{6}$/.test(emailForm.code)) { errorMessage('请先获取并输入新邮箱验证码'); return; }
  busy.value = true;
  try {
    await changeAccountEmail(emailForm);
    userPinia.clearSession();
    successMessage('邮箱已更新，请重新登录');
    await router.replace({name: 'Sign'});
  } catch (error) { errorMessage(error instanceof Error ? error.message : '邮箱更新失败'); }
  finally { busy.value = false; }
};
</script>

<template>
  <div v-if="userBasic" class="basic_box">
    <div class="title"><h2>基本信息</h2><span>修改用户名时需确认当前密码。</span></div>
    <el-form :model="basic" label-width="auto" @submit.prevent="saveBasic">
      <el-form-item label="用户名：" required><el-input v-model="basic.username" :maxlength="50" /></el-form-item>
      <el-form-item label="电话："><el-input v-model="basic.telephone" :maxlength="20" /></el-form-item>
      <el-form-item label="当前密码："><el-input v-model="basic.current_password" type="password" autocomplete="current-password" /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy">保存基本信息</el-button>
    </el-form>
    <div class="title email-title"><h3>电子邮箱</h3><span>验证当前密码和新邮箱后完成改绑，所有设备需重新登录。</span></div>
    <el-form :model="emailForm" label-width="auto" @submit.prevent="saveEmail">
      <el-form-item label="原邮箱："><el-input :model-value="userBasic.email" disabled /></el-form-item>
      <el-form-item label="当前密码："><el-input v-model="emailForm.current_password" type="password" autocomplete="current-password" /></el-form-item>
      <el-form-item label="新邮箱："><el-input v-model="emailForm.email" type="email" :maxlength="255" /></el-form-item>
      <el-form-item label="新邮箱验证码：">
        <div class="code-row"><el-input v-model="emailForm.code" :maxlength="6" autocomplete="one-time-code" />
          <el-button :disabled="busy || countdown > 0" @click="sendCode">{{countdown > 0 ? `${countdown} 秒后重试` : '获取'}}</el-button>
        </div>
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy">确认修改邮箱</el-button>
    </el-form>
  </div>
</template>

<style scoped>
.title { margin-bottom: 2rem; }
.title span { font-size: .9rem; }
.email-title { margin-top: 2rem; }
.code-row { display: flex; gap: .5rem; }
</style>
