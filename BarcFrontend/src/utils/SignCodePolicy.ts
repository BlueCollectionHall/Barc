/**
 * 注册验证码按钮的展示与可用性策略。
 *
 * 背景：原实现里倒计时用 setInterval 无限循环（到负数就重置回 60），
 * 按钮也从不 disabled，导致「60s 内可重复点击」只是文案假象，
 * 同一邮箱会被反复索取验证码，进而出现「新 unique_id 搭配旧邮件验证码」的错配。
 *
 * 这里把「什么状态、按钮显示什么、是否可点」抽成纯函数：
 * 1、组件内只用它们渲染，逻辑可独立测试，不需要 DOM 环境；
 * 2、冷却期与请求中一律锁定，避免并发发码。
 */

/** 验证码重新获取的冷却秒数 */
export const COUNT_DOWN_SECONDS = 60;

/**
 * 计算按钮文案。
 *
 * @param countDown 剩余冷却秒数（<=0 表示不在冷却期）
 * @param isSending 发码请求是否进行中
 * @returns 按钮展示文案
 */
export const codeButtonText = (countDown: number, isSending: boolean): string => {
  if (isSending) return "发送中...";
  if (countDown > 0) return `${countDown}s后再试`;
  return "获取验证码";
};

/**
 * 判断按钮是否应被锁定（锁定后不可点击）。
 *
 * @param countDown 剩余冷却秒数
 * @param isSending 发码请求是否进行中
 * @returns 请求中或冷却中都返回 true
 */
export const isCodeLocked = (countDown: number, isSending: boolean): boolean => {
  return isSending || countDown > 0;
};

/**
 * 推进一次冷却计时。
 *
 * @param countDown 当前剩余秒数
 * @returns 下一个剩余秒数（最小为 0），返回 0 表示应停止计时
 */
export const nextCountDown = (countDown: number): number => {
  if (countDown <= 1) return 0;
  return countDown - 1;
};
