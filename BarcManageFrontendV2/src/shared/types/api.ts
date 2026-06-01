export interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

export interface PageRequest<TParams = Record<string, unknown>> {
  page_num: number
  page_size: number
  sort_field?: string
  sort_order?: string
  params?: TParams
}

export interface PageResult<T> {
  total: number
  list: T[]
  page_num: number
  page_size: number
  total_page: number
}

export interface ValueLabel<T extends string | number = string | number> {
  value: T
  label: string
}

export class ApiError extends Error {
  code?: number
  payload?: unknown

  constructor(message: string, options?: { code?: number; payload?: unknown }) {
    super(message)
    this.name = 'ApiError'
    this.code = options?.code
    this.payload = options?.payload
  }
}

export class AuthExpiredError extends ApiError {
  constructor(message = '登录状态已失效，请重新登录。') {
    super(message)
    this.name = 'AuthExpiredError'
  }
}

export class ManagerOnlyError extends ApiError {
  constructor(message = '当前账号不是管理员，无法进入管理后台。') {
    super(message)
    this.name = 'ManagerOnlyError'
  }
}

export function isApiEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  if (!value || typeof value !== 'object') {
    return false
  }

  const target = value as Record<string, unknown>
  return typeof target.code === 'number' && 'msg' in target && 'data' in target
}

export function getErrorMessage(error: unknown, fallback = '请求失败，请稍后重试。'): string {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}
