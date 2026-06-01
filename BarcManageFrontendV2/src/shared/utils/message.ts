import { ElMessage } from 'element-plus'

export function showSuccess(message: string): void {
  ElMessage({ type: 'success', message })
}

export function showInfo(message: string): void {
  ElMessage({ type: 'info', message })
}

export function showWarning(message: string): void {
  ElMessage({ type: 'warning', message })
}

export function showError(message: string): void {
  ElMessage({ type: 'error', message })
}
