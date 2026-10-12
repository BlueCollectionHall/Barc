<script setup lang="ts">
/** 改密使用当前绑定邮箱验证，成功后清理旧会话并重新登录。 */
import {onUnmounted, ref} from 'vue';
import {storeToRefs} from 'pinia';
import {useRouter} from 'vue-router';
import {useUserPinia} from '@/stores/UserPinia.ts';
import {errorMessage, successMessage} from '@/utils/MessageAlert.ts';
import {resetAccountPassword, sendVerificationCode} from '@/utils/accountApi.ts';

const userPinia = useUserPinia();
const {userBasic} = storeToRefs(userPinia);
const router = useRouter();
const form = ref({unique_id: '', password: '', code: '', email: ''});
const countdown = ref(0);
const busy = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
onUnmounted(() => { if (timer) clearInterval(timer); });

const fetchCode = async () => {
  if (!userBasic.value || countdown.value > 0 || busy.value) return;
  busy.value = true;
  try {
    form.value.email = userBasic.value.email;
    form.value.unique_id = await sendVerificationCode('reset_password', form.value.email);
    countdown.value = 60;
    timer = setInterval(() => { countdown.value--; if (countdown.value <= 0 && timer) clearInterval(timer); }, 1000);
    successMessage('验证码已发送至绑定邮箱');
  } catch (error) { errorMessage(error instanceof Error ? error.message : '验证码发送失败'); }
  finally { busy.value = false; }
};

const resetPassword = async () => {
  if (busy.value) return;
  if (!/^\d{6}$/.test(form.value.code) || !form.value.unique_id) { errorMessage('请先获取并输入六位验证码'); return; }
  if (form.value.password.length < 8 || form.value.password.length > 128) { errorMessage('密码长度须为 8 至 128 个字符'); return; }
  busy.value = true;
  try {
    await resetAccountPassword(form.value);
    userPinia.clearSession();
    successMessage('密码已修改，请重新登录');
    await router.replace({name: 'Sign'});
  } catch (error) { errorMessage(error instanceof Error ? error.message : '密码修改失败'); }
  finally { busy.value = false; }
};
</script>

<template>
  <div class="change">
    <div class="title"><h3>修改密码</h3><span>验证绑定邮箱后设置新密码，成功后所有设备需重新登录。</span></div>
    <el-form :model="form" label-width="auto" @submit.prevent="resetPassword">
      <el-form-item label="新密码："><el-input v-model="form.password" type="password" show-password :maxlength="128" autocomplete="new-password" /></el-form-item>
      <el-form-item label="验证码：">
        <div class="code-row"><el-input v-model="form.code" placeholder="六位验证码" :maxlength="6" autocomplete="one-time-code" />
          <el-button :disabled="countdown > 0 || busy" @click="fetchCode">{{countdown > 0 ? `${countdown} 秒后重试` : '获取验证码'}}</el-button>
        </div>
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="busy">确认修改</el-button>
    </el-form>
  </div>
</template>

<style scoped>
.title { margin-bottom: 2rem; }
.title span { font-size: .9rem; }
.change { margin-bottom: 4rem; }
.code-row { display: flex; gap: 1rem; width: 100%; }
</style>
