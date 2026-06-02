import { baseHttp } from "@/utils/https.ts";
import type { ResponseImpl } from "@/interfaces/ResponseImpl.ts";
import type { WorkCommentImpl, WorkCommentReplyImpl } from "@/interfaces/WorkImpl.ts";
import type { FeedBackImpl } from "@/interfaces/FeedbackImpl.ts";

/** 获取作品的所有评论和回复 */
export async function fetchCommentsByWork(workId: string): Promise<Array<WorkCommentImpl>> {
  const res = await baseHttp.get<ResponseImpl>("/comment/work/comments_by_work", {
    params: { work_id: workId },
  });
  if (res.data.code === 0) {
    return res.data.data as Array<WorkCommentImpl>;
  }
  throw new Error(res.data.msg || "获取评论失败");
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
  return res.data.code === 0;
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
  return res.data.code === 0;
}

/** 删除评论 */
export async function deleteComment(commentId: string, token: string): Promise<boolean> {
  const res = await baseHttp.delete<ResponseImpl>("/comment/work/delete_comment", {
    params: { comment_id: commentId },
    headers: { Authorization: token },
  });
  return res.data.code === 0;
}

/** 删除回复 */
export async function deleteReply(replyId: string, token: string): Promise<boolean> {
  const res = await baseHttp.delete<ResponseImpl>("/comment/work/delete_reply", {
    params: { reply_id: replyId },
    headers: { Authorization: token },
  });
  return res.data.code === 0;
}

/** 举报评论（复用现有反馈体系，type=COMMENT） */
export async function reportComment(feedback: FeedBackImpl): Promise<boolean> {
  const res = await baseHttp.post<ResponseImpl>("/feedback/upload", feedback);
  return res.data.code === 0;
}

/** 根据用户 UUID 获取用户档案 */
export async function fetchUserArchive(uuid: string): Promise<{ nickname: string; avatar: string | null } | null> {
  const res = await baseHttp.get<ResponseImpl>("/user/current_by_uuid", {
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
