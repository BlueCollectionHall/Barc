import {describe, expect, it} from "vitest";

import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {
  buildManageWorkFilterParams,
  buildOwnerWorkListParams,
  createConfirmedCoverFile,
  createEditSnapshot,
  detectEditDirtyState,
  normalizeEditDetail,
  toEditUpdatePayload,
} from "@/utils/manageWorkEditHelpers.ts";

const baseWork: WorkImpl = {
  id: "work-1",
  title: "旧标题",
  description: "旧简介",
  content: "<p>旧内容</p>",
  banner_image: "",
  cover_image: "",
  view_count: 10,
  like_count: 2,
  author: "owner-1",
  author_nickname: null,
  uploader: null,
  is_claim: true,
  status: "PUBLIC",
  student: "student-1",
  created_at: new Date("2025-01-01T00:00:00Z"),
  updated_at: new Date("2025-01-02T00:00:00Z"),
};

describe("manage work filter params", () => {
  it("omits blank filters and keeps required uuid/status", () => {
    expect(buildManageWorkFilterParams("uuid-1", "PUBLIC", {type: "keyword", value: "   "})).toEqual({
      uuid: "uuid-1",
      status: "PUBLIC",
    });
  });

  it("maps the selected current-list filter field to backend params", () => {
    expect(buildManageWorkFilterParams("uuid-1", "PRIVATE", {type: "school", value: "  阿拜多斯  "})).toEqual({
      uuid: "uuid-1",
      status: "PRIVATE",
      school: "阿拜多斯",
    });
  });

  it("builds a review-only query for pending works across visibility states", () => {
    expect(buildOwnerWorkListParams("PENDING", {type: "keyword", value: "  白子  "})).toEqual({
      review_status: "PENDING",
      keyword: "白子",
    });
  });

  it("locks approved visibility sections to both status dimensions", () => {
    expect(buildOwnerWorkListParams("PRIVATE", {type: "school", value: ""})).toEqual({
      status: "PRIVATE",
      review_status: "APPROVED",
    });
  });
});

describe("owner edit detail helpers", () => {
  it("prefills work, cover preview, and gallery previews from edit-detail", () => {
    const detail = normalizeEditDetail({
      work: baseWork,
      cover_image_url: "https://cover.example/signed",
      content_images: [
        {id: "img-2", url: "https://img.example/2", sort: 2},
        {id: "img-1", url: "https://img.example/1", sort: 1},
      ],
      content_image_urls: ["https://legacy.example/ignored"],
      student_name: "白子",
      school_name: "阿拜多斯",
      club_name: "对策委员会",
      author_display: "作者显示名",
      uploader_nickname: "收录者",
    });

    expect(detail.work.title).toBe("旧标题");
    expect(detail.coverPreviewUrl).toBe("https://cover.example/signed");
    expect(detail.galleryImages.map((item) => item.id)).toEqual(["img-1", "img-2"]);
    expect(detail.studentLabel).toBe("白子 / 阿拜多斯 / 对策委员会");
  });

  it("falls back to legacy gallery URLs without pretending they are deletable", () => {
    const detail = normalizeEditDetail({
      work: baseWork,
      cover_image_url: "",
      content_image_urls: ["https://legacy.example/1"],
    });

    expect(detail.galleryImages).toEqual([
      {id: null, previewUrl: "https://legacy.example/1", sort: 0, source: "remote"},
    ]);
  });

  it("detects split dirty state for text, cover, additions, and deletions", () => {
    const normalized = normalizeEditDetail({
      work: baseWork,
      cover_image_url: "https://cover.example/signed",
      content_images: [{id: "img-1", url: "https://img.example/1", sort: 0}],
    });
    const snapshot = createEditSnapshot(normalized.work, normalized.galleryImages);

    const dirty = detectEditDirtyState({
      snapshot,
      currentWork: {...normalized.work, title: "新标题"},
      coverFile: new File(["cover"], "cover.png", {type: "image/png"}),
      galleryImages: [],
      newGalleryFiles: [new File(["gallery"], "gallery.png", {type: "image/png"})],
    });

    expect(dirty).toEqual({
      text: true,
      cover: true,
      galleryAdditions: true,
      galleryDeletions: true,
      deletedImageIds: ["img-1"],
    });
  });

  it("builds the text update payload without legacy image fields", () => {
    expect(toEditUpdatePayload({...baseWork, title: "新标题"})).toEqual({
      id: "work-1",
      title: "新标题",
      description: "旧简介",
      content: "<p>旧内容</p>",
      author_nickname: null,
      is_claim: true,
      student: "student-1",
    });
  });

  it("creates a saveable cover file only from a confirmed cropped blob", async () => {
    const croppedBlob = new Blob(["cropped"], {type: "image/png"});
    const coverFile = createConfirmedCoverFile(croppedBlob);

    expect(coverFile.name).toBe("cropped-cover.png");
    expect(coverFile.type).toBe("image/png");
    expect(await coverFile.text()).toBe("cropped");
  });
});
