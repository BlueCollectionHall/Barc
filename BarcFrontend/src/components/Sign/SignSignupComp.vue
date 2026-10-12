<script setup lang="ts">
/**
 * 注册表单组件
 *
 * 功能：
 * 1、收集注册信息并做基础校验；
 * 2、获取邮箱验证码（后端返回的 unique_id 是注册的必需参数）；
 * 3、提交注册并反馈结果。
 *
 * 关键约定（注册链路修复）：
 * 1、发码与注册请求使用慢接口实例 slowHttp（60 秒超时），
 *    避免邮件接口耗时超过 10 秒时被前端主动中断，导致 unique_id 拿不到；
 * 2、unique_id 为空时不允许提交，直接给出「请先获取验证码」的明确提示，
 *    而不是把空参数发给后端换来一句「资源获取失败」；
 * 3、倒计时使用可清理的定时器并禁用按钮，禁止重复索取验证码。
 */
import type {UserBasicImpl} from "@/interfaces/UserImpl.ts";
import {computed, onUnmounted, ref, watch} from "vue";
import {slowHttp} from "@/utils/https.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {errorMessage, successMessage} from "@/utils/MessageAlert.ts";
// 以命名空间引入，避免与组件内同名 computed 冲突（模板仍需 isCodeLocked/codeButtonText 这两个名字）
import * as SignCodePolicy from "@/utils/SignCodePolicy.ts";

interface SignupImpl extends UserBasicImpl{
  email: string;
  unique_id: string;
  code: string;
}

/**
 * 验证码会话的会话级存储键。
 *
 * 背景：unique_id 只存在组件内存里时，任何一次组件重建（页面刷新、
 * dev server 重启触发整页 reload、在登录/注册页签之间切换）都会把它清空，
 * 而用户依然能从邮件里手填验证码，结果是必然失败且原因难以自查。
 * 因此把「unique_id + 邮箱」落到 sessionStorage，组件重建后自动恢复。
 */
const SIGNUP_SESSION_KEY = "barc_signup_code_session";

/** 会话级验证码上下文 */
interface SignupCodeSession {
  unique_id: string;
  email: string;
}

/** 读取会话级验证码上下文；解析失败时按不存在处理 */
const readCodeSession = (): SignupCodeSession | null => {
  try {
    const raw = window.sessionStorage.getItem(SIGNUP_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SignupCodeSession;
    if (!parsed || typeof parsed.unique_id !== "string" || typeof parsed.email !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
};

/** 写入会话级验证码上下文 */
const writeCodeSession = (session: SignupCodeSession) => {
  try {
    window.sessionStorage.setItem(SIGNUP_SESSION_KEY, JSON.stringify(session));
  } catch {
    // 隐私模式等场景下 sessionStorage 可能不可用，退化为仅内存保存，不影响主流程
  }
};

/** 清除会话级验证码上下文（注册成功后必须清除，避免复用已使用的验证码） */
const clearCodeSession = () => {
  try {
    window.sessionStorage.removeItem(SIGNUP_SESSION_KEY);
  } catch {
    // 同上，清理失败不阻塞主流程
  }
};

// 在注册邮箱监听器之前恢复初始值，避免挂载回填邮箱被误判为用户改邮箱而作废验证码。
const restoredCodeSession = readCodeSession();
const signupForm = ref<SignupImpl>({
  email: restoredCodeSession?.email || "",
  unique_id: restoredCodeSession?.unique_id || "",
  code: "",
  uuid: "", username: "", password: "", password_version: 0, email_verified: false, telephone: null, safe_level: 100, created_at: new Date(), updated_at: new Date()
});

const countDown = ref<number>(0);
/** 发码请求进行中标记，用于按钮 loading 与防重复点击 */
const isCodeSending = ref<boolean>(false);
/** 注册请求进行中标记，用于提交按钮 loading */
const isSubmitting = ref<boolean>(false);
/** 倒计时定时器句柄，组件卸载与重新发码时必须清理 */
const countDownTimer = ref<ReturnType<typeof setInterval> | null>(null);

/**
 * 邮箱发生变化时作废上一次的 unique_id。
 *
 * 原因：unique_id 与邮箱是绑定的，改邮箱后沿用旧 unique_id 必然校验失败，
 * 这里主动清空可以让用户看到「请先获取验证码」，而不是提交后才报错。
 */
watch(() => signupForm.value.email, (newEmail, oldEmail) => {
  if (oldEmail !== undefined && newEmail !== oldEmail && signupForm.value.unique_id !== "") {
    signupForm.value.unique_id = "";
    signupForm.value.code = "";
    clearCodeSession();
  }
});

/** 邮箱是否处于验证码冷却期（冷却中或请求中都不允许再次点击） */

/** 验证码按钮是否锁定：请求中或冷却中都不允许点击（策略见 SignCodePolicy） */
const isCodeLocked = computed<boolean>(() => SignCodePolicy.isCodeLocked(countDown.value, isCodeSending.value));

/** 获取验证码按钮文案（策略见 SignCodePolicy） */
const codeButtonText = computed<string>(() => SignCodePolicy.codeButtonText(countDown.value, isCodeSending.value));

/** 停止并清理倒计时定时器，防止定时器泄漏与重复计时 */
const stopCountDown = () => {
  if (countDownTimer.value !== null) {
    clearInterval(countDownTimer.value);
    countDownTimer.value = null;
  }
};

/** 启动验证码冷却倒计时 */
const startCountDown = () => {
  stopCountDown();
  countDown.value = SignCodePolicy.COUNT_DOWN_SECONDS;
  countDownTimer.value = setInterval(() => {
    countDown.value = SignCodePolicy.nextCountDown(countDown.value);
    // 倒计时归零后立即停止定时器，避免定时器无限运行
    if (countDown.value === 0) {
      stopCountDown();
    }
  }, 1000);
};

// 组件卸载时必须清理定时器，否则会持续在后台递减
onUnmounted(stopCountDown);

/**
 * 获取邮箱验证码。
 *
 * 处理逻辑：
 * 1、校验邮箱非空；
 * 2、请求发码接口并保存返回的 unique_id（注册时必需）；
 * 3、成功后启动 60 秒冷却，失败时给出明确提示。
 */
const getCode = async () => {
  if (signupForm.value.email === "") {
    errorMessage("邮箱不得为空");
    return;
  }
  if (isCodeLocked.value) {
    return;
  }
  isCodeSending.value = true;
  try {
    // 发码是写操作，邮箱与验证码相关参数统一放在 JSON 请求体。
    const response = await slowHttp.post("/email/send/signup", {email: signupForm.value.email});
    const data: ResponseImpl<string> = response.data;
    if (data.code === 0) {
      // unique_id 是后续注册的必需参数，必须落进表单状态并持久化到会话，抗页面重载
      signupForm.value.unique_id = data.data;
      writeCodeSession({unique_id: data.data, email: signupForm.value.email});
      successMessage("验证码已发送，请查收邮件");
      startCountDown();
    } else {
      // 后端明确返回失败时要把原因透出，不能静默吞掉
      errorMessage(data.data || data.msg || "验证码发送失败，请重试");
    }
  } catch {
    // 超时、断网等异常都必须反馈给用户，否则会表现为「点了没反应」
    errorMessage("验证码发送失败，请检查网络后重试");
  } finally {
    isCodeSending.value = false;
  }
}

/**
 * 提交注册。
 *
 * 处理逻辑：
 * 1、unique_id 为空说明没有成功获取验证码，直接提示并终止，不发无意义的请求；
 * 2、调用注册接口并展示后端返回结果。
 */
const signup = async () => {
  if (signupForm.value.unique_id === "") {
    errorMessage("请先获取邮箱验证码");
    return;
  }
  isSubmitting.value = true;
  try {
    const response = await slowHttp.post("/user/sign/up", signupForm.value);
    const data: ResponseImpl<string> = response.data;
    if (data.code === 0) {
      // 注册成功后必须清除会话中的 unique_id，避免复用一个已使用的验证码
      clearCodeSession();
      signupForm.value.unique_id = "";
      successMessage(data.msg);
    } else {
      // 后端失败原因直接呈现给用户（如「验证码不匹配」「验证码会话不存在或已过期」）
      errorMessage(data.data || data.msg);
    }
  } catch {
    errorMessage("注册失败，请检查网络后重试");
  } finally {
    isSubmitting.value = false;
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
        <a-button :disabled="isCodeLocked" :loading="isCodeSending" @click="getCode">{{codeButtonText}}</a-button>
      </a-form-item>
      <a-form-item label="密码：" name="password" :rules="[{required: true, message: '密码必须设置'}]">
        <a-input-password v-model:value="signupForm.password"/>
      </a-form-item>
      <a-form-item label="手机号码：" name="tel" :rules="[{required: false}]">
        <a-input v-model:value="signupForm.telephone"/>
      </a-form-item>
      <a-form-item :wrapper-col="{ span: 14, offset: 4 }">
        <a-button type="primary" html-type="submit" :loading="isSubmitting">注册</a-button>
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
