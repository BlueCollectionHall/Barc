import {message} from "ant-design-vue";

export const successMessage = (content: string | null | undefined) => {
  message.success(content);
}
export const infoMessage = (content: string | null | undefined) => {
  message.info(content);
}
export const errorMessage = (content: string | null | undefined) => {
  message.error(content);
}
export const errorCatchMessage = () => {
  message.error("发生严重网络错误！");
}
