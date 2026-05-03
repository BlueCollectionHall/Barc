import axios, { type AxiosRequestConfig } from 'axios'

import { DEFAULT_API_BASE_URL, SESSION_TOKEN_KEY } from '@/shared/constants/auth'
import { ApiError, AuthExpiredError, isApiEnvelope } from '@/shared/types/api'

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL,
  timeout: 30000,
})

function readToken(): string | null {
  return window.localStorage.getItem(SESSION_TOKEN_KEY)
}

function clearToken(): void {
  window.localStorage.removeItem(SESSION_TOKEN_KEY)
  window.dispatchEvent(new CustomEvent('barc:auth-changed'))
}

function isUnauthorizedPayload(payload: unknown): boolean {
  return typeof payload === 'string' && payload.toLowerCase().includes('401')
}

httpClient.interceptors.request.use((config) => {
  const token = readToken()
  if (token) {
    config.headers = config.headers ?? {}
    if (!('Authorization' in config.headers)) {
      config.headers.Authorization = token
    }
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => {
    const payload = response.data

    if (isApiEnvelope(payload)) {
      if (payload.code === 0) {
        return payload.data
      }

      throw new ApiError(String(payload.msg || payload.data || '请求失败。'), {
        code: payload.code,
        payload: payload.data,
      })
    }

    if (response.status === 401 || isUnauthorizedPayload(payload)) {
      clearToken()
      throw new AuthExpiredError()
    }

    if (typeof payload === 'string') {
      throw new ApiError(payload)
    }

    return payload
  },
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      const data = error.response?.data

      if (status === 401 || isUnauthorizedPayload(data)) {
        clearToken()
        throw new AuthExpiredError()
      }

      if (isApiEnvelope(data)) {
        throw new ApiError(String(data.msg || data.data || '请求失败。'), {
          code: data.code,
          payload: data.data,
        })
      }

      if (typeof data === 'string' && data.trim().length > 0) {
        throw new ApiError(data)
      }

      if (error.message) {
        throw new ApiError(error.message)
      }
    }

    throw error
  },
)

export const http = {
  request<T>(config: AxiosRequestConfig): Promise<T> {
    return httpClient.request<unknown, T>(config)
  },
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return httpClient.get<unknown, T>(url, config)
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return httpClient.post<unknown, T>(url, data, config)
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return httpClient.put<unknown, T>(url, data, config)
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return httpClient.delete<unknown, T>(url, config)
  },
}
