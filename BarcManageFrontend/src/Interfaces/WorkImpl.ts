export interface WorkImpl {
  id: string;
  title: string;
  description: string | null;
  content: string | null;
  banner_image: string;
  cover_image: string;
  view_count: number;
  like_count: number;
  author: string;
  author_nickname: string | null;
  uploader: string | null;
  is_claim: boolean;
  status: string;
  student: string;
  created_at: Date;
  updated_at: Date;
}

export interface WorkCommentImpl {
  id: string;
  work_id: string;
  author: string;
  content: string;
  created_at: Date;
  replies?: Array<WorkCommentReplyImpl>
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
