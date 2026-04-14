import type {UserArchiveImpl} from "@/interfaces/UserImpl.ts";

export interface MessageBoardImpl {
  id: string;
  author: string;
  content: string;
  create_at: Date;
}
export interface MessageBoardWithUserImpl extends MessageBoardImpl {
  userInfo?: UserArchiveImpl;
}
