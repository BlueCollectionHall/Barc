import type {FeedBackImpl} from "@/interfaces/FeedbackImpl.ts";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {baseHttp} from "@/utils/https.ts";

export type ManageWorkStatus = "PUBLIC" | "PRIVATE" | "OFF" | "BAN" | string;
export type OwnerVisibilityStatus = "PUBLIC" | "PRIVATE";

export interface ManageWorkSecondaryAction {
  kind: "visibility" | "appeal";
  label: string;
  targetStatus?: OwnerVisibilityStatus;
}

interface BuildWorkAppealFeedbackOptions {
  workId: string;
  workTitle: string;
  content: string;
  author: string | null;
  email: string | null;
}

export const getManageWorkSecondaryAction = (status: ManageWorkStatus): ManageWorkSecondaryAction | null => {
  // 状态动作矩阵：公开/私有只允许作者自助切换；下架/封禁必须走申诉，不能前端自助恢复。
  if (status === "PUBLIC") return {kind: "visibility", label: "设为私有", targetStatus: "PRIVATE"};
  if (status === "PRIVATE") return {kind: "visibility", label: "设为公开", targetStatus: "PUBLIC"};
  if (status === "OFF" || status === "BAN") return {kind: "appeal", label: "申诉"};
  return null;
};

export const shouldShowWorkAppealEmailInput = (userBasic: unknown | null | undefined): boolean => {
  // 易回归规则：匿名申诉才显示邮箱输入框；登录用户沿用账号邮箱自动填充。
  return userBasic == null;
};

export const buildWorkAppealFeedback = (options: BuildWorkAppealFeedbackOptions): FeedBackImpl => {
  const now = new Date();
  const trimmedContent = options.content.trim();
  return {
    id: "",
    target_id: options.workId,
    ipv4: null,
    ipv6: null,
    author: options.author,
    email: options.email,
    // 复用通用 feedback(type=WORK) 给管理端审核；不要回到已废弃的 feedback_work 链路。
    content: `【作品申诉】\n作品：${options.workTitle}\n申诉内容：${trimmedContent}`,
    echo: null,
    type: "WORK",
    status: "PENDING",
    created_at: now,
    updated_at: now,
  };
};

export const updateOwnerWorkVisibility = async (
  workId: string,
  status: ManageWorkStatus,
  token: string,
): Promise<void> => {
  if (status !== "PUBLIC" && status !== "PRIVATE") {
    throw new Error("只允许切换公开/私有状态");
  }

  const response = await baseHttp.put(
    "/api/work/visibility",
    {work_id: workId, status},
    {headers: {Authorization: token}},
  );
  const data: ResponseImpl = response.data;
  if (data.code !== 0) throw new Error(data.msg || "状态更新失败");
};
