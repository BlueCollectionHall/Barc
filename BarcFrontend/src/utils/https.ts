import axios from 'axios';

/** 默认 API 地址（兜底，防止 .env 缺失时崩溃） */
const DEFAULT_API_BASE_URL = 'https://api.barc.work';

/**
 * 常规请求超时（毫秒）。
 *
 * 说明：保持 10 秒不变，避免影响既有接口的失败反馈速度。
 */
const DEFAULT_TIMEOUT = 10000;

/**
 * 慢接口超时（毫秒）。
 *
 * 背景：注册验证码、重置密码验证码这类接口在后端涉及邮件发送，
 * 同步 SMTP 实测单封耗时约 12~13 秒，用 10 秒超时会导致请求被前端主动中断。
 * 注册链路更严重：响应里携带的 unique_id 是后续注册的必需参数，
 * 一旦超时，前端只会在 catch 里静默失败，用户虽然收到了邮件验证码，
 * 但注册时必然报「验证码会话不存在」，表现为「注册功能联通不上」。
 *
 * 因此这类接口单独放宽到 60 秒：
 * 1、后端已改为「落库即返回 + 邮件异步发送」，正常情况下毫秒级返回；
 * 2、60 秒仅作为兜底，防止后端退化为同步发信时再次被前端掐断。
 */
export const SLOW_TIMEOUT = 60000;

export const baseHttp = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL,
  timeout: DEFAULT_TIMEOUT,
});

/**
 * 慢接口专用实例。
 *
 * 用于发送邮箱验证码、提交注册等依赖邮件链路的请求，超时放宽到 {@link SLOW_TIMEOUT}。
 * 与 baseHttp 共用同一 baseURL 来源，避免两处配置漂移。
 *
 * @example
 * await slowHttp("/email/send/signup", {method: "GET", params: {email}});
 */
export const slowHttp = axios.create({
  baseURL: baseHttp.defaults.baseURL,
  timeout: SLOW_TIMEOUT,
});
