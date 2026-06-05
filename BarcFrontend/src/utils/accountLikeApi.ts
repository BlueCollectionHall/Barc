import { baseHttp } from "@/utils/https.ts";
import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import type { PageRequestImpl, PageResultImpl } from "@/interfaces/PageImpl.ts";
import type { WorkImpl } from "@/interfaces/WorkImpl.ts";

const isRenderableLikedWork = (data: unknown): data is WorkImpl => {
  if (typeof data !== "object" || data === null) return false;
  const work = data as Record<string, unknown>;
  return typeof work.id === "string"
    && typeof work.title === "string"
    && typeof work.cover_image === "string"
    && typeof work.view_count === "number"
    && typeof work.like_count === "number";
}

const isLikedWorksPage = (data: unknown): data is PageResultImpl<WorkImpl> => {
  if (typeof data !== "object" || data === null) return false;
  const page = data as Record<string, unknown>;
  return Array.isArray(page.list)
    && page.list.every(isRenderableLikedWork)
    && typeof page.total === "number"
    && typeof page.page_num === "number"
    && typeof page.page_size === "number"
    && typeof page.total_page === "number";
}

/** 获取个人中心公开展示的目标用户喜欢作品分页。 */
export async function fetchLikedWorksByUsername(
  username: string,
  pageRequest: PageRequestImpl,
): Promise<PageResultImpl<WorkImpl>> {
  const encodedUsername = encodeURIComponent(username);
  const res = await baseHttp.post<ResponseImpl>(
    `/api/work/like/list_by_username?username=${encodedUsername}`,
    pageRequest,
  );

  if (res.data.code === 0) {
    if (!isLikedWorksPage(res.data.data)) {
      throw new Error("喜欢作品分页响应格式异常");
    }
    return res.data.data;
  }

  throw new Error(res.data.msg || res.data.data || "喜欢作品列表获取失败");
}
