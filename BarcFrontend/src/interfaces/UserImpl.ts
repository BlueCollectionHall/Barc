export interface UserArchiveImpl {
  uuid: string;
  nickname: string;
  avatar: string | null;
  gender: boolean;
  birthday: Date;
  age: number;
  permission: number;
  identity: string;
  updated_at: Date;
}
export interface UserBasicImpl {
  uuid: string;
  username: string;
  password: string;
  password_version: number;
  email: string;
  email_verified: boolean;
  telephone: string | null;
  safe_level: number;
  created_at: Date;
  updated_at: Date;
}
