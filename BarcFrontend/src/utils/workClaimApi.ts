import {baseHttp} from "@/utils/https";

/** 认领提交仅传业务说明，账号与处理结果由服务端确定。 */
export interface ClaimProgress {
  id: string; work_id: string; work_title: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUPERSEDED";
  statement?: string; result_reason?: string; created_at: string; processed_at?: string;
}
export interface ClaimPage { list: ClaimProgress[]; total: number; }
export const claimStatusLabel: Record<ClaimProgress["status"], string> = {
  PENDING: "待审核", APPROVED: "已通过", REJECTED: "未通过", SUPERSEDED: "已结案（归属已确定）",
};

export async function submitWorkClaim(workId: string, statement: string): Promise<void> {
  const {data} = await baseHttp.post("/api/work/claim/upload", {work_id: workId, statement});
  if (data.code !== 0) throw new Error(String(data.data || data.msg || "申请失败"));
}

export async function fetchMyClaims(page = 1, workId?: string): Promise<ClaimPage> {
  const {data} = await baseHttp.get("/api/work/claim/mine", {params: {page, size: 20, work_id: workId}});
  if (data.code !== 0) throw new Error(String(data.data || data.msg || "加载失败"));
  return data.data as ClaimPage;
}
