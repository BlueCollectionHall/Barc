// @vitest-environment jsdom
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia, disposePinia, setActivePinia, type Pinia } from 'pinia'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { baseHttp, slowHttp } from '../src/utils/https'
import { useUserPinia } from '../src/stores/UserPinia'
import * as comments from '../src/utils/commentApi'
import type { FeedBackImpl } from '../src/interfaces/FeedbackImpl'

/** 使用 Axios 的真实序列化和拦截器，只把传输适配器替换为内存响应，保证测试完全不联网。 */
let pinia: Pinia
const transport = vi.fn()
const ok = (data: unknown, config: InternalAxiosRequestConfig) => ({ status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, msg: '成功', data } })
beforeEach(() => {
  transport.mockReset(); localStorage.clear(); pinia = createPinia(); setActivePinia(pinia)
  transport.mockImplementation(async (config: InternalAxiosRequestConfig) => ok(config.url?.includes('basic_me') ? { uuid: 'u', email: 'u@example.test' } : { uuid: 'u', nickname: '用户' }, config))
  baseHttp.defaults.adapter = transport; slowHttp.defaults.adapter = transport
})
afterEach(() => disposePinia(pinia))
it('两个客户端规范化 Token 前缀并保留显式身份，游客请求不自动伪造身份', async () => {
  await baseHttp.get('/read'); expect(transport.mock.lastCall![0].headers.Authorization).toBeUndefined()
  localStorage.setItem('token', 'raw'); await baseHttp.get('/read'); expect(transport.mock.lastCall![0].headers.Authorization).toBe('Token raw')
  await slowHttp.post('/write', {}, { headers: { Authorization: 'Token explicit' } }); expect(transport.mock.lastCall![0].headers.Authorization).toBe('Token explicit')
  await baseHttp.get('/read', { headers: { Authorization: 'other' } }); expect(transport.mock.lastCall![0].headers.Authorization).toBe('Token other')
})
it('用户资料读取两个脱敏入口，401 立即清空存储和页面状态', async () => {
  localStorage.setItem('token', 'raw'); const store = useUserPinia(); await store.fetchUserInfo('raw'); expect(store.userArchive?.nickname).toBe('用户'); expect(store.userBasic?.email).toBe('u@example.test')
  transport.mockImplementationOnce(async (config: InternalAxiosRequestConfig) => { throw new AxiosError('expired', 'ERR_BAD_REQUEST', config, undefined, { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} }) })
  await expect(slowHttp.get('/authenticated')).rejects.toThrow('expired'); expect(localStorage.getItem('token')).toBeNull(); expect(store.userBasic).toBeNull(); expect(store.userArchive).toBeNull()
})
it('资料获取网络失败也清理旧会话，普通接口失败不注销已登录用户', async () => {
  localStorage.setItem('token', 'raw'); const store = useUserPinia(); await store.fetchUserInfo('raw')
  transport.mockRejectedValueOnce(new Error('offline')); await expect(baseHttp.get('/public')).rejects.toThrow('offline'); expect(store.userBasic).not.toBeNull()
  transport.mockRejectedValueOnce(new Error('offline')); await store.fetchUserInfo('raw'); expect(store.userBasic).toBeNull(); expect(localStorage.getItem('token')).toBeNull()
})
it('评论列表、主评分页和回复分页使用各自契约，资料缺失显示兜底', async () => {
  transport.mockImplementation(async (config: InternalAxiosRequestConfig) => ok(config.url?.includes('current_by_uuid') ? {} : { list: [], total: 0 }, config))
  await comments.fetchCommentsByWork('w'); expect(transport.mock.lastCall![0]).toMatchObject({ url: '/comment/work/comments_by_work', params: { work_id: 'w' } })
  await comments.fetchCommentsPage('w', 2); expect(transport.mock.lastCall![0]).toMatchObject({ url: '/comment/work/page', params: { work_id: 'w', page: 2, size: 10 } })
  await comments.fetchRepliesPage('c', 3); expect(transport.mock.lastCall![0]).toMatchObject({ url: '/comment/work/replies/page', params: { parent_id: 'c', page: 3, size: 5 } })
  await expect(comments.fetchUserArchive('u')).resolves.toEqual({ nickname: '未知用户', avatar: null })
  transport.mockImplementationOnce(async (config: InternalAxiosRequestConfig) => ({ ...ok(null, config), data: { code: 1, msg: '无资料' } })); await expect(comments.fetchUserArchive('missing')).resolves.toBeNull()
})
it('评论与回复发布只在 JSON 传内容，删除使用真实目标 ID，反馈使用统一入口', async () => {
  await expect(comments.uploadComment('w', '正文', 'token')).resolves.toBe(true)
  expect(transport.mock.lastCall![0]).toMatchObject({ method: 'post', url: '/comment/work/upload_comment', data: JSON.stringify({ work_id: 'w', content: '正文' }) })
  await expect(comments.uploadReply('c', 'target', '回复', 'token')).resolves.toBe(true)
  expect(JSON.parse(transport.mock.lastCall![0].data)).toEqual({ parent_id: 'c', reply_user: 'target', content: '回复' })
  await comments.deleteComment('c', 'token'); expect(transport.mock.lastCall![0]).toMatchObject({ method: 'delete', url: '/comment/work/delete_comment', params: { comment_id: 'c' } })
  await comments.deleteReply('r', 'token'); expect(transport.mock.lastCall![0]).toMatchObject({ method: 'delete', url: '/comment/work/delete_reply', params: { reply_id: 'r' } })
  await comments.reportComment({ type: 'COMMENT', target_id: 'c', content: '原因' } as FeedBackImpl); expect(transport.mock.lastCall![0].url).toBe('/feedback/upload')
})
it.each([
  () => comments.fetchCommentsByWork('w'), () => comments.fetchCommentsPage('w'), () => comments.fetchRepliesPage('c', 1),
  () => comments.uploadComment('w', '内容', 't'), () => comments.uploadReply('c', 'u', '内容', 't'),
  () => comments.deleteComment('c', 't'), () => comments.deleteReply('r', 't'), () => comments.reportComment({} as FeedBackImpl),
])('评论接口业务失败向调用组件抛出原因，不伪装成功 %#', async operation => {
  transport.mockImplementation(async (config: InternalAxiosRequestConfig) => ({ ...ok(null, config), data: { code: 403, msg: '操作已被拒绝' } }))
  await expect(operation()).rejects.toThrow('操作已被拒绝')
})
