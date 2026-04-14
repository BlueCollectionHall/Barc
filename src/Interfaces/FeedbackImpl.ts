export interface FeedbackTypeImpl {
  id: string;
  name: string;
}

export interface FeedbackOptionImpl {
  id: string;
  name: string;
  parent_id: string;
}

export interface FeedBackImpl {
  id: string;
  target_id: string;
  ipv4: string | null;
  ipv6: string | null;
  author: string | null;
  email: string | null;
  content: string;
  echo: string | null;
  type: string;
  status: string;
  created_at: Date;
  updated_at: Date;
}

export interface FeedbackWorkImpl {
  id: string;
  work_id: string;
  ipv4: string | null;
  device_info: string | null;
  author: string | null;
  reason_option: string;
  content: string | null;
  email: string | null;
  status: boolean;
  note: string | null;
  create_at: Date;
  update_at: Date;
}
