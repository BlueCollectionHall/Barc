import {beforeEach, describe, expect, it, vi} from 'vitest'
import {signIn, signInByNaigos} from '../src/modules/auth/api/auth.service'

const mocks = vi.hoisted(() => ({post: vi.fn()}))
vi.mock('@/shared/api/http', () => ({http: {post: mocks.post}}))

/** 管理端与用户端同步迁移到 JSON 登录，防止某端仍把密码放入 URL。 */
describe('管理端登录请求', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.post.mockResolvedValue('token') })
  it('本地登录使用 POST', async () => {
    const body = {type: 'username' as const, account: 'manager', password: 'secret'}
    await expect(signIn(body)).resolves.toBe('token')
    expect(mocks.post).toHaveBeenCalledWith('/user/sign/in', body)
  })
  it('外部登录使用 POST', async () => {
    const body = {type: 'uid' as const, account: 'manager', password: 'secret'}
    await signInByNaigos(body)
    expect(mocks.post).toHaveBeenCalledWith('/user/sign/in_by_naigos', body)
  })
})
