import type { WorkCommentImpl, WorkCommentReplyImpl } from "@/interfaces/WorkImpl.ts";

/** 前端展示用的评论（含用户昵称） */
export interface CommentDisplayImpl extends WorkCommentImpl {
  authorNickname: string;
  authorAvatar: string | null;
  replies?: Array<ReplyDisplayImpl>;
}

/** 前端展示用的回复（含用户昵称） */
export interface ReplyDisplayImpl extends WorkCommentReplyImpl {
  authorNickname: string;
  authorAvatar: string | null;
  replyUserNickname: string | null;
}

/** 评论分页响应 */
export interface CommentPageImpl {
  comments: Array<CommentDisplayImpl>;
  total: number;
  hasMore: boolean;
}
