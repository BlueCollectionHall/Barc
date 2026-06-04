import { baseHttp } from "@/utils/https.ts";
import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";

export interface WorkLikeToggleResult {
  liked: boolean;
  like_count: number;
}

const isWorkLikeToggleResult = (data: unknown): data is WorkLikeToggleResult => {
  if (typeof data !== "object" || data === null) return false;
  const result = data as Record<string, unknown>;
  return typeof result.liked === "boolean" && typeof result.like_count === "number";
}

/** 切换作品点赞状态，返回值必须以服务端为准，避免前端乐观更新与后端不一致。 */
export async function toggleWorkLike(workId: string, token: string): Promise<WorkLikeToggleResult> {
  const res = await baseHttp.post<ResponseImpl>(
    "/api/work/like/toggle",
    { work_id: workId },
    { headers: { Authorization: token } },
  );

  if (res.data.code === 0) {
    if (!isWorkLikeToggleResult(res.data.data)) {
      throw new Error("点赞响应格式异常");
    }
    return res.data.data;
  }

  throw new Error(res.data.msg || res.data.data || "点赞失败");
}
