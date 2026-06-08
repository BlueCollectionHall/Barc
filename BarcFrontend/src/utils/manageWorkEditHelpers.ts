import type {WorkImpl} from "@/interfaces/WorkImpl.ts";

export type ManageWorkFilterType = "keyword" | "school" | "club" | "student";

export interface ManageWorkFilterState {
  type: ManageWorkFilterType;
  value: string;
}

export interface EditGalleryImage {
  id: string | null;
  previewUrl: string;
  sort: number;
  source: "remote" | "local";
}

export interface NormalizedEditDetail {
  work: WorkImpl;
  coverPreviewUrl: string;
  galleryImages: Array<EditGalleryImage>;
  studentLabel: string;
  authorDisplay: string;
  uploaderNickname: string;
}

export interface EditSnapshot {
  textJson: string;
  remoteImageIds: Array<string>;
}

export interface DetectEditDirtyStateOptions {
  snapshot: EditSnapshot;
  currentWork: WorkImpl;
  coverFile: File | null;
  galleryImages: Array<EditGalleryImage>;
  newGalleryFiles: Array<File>;
}

export interface EditDirtyState {
  text: boolean;
  cover: boolean;
  galleryAdditions: boolean;
  galleryDeletions: boolean;
  deletedImageIds: Array<string>;
}

export type EditUpdatePayload = Pick<
  WorkImpl,
  "id" | "title" | "description" | "content" | "author_nickname" | "is_claim" | "student"
>;

interface RawContentImage {
  id?: unknown;
  url?: unknown;
  sort?: unknown;
}

interface RawEditDetail {
  work: WorkImpl;
  cover_image_url?: string;
  content_images?: Array<RawContentImage>;
  content_image_urls?: Array<string>;
  student_name?: string;
  school_name?: string;
  club_name?: string;
  author_display?: string;
  uploader_nickname?: string;
}

export const buildManageWorkFilterParams = (
  uuid: string,
  status: string,
  filter: ManageWorkFilterState,
): Record<string, string> => {
  const params: Record<string, string> = {uuid, status};
  const value = filter.value.trim();
  if (!value) return params;
  params[filter.type] = value;
  return params;
};

export const normalizeEditDetail = (detail: RawEditDetail): NormalizedEditDetail => {
  const richImages = Array.isArray(detail.content_images) ? detail.content_images : [];
  const legacyUrls = Array.isArray(detail.content_image_urls) ? detail.content_image_urls : [];

  // API 边界：新版 content_images 带有数据库 id，可用于删除；旧版 URL 只能预览，不能假装可删。
  const galleryImages: Array<EditGalleryImage> = richImages.length > 0
    ? richImages
      .map((item: any, index: number) => ({
        id: typeof item.id === "string" ? item.id : null,
        previewUrl: typeof item.url === "string" ? item.url : "",
        sort: typeof item.sort === "number" ? item.sort : index,
        source: "remote" as const,
      }))
      .filter((item) => item.previewUrl.length > 0)
      .sort((a, b) => a.sort - b.sort)
    : legacyUrls.map((url: string, index: number) => ({
      id: null,
      previewUrl: url,
      sort: index,
      source: "remote" as const,
    }));

  const labelParts = [detail.student_name, detail.school_name, detail.club_name]
    .filter((item) => typeof item === "string" && item.trim().length > 0);

  return {
    work: detail.work as WorkImpl,
    coverPreviewUrl: detail.cover_image_url || "",
    galleryImages,
    studentLabel: labelParts.join(" / "),
    authorDisplay: detail.author_display || "",
    uploaderNickname: detail.uploader_nickname || "",
  };
};

export const toEditUpdatePayload = (work: WorkImpl): EditUpdatePayload => ({
  id: work.id,
  title: work.title,
  description: work.description,
  content: work.content,
  author_nickname: work.author_nickname,
  is_claim: work.is_claim,
  student: work.student,
});

export const createConfirmedCoverFile = (croppedBlob: Blob): File => (
  new File([croppedBlob], "cropped-cover.png", {type: croppedBlob.type || "image/png"})
);

export const createEditSnapshot = (work: WorkImpl, galleryImages: Array<EditGalleryImage>): EditSnapshot => ({
  textJson: JSON.stringify(toEditUpdatePayload(work)),
  remoteImageIds: galleryImages
    .map((item) => item.id)
    .filter((id): id is string => typeof id === "string" && id.length > 0),
});

export const detectEditDirtyState = (options: DetectEditDirtyStateOptions): EditDirtyState => {
  const currentRemoteIds = new Set(
    options.galleryImages
      .map((item) => item.id)
      .filter((id): id is string => typeof id === "string" && id.length > 0),
  );
  const deletedImageIds = options.snapshot.remoteImageIds.filter((id) => !currentRemoteIds.has(id));

  // 状态切分：文字、封面、图集新增/删除走不同接口，避免一次保存误覆盖图片表。
  return {
    text: JSON.stringify(toEditUpdatePayload(options.currentWork)) !== options.snapshot.textJson,
    cover: options.coverFile !== null,
    galleryAdditions: options.newGalleryFiles.length > 0,
    galleryDeletions: deletedImageIds.length > 0,
    deletedImageIds,
  };
};
