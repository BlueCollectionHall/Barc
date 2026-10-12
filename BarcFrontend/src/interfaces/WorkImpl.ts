export interface WorkImpl {
  id: string;
  title: string;
  description: string | null;
  content: string | null;
  banner_image: string;
  cover_image: string;
  view_count: number;
  like_count: number;
  liked_by_current_user?: boolean;
  author: string;
  author_nickname: string | null;
  uploader: string | null;
  is_claim: boolean;
  status: string;
  review_status?: "PENDING" | "APPROVED" | "REJECTED" | null;
  review_reason?: string | null;
  reviewer_uuid?: string | null;
  review_submitted_at?: Date | null;
  reviewed_at?: Date | null;
  student: string;
  created_at: Date;
  content_updated_at?: Date | null;
  /** 服务端内容版本，用于编辑冲突检测。 */
  content_revision?: number;
  updated_at: Date;
}

export interface WorkCommentImpl {
  id: string;
  work_id: string;
  author: string;
  content: string;
  created_at: Date;
  replies?: Array<WorkCommentReplyImpl>
  /** 回复分页的真实总数，首屏只附带少量预览。 */
  reply_count?: number;
}

export interface WorkCommentReplyImpl {
  id: string;
  parent_id: string;
  author: string;
  reply_user: string | null;
  content: string;
  created_at: Date;
}

export interface WorkClaimImpl {
  id: string;
  work_id: string;
  applicant_uuid: string;
  created_at: Date;
}

export interface WorkCategoryImpl {
  id: string;
  work_id: string;
  category_id: string;
  created_at: Date;
}

export interface WorkImageImpl {
  id: string;
  work_id: string;
  sort: number;
  object_key: string;
  created_at: Date;
}
