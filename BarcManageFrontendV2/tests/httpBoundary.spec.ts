import { beforeEach, expect, it } from 'vitest'
import { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios'
import { http } from '../src/shared/api/http'
import { SESSION_TOKEN_KEY } from '../src/shared/constants/auth'
import { ApiError, AuthExpiredError } from '../src/shared/types/api'

/** 用 Axios 适配器验证真实拦截器，测试请求不会离开进程。 */
function adapter(data: unknown, status = 200): AxiosAdapter {
  return async config => ({ data, status, statusText: 'test', headers: {}, config })
}
function failing(data: unknown, status = 500, message = 'network'): AxiosAdapter {
  return async config => { throw new AxiosError(message, 'TEST', config, undefined, { data, status, config, headers: {}, statusText: 'test' } as AxiosResponse) }
}
beforeEach(() => localStorage.clear())
it('统一 Token 前缀，显式授权头优先，成功只解包业务 data', async () => {
  localStorage.setItem(SESSION_TOKEN_KEY, 'jwt')
  const echo: AxiosAdapter = async config => ({ data: { code: 0, msg: 'ok', data: config.headers.get('Authorization') }, status: 200, headers: {}, config, statusText: 'OK' })
  await expect(http.get('/test', { adapter: echo })).resolves.toBe('Token jwt')
  localStorage.setItem(SESSION_TOKEN_KEY, 'Token prefixed')
  await expect(http.post('/test', { value: 1 }, { adapter: echo })).resolves.toBe('Token prefixed')
  await expect(http.put('/test', {}, { headers: { Authorization: 'Token explicit' }, adapter: echo })).resolves.toBe('Token explicit')
  localStorage.clear(); await expect(http.delete('/test', { adapter: adapter({ code: 0, msg: 'ok', data: 7 }) })).resolves.toBe(7)
})
it('业务失败与非标准响应保留错误信息，不被成功解包', async () => {
  await expect(http.get('/test', { adapter: adapter({ code: 1, msg: '业务拒绝', data: null }) })).rejects.toThrow('业务拒绝')
  await expect(http.get('/test', { adapter: adapter({ code: 1, msg: '', data: '详细错误' }) })).rejects.toThrow('详细错误')
  await expect(http.get('/test', { adapter: adapter({ code: 1, msg: '', data: null }) })).rejects.toBeInstanceOf(ApiError)
  await expect(http.get('/test', { adapter: adapter('纯文本失败') })).rejects.toThrow('纯文本失败')
  await expect(http.request({ url: '/test', adapter: adapter({ legacy: true }) })).resolves.toEqual({ legacy: true })
})
it('401 与旧文本未授权响应均撤销本地会话并通知页面', async () => {
  let changed = 0; const listener = () => changed++; window.addEventListener('barc:auth-changed', listener)
  for (const transport of [failing({ code: 1, msg: '过期', data: null }, 401), adapter('401 Unauthorized')]) {
    localStorage.setItem(SESSION_TOKEN_KEY, 'old')
    await expect(http.get('/test', { adapter: transport })).rejects.toBeInstanceOf(AuthExpiredError)
    expect(localStorage.getItem(SESSION_TOKEN_KEY)).toBeNull()
  }
  expect(changed).toBe(2); window.removeEventListener('barc:auth-changed', listener)
})
it('HTTP 错误壳、文本、网络错误和普通异常保留各自原因', async () => {
  await expect(http.get('/test', { adapter: failing({ code: 1, msg: '禁止', data: null }, 403) })).rejects.toThrow('禁止')
  await expect(http.get('/test', { adapter: failing('上游错误') })).rejects.toThrow('上游错误')
  await expect(http.get('/test', { adapter: failing(null, 500, '连接失败') })).rejects.toThrow('连接失败')
  const original = new Error('adapter error'); await expect(http.get('/test', { adapter: async () => { throw original } })).rejects.toBe(original)
})
