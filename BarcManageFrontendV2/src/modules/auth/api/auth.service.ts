import { http } from '@/shared/api/http'
import type { BarcLoginType, NaigosLoginType, UserArchive, UserBasic } from '@/shared/types/user'

interface SignInPayload<TType extends string> {
  type: TType
  account: string
  password: string
}

export function signIn(payload: SignInPayload<BarcLoginType>): Promise<string> {
  return http.get<string>('/user/sign/in', {
    params: payload,
  })
}

export function signInByNaigos(payload: SignInPayload<NaigosLoginType>): Promise<string> {
  return http.get<string>('/user/sign/in_by_naigos', {
    params: payload,
  })
}

export function fetchCurrentMe(): Promise<UserArchive> {
  return http.get<UserArchive>('/user/current_me')
}

export function fetchBasicMe(): Promise<UserBasic> {
  return http.get<UserBasic>('/user/basic_me')
}
