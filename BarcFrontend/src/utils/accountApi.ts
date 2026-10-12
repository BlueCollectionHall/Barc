import { baseHttp, slowHttp } from '@/utils/https.ts';
import type { ResponseImpl } from '@/interfaces/ResponseImpl.ts';

export interface LoginPayload { type: string; account: string; password: string }
export interface SignupPayload { username: string; email: string; password: string; telephone?: string; unique_id: string; code: string }
export interface PasswordResetPayload { email: string; password: string; unique_id: string; code: string }
export interface EmailChangePayload { email: string; current_password: string; unique_id: string; code: string }
export interface BasicUpdatePayload { uuid: string; username: string; email: string; telephone?: string; current_password: string }

/** 认证写入仅使用 JSON；失败信息保留后端业务文案，禁止记录含凭据的 Axios 对象。 */
function unwrap<T>(response: { data: ResponseImpl }): T {
  if (response.data.code !== 0) throw new Error(String(response.data.data || response.data.msg || '操作失败'));
  return response.data.data as T;
}

export async function login(payload: LoginPayload, naigos = false): Promise<string> {
  return unwrap<string>(await baseHttp.post(naigos ? '/user/sign/in_by_naigos' : '/user/sign/in', payload));
}

export async function sendVerificationCode(purpose: 'signup' | 'reset_password' | 'change_email', email: string, currentPassword?: string): Promise<string> {
  return unwrap<string>(await slowHttp.post(`/email/send/${purpose}`, { email, current_password: currentPassword }));
}

export async function registerAccount(payload: SignupPayload): Promise<unknown> {
  return unwrap(await slowHttp.post('/user/sign/up', payload));
}

export async function resetAccountPassword(payload: PasswordResetPayload): Promise<void> {
  unwrap(await baseHttp.post('/user/reset_password', payload));
}

export async function changeAccountEmail(payload: EmailChangePayload): Promise<void> {
  unwrap(await baseHttp.post('/user/change_email', payload));
}

export async function updateAccountBasic(payload: BasicUpdatePayload): Promise<void> {
  unwrap(await baseHttp.post('/user/edit_basic', payload));
}
