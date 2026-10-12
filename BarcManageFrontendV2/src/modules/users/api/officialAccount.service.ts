import { http } from '@/shared/api/http'
import type {
  BindOfficialAccountPayload,
  BindOfficialAccountResult,
  OfficialAccountCandidate,
  OfficialAccountItem,
} from '@/shared/types/officialAccount'

/**
 * 官方业务指定账号（用户 → 官方账号）。
 *
 * 接口约定（后端 com.miaoyu.barc.user.controller.OfficialAccountController，馆长 1匹配鉴权）：
 * 1、GET  /user/official/list        全部业务项与绑定状态（含未绑定项）
 * 2、POST /user/official/bind        绑定 / 更换；更换时后端在同一事务内迁移未认领作品
 * 3、GET  /user/query/search 账号选择器搜索
 *
 * 注意：没有 unbind 接口 —— 绑定只能更换，不能解绑。
 */

/** 管理端：全部官方业务项与绑定状态 */
export function fetchOfficialAccounts(): Promise<OfficialAccountItem[]> {
  return http.get<OfficialAccountItem[]>('/user/official/list')
}

/** 管理端：绑定 / 更换某业务的官方账号 */
export function bindOfficialAccount(
  payload: BindOfficialAccountPayload,
): Promise<BindOfficialAccountResult> {
  return http.post<BindOfficialAccountResult>('/user/official/bind', payload)
}

/** 管理端：账号选择器搜索（UUID / 用户名 / 昵称） */
export function searchOfficialAccountCandidates(
  keyword: string,
): Promise<OfficialAccountCandidate[]> {
  return http.get<OfficialAccountCandidate[]>('/user/query/search', {
    params: { keyword },
  })
}
