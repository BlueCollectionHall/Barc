import { baseHttp } from "@/utils/https.ts";
import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import type { WorkCommentImpl, WorkCommentReplyImpl } from "@/interfaces/WorkImpl.ts";
import type { FeedBackImpl } from "@/interfaces/FeedbackImpl.ts";

/** 兼容旧列表；新界面使用分页接口。 */
export async function fetchCommentsByWork(workId: string): Promise<Array<WorkCommentImpl>> {
  const res = await baseHttp.get<ResponseImpl>("/comment/work/comments_by_work", {
    params: { work_id: workId },
  });
  if (res.data.code === 0) {
    return res.data.data as Array<WorkCommentImpl>;
  }
  throw new Error(res.data.msg || "获取评论失败");
}

/** 分页元数据使用服务端总数，回复预览不影响真实统计。 */
export interface CommentPage<T> { total: number; list: T[]; page_num: number; page_size: number; total_page: number }

export async function fetchCommentsPage(workId: string, page = 1): Promise<CommentPage<WorkCommentImpl>> {
  const res = await baseHttp.get<ResponseImpl>("/comment/work/page", { params: { work_id: workId, page, size: 10 } });
  if (res.data.code !== 0) throw new Error(res.data.msg || "获取评论失败");
  return res.data.data as CommentPage<WorkCommentImpl>;
}

/** 五条回复为一页，与主评论的首屏预览容量相同。 */
export async function fetchRepliesPage(parentId: string, page: number): Promise<CommentPage<WorkCommentReplyImpl>> {
  const res = await baseHttp.get<ResponseImpl>("/comment/work/replies/page", { params: { parent_id: parentId, page, size: 5 } });
  if (res.data.code !== 0) throw new Error(res.data.msg || "获取回复失败");
  return res.data.data as CommentPage<WorkCommentReplyImpl>;
}

/** 获取作品评论总数（主评论数 + 所有回复数） */
export async function fetchCommentCountByWork(workId: string): Promise<number> {
  const res = await baseHttp.get<ResponseImpl>("/comment/work/count_by_work", {
    params: { work_id: workId },
  });
  if (res.data.code !== 0) {
    throw new Error(res.data.msg || "获取评论数失败");
  }
  if (typeof res.data.data !== "number") {
    throw new Error("评论数响应格式异常");
  }
  return res.data.data;
}

/** 发布评论 */
export async function uploadComment(
  workId: string,
  content: string,
  token: string,
): Promise<boolean> {
  const res = await baseHttp.post<ResponseImpl>(
    "/comment/work/upload_comment",
    { work_id: workId, content },
    { headers: { Authorization: token } },
  );
  if (res.data.code !== 0) throw new Error(res.data.msg || "评论提交失败");
  return true;
}

/** 回复评论 */
export async function uploadReply(
  parentId: string,
  replyUser: string,
  content: string,
  token: string,
): Promise<boolean> {
  const res = await baseHttp.post<ResponseImpl>(
    "/comment/work/upload_reply",
    { parent_id: parentId, reply_user: replyUser, content },
    { headers: { Authorization: token } },
  );
  if (res.data.code !== 0) throw new Error(res.data.msg || "回复提交失败");
  return true;
}

/** 删除评论 */
export async function deleteComment(commentId: string, token: string): Promise<boolean> {
  const res = await baseHttp.delete<ResponseImpl>("/comment/work/delete_comment", {
    params: { comment_id: commentId },
    headers: { Authorization: token },
  });
  if (res.data.code !== 0) throw new Error(res.data.msg || "评论删除失败");
  return true;
}

/** 删除回复 */
export async function deleteReply(replyId: string, token: string): Promise<boolean> {
  const res = await baseHttp.delete<ResponseImpl>("/comment/work/delete_reply", {
    params: { reply_id: replyId },
    headers: { Authorization: token },
  });
  if (res.data.code !== 0) throw new Error(res.data.msg || "回复删除失败");
  return true;
}

/** 举报评论（复用现有反馈体系，type=COMMENT） */
export async function reportComment(feedback: FeedBackImpl): Promise<boolean> {
  const res = await baseHttp.post<ResponseImpl>("/feedback/upload", feedback);
  if (res.data.code !== 0) throw new Error(res.data.msg || "举报提交失败");
  return true;
}

/** 根据用户 UUID 获取用户档案 */
export async function fetchUserArchive(uuid: string): Promise<{ nickname: string; avatar: string | null } | null> {
  const res = await baseHttp.get<ResponseImpl<{ nickname?: string; avatar?: string }>>("/user/current_by_uuid", {
    params: { uuid },
  });
  if (res.data.code === 0 && res.data.data) {
    return {
      nickname: res.data.data.nickname || "未知用户",
      avatar: res.data.data.avatar || null,
    };
  }
  return null;
}
