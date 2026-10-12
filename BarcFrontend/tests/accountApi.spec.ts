import {beforeEach, describe, expect, it, vi} from 'vitest';
import {changeAccountEmail, login, registerAccount, resetAccountPassword, sendVerificationCode, updateAccountBasic} from '../src/utils/accountApi';

const mocks = vi.hoisted(() => ({post: vi.fn(), slowPost: vi.fn()}));
vi.mock('@/utils/https.ts', () => ({baseHttp: {post: mocks.post}, slowHttp: {post: mocks.slowPost}}));

/** 验证双向认证契约：敏感参数仅在 JSON 中，业务失败不被当作成功。 */
describe('账号安全 API', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.post.mockResolvedValue({data: {code: 0, data: 'ok'}}); mocks.slowPost.mockResolvedValue({data: {code: 0, data: 'id'}}); });
  it('本地与 Naigos 登录均使用 POST 请求体', async () => {
    const body = {type: 'username', account: 'owner', password: 'secret'};
    await expect(login(body)).resolves.toBe('ok'); expect(mocks.post).toHaveBeenCalledWith('/user/sign/in', body);
    await login(body, true); expect(mocks.post).toHaveBeenCalledWith('/user/sign/in_by_naigos', body);
  });
  it('验证码、注册和改密不使用查询参数', async () => {
    await expect(sendVerificationCode('signup', 'a@example.com')).resolves.toBe('id');
    expect(mocks.slowPost).toHaveBeenCalledWith('/email/send/signup', {email: 'a@example.com', current_password: undefined});
    const body = {username: 'name', email: 'a@example.com', password: 'secret123', unique_id: 'id', code: '123456'};
    await registerAccount(body); expect(mocks.slowPost).toHaveBeenCalledWith('/user/sign/up', body);
    await resetAccountPassword(body); expect(mocks.post).toHaveBeenCalledWith('/user/reset_password', body);
  });
  it('改绑与改用户名提交当前口令', async () => {
    const body = {email: 'new@example.com', current_password: 'current', unique_id: 'id', code: '123456'};
    await sendVerificationCode('change_email', body.email, body.current_password);
    expect(mocks.slowPost).toHaveBeenCalledWith('/email/send/change_email', {email: body.email, current_password: 'current'});
    await changeAccountEmail(body); expect(mocks.post).toHaveBeenCalledWith('/user/change_email', body);
    const basic = {uuid: 'user', username: 'name', email: body.email, current_password: 'current'};
    await updateAccountBasic(basic); expect(mocks.post).toHaveBeenCalledWith('/user/edit_basic', basic);
  });
  it('保留业务错误而不返回成功', async () => {
    mocks.post.mockResolvedValue({data: {code: 1, data: '验证码已失效'}});
    await expect(login({type: 'username', account: 'owner', password: 'x'})).rejects.toThrow('验证码已失效');
    mocks.post.mockResolvedValue({data: {code: 1, msg: '拒绝访问'}});
    await expect(login({type: 'username', account: 'owner', password: 'x'})).rejects.toThrow('拒绝访问');
  });
});
