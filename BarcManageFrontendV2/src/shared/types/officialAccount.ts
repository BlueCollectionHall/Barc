/**
 * 官方业务指定账号（用户 → 官方账号）类型定义。
 *
 * 语义约定：
 * 1、business_key 是「业务」，display_name 是业务名，与绑定账号的昵称无关；
 * 2、bound=false 表示未配置（含「从未绑定」与「绑定账号已被删除」两种成因，由 dangling 区分）；
 * 3、未配置会让对应业务的写链路被后端阻断，因此页面必须强提示，不能隐藏。
 */

/** 列表项：一条业务 + 其绑定状态 */
export interface OfficialAccountItem {
  /** 业务键，提交绑定时原样回传 */
  business_key: string
  /** 业务名 */
  display_name: string
  /** 业务说明 */
  description: string
  /** 是否已绑定（绑定行存在且绑定账号仍然存在） */
  bound: boolean
  /** 绑定行存在但账号已被删除 */
  dangling: boolean
  user_uuid: string | null
  nickname: string | null
  username: string | null
  avatar: string | null
  remark: string | null
  updated_by_nickname: string | null
  /** yyyy-MM-dd HH:mm:ss；未绑定时为空串 */
  updated_at: string
}

/** 绑定请求体 */
export interface BindOfficialAccountPayload {
  business_key: string
  user_uuid: string
  remark?: string | null
}

/** 绑定结果：更换绑定时会带回被迁移的未认领作品数量 */
export interface BindOfficialAccountResult {
  business_key: string
  user_uuid: string
  /** 被替换掉的旧账号 UUID；首次绑定时为 null */
  replaced_uuid: string | null
  /** 本次被重新归属到新账号的未认领作品数 */
  migrated_works: number
}

/** 账号选择器候选项（对应后端 UserInfoVo 的子集） */
export interface OfficialAccountCandidate {
  uuid: string
  nickname: string | null
  username: string | null
  avatar: string | null
}
