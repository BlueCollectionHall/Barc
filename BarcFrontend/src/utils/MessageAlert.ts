import {message} from "ant-design-vue";

/** 后端错误数据不保证是文字，提示组件只接收明确的字符串。 */
const messageText = (content: unknown): string => typeof content === "string" ? content : "操作未完成，请稍后重试";

export const successMessage = (content: unknown) => {
  message.success(messageText(content));
}
export const infoMessage = (content: unknown) => {
  message.info(messageText(content));
}
export const errorMessage = (content: unknown) => {
  message.error(messageText(content));
}
export const errorCatchMessage = () => {
  message.error("发生严重网络错误！");
}
