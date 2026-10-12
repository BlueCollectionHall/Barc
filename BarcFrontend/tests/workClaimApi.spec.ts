import {beforeEach, describe, expect, it, vi} from "vitest";
const http = vi.hoisted(() => ({get: vi.fn(), post: vi.fn()}));
vi.mock("@/utils/https", () => ({baseHttp: http}));
import {fetchMyClaims, submitWorkClaim} from "@/utils/workClaimApi";

/** 用户提交不能携带伪造身份，历史读取保留分页与当前作品过滤。 */
describe("认领请求契约", () => {
  beforeEach(() => vi.clearAllMocks());
  it("只提交作品和认领说明", async () => {
    http.post.mockResolvedValue({data: {code: 0}});
    await submitWorkClaim("work", "原作证明");
    expect(http.post).toHaveBeenCalledWith("/api/work/claim/upload", {work_id: "work", statement: "原作证明"});
  });
  it("读取本人记录并保留处理结果", async () => {
    const page = {list: [{id: "claim", status: "APPROVED"}], total: 1};
    http.get.mockResolvedValue({data: {code: 0, data: page}});
    expect(await fetchMyClaims(3, "work")).toEqual(page);
    expect(http.get).toHaveBeenCalledWith("/api/work/claim/mine", {params: {page: 3, size: 20, work_id: "work"}});
  });
  it("申请失败不得显示已提交", async () => {
    http.post.mockResolvedValue({data: {code: 1, data: "已认领"}});
    await expect(submitWorkClaim("work", "说明")).rejects.toThrow("已认领");
  });
});
