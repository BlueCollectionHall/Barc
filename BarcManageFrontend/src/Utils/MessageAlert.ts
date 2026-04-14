import {ElMessage} from "element-plus";

export const successMessage = (content: string | null | undefined) => {
  ElMessage({
    message: String(content),
    type: 'success',
  })
}
export const infoMessage = (content: string | null | undefined) => {
  ElMessage({
    message: String(content),
    type: 'info',
  })
}
export const warningMessage = (content: string | null | undefined) => {
  ElMessage({
    message: String(content),
    type: 'warning',
  })
}
export const errorMessage = (content: string | null | undefined) => {
  ElMessage({
    message: String(content),
    type: 'error',
  })
}
export const errorCatchMessage = () => {
  ElMessage({
    message: "网络错误！",
    type: 'error',
  })
}



