import { beforeEach, describe, expect, it, vi } from "vitest";

import { baseHttp } from "@/utils/https.ts";
import {
  buildWorkAppealFeedback,
  getManageWorkSecondaryAction,
  resubmitRejectedWork,
  shouldShowWorkAppealEmailInput,
  updateOwnerWorkVisibility,
} from "@/utils/manageWorkStatusActions.ts";

vi.mock("@/utils/https.ts", () => ({
  baseHttp: {
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe("manage work status action matrix", () => {
  it("shows private toggle for PUBLIC works", () => {
    expect(getManageWorkSecondaryAction("PUBLIC")).toEqual({
      kind: "visibility",
      label: "设为私有",
      targetStatus: "PRIVATE",
    });
  });

  it("shows public toggle for PRIVATE works", () => {
    expect(getManageWorkSecondaryAction("PRIVATE")).toEqual({
      kind: "visibility",
      label: "设为公开",
      targetStatus: "PUBLIC",
    });
  });

  it("shows appeal entry for OFF and BAN works", () => {
    expect(getManageWorkSecondaryAction("OFF")).toEqual({kind: "appeal", label: "申诉"});
    expect(getManageWorkSecondaryAction("BAN")).toEqual({kind: "appeal", label: "申诉"});
  });
});

describe("work appeal feedback payload", () => {
  it("reuses generic WORK feedback semantics with user-filled appeal content", () => {
    const feedback = buildWorkAppealFeedback({
      workId: "work-1",
      workTitle: "测试作品",
      content: "  我已经修改了问题内容，请管理员复核。 ",
      author: "user-1",
      email: "user@example.com",
    });

    expect(feedback).toMatchObject({
      target_id: "work-1",
      author: "user-1",
      email: "user@example.com",
      type: "WORK",
      status: "PENDING",
      echo: null,
      ipv4: null,
      ipv6: null,
    });
    expect(feedback.content).toContain("【作品申诉】");
    expect(feedback.content).toContain("作品：测试作品");
    expect(feedback.content).toContain("我已经修改了问题内容，请管理员复核。");
  });
});

describe("work appeal email visibility", () => {
  it("hides the optional appeal email input for logged-in users", () => {
    expect(shouldShowWorkAppealEmailInput({uuid: "user-1"})).toBe(false);
  });

  it("shows the optional appeal email input for anonymous users", () => {
    expect(shouldShowWorkAppealEmailInput(null)).toBe(true);
    expect(shouldShowWorkAppealEmailInput(undefined)).toBe(true);
  });
});

describe("owner work visibility api", () => {
  beforeEach(() => {
    vi.mocked(baseHttp.put).mockReset();
  });

  it("calls the owner-safe status endpoint with raw JWT authorization", async () => {
    vi.mocked(baseHttp.put).mockResolvedValueOnce({
      data: {code: 0, msg: "ok", data: "修改成功"},
    });

    await updateOwnerWorkVisibility("work-1", "PRIVATE", "raw.jwt.token");

    expect(baseHttp.put).toHaveBeenCalledWith(
      "/api/work/visibility",
      {work_id: "work-1", status: "PRIVATE"},
      {headers: {Authorization: "raw.jwt.token"}},
    );
  });

  it("rejects OFF/BAN self-restore attempts from owner buttons", async () => {
    await expect(updateOwnerWorkVisibility("work-1", "OFF", "raw.jwt.token")).rejects.toThrow("只允许切换公开/私有状态");
    expect(baseHttp.put).not.toHaveBeenCalled();
  });
});

describe("rejected work resubmit api", () => {
  beforeEach(() => {
    vi.mocked(baseHttp.post).mockReset();
  });

  it("submits the rejected work through the owner-only review endpoint", async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {code: 0, msg: "ok", data: "修改成功"},
    });

    await resubmitRejectedWork("work-1", "raw.jwt.token");

    expect(baseHttp.post).toHaveBeenCalledWith(
      "/api/work/review/resubmit",
      {work_id: "work-1"},
      {headers: {Authorization: "raw.jwt.token"}},
    );
  });

  it("surfaces the backend state error when the work is no longer rejected", async () => {
    vi.mocked(baseHttp.post).mockResolvedValueOnce({
      data: {code: 1, msg: "失败：Error", data: "只有审核未通过的作品可以再次提审"},
    });

    await expect(resubmitRejectedWork("work-1", "raw.jwt.token"))
      .rejects.toThrow("只有审核未通过的作品可以再次提审");
  });
});
