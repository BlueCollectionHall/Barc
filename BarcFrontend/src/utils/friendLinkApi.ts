import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import { baseHttp } from "@/utils/https.ts";

/**
 * 友情链接（公开接口，无需登录）
 *
 * 数据来源：管理端「系统 → 友情链接」，仅馆长可维护。
 * 后端只返回用户端渲染所需的四项，logo 已由后端签名成完整 URL（有效期 1 小时）。
 */
export interface FriendLinkItem {
  title: string;
  url: string;
  /** 简介，未填写时为 null */
  intro: string | null;
  /** 签名后的完整 logo URL，未设置 logo 时为 null */
  logo_url: string | null;
}

/**
 * 拉取全部友链（按管理端维护的顺序）。
 *
 * 注意：**这里刻意不做模块级缓存**。友链 logo 是签名 URL、有效期 1 小时，
 * 缓存到会话级反而会拿到过期地址；每次进首页重新拉取的代价可以忽略（数据量通常是个位数）。
 *
 * 失败时兜底为空数组，组件据此保持「只有标题、没有卡片」的降级形态，不影响首页其它区块。
 */
export async function fetchFriendLinks(): Promise<FriendLinkItem[]> {
  try {
    const response = await baseHttp.get("/api/friend-link/all");
    const data: ResponseImpl = response.data;
    if (data.code === 0 && Array.isArray(data.data)) {
      return data.data as FriendLinkItem[];
    }
  } catch {
    // 网络异常 / 未部署时静默降级，首页不显示报错
  }
  return [];
}
