<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from "vue";
import {useRoute, useRouter} from "vue-router";
import {QuillEditor} from "@vueup/vue-quill";
import "@vueup/vue-quill/dist/vue-quill.snow.css";
import Cropper from "cropperjs";
import "cropperjs/dist/cropper.css";
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  LeftOutlined,
  PlusSquareOutlined,
  QuestionCircleOutlined,
  SaveOutlined,
} from "@ant-design/icons-vue";

import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import type {StudentImpl} from "@/interfaces/BaImpl.ts";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {baseHttp} from "@/utils/https.ts";
import {
  type EditGalleryImage,
  type EditSnapshot,
  createConfirmedCoverFile,
  createEditSnapshot,
  detectEditDirtyState,
  normalizeEditDetail,
  toEditUpdatePayload,
} from "@/utils/manageWorkEditHelpers.ts";

const route = useRoute();
const router = useRouter();
const userPinia = useUserPinia();

const token = ref<string | null>(window.localStorage.getItem("token"));
const workForm = ref<WorkImpl | null>(null);
const content = ref<string>("<p></p>");
const coverPreviewUrl = ref<string>("");
const coverFile = ref<File | null>(null);
const coverInputRef = ref<HTMLInputElement | null>(null);
const coverCropImageValue = ref<string | null>(null);
const coverCropImageRef = ref<HTMLImageElement | null>(null);
const galleryInputRef = ref<HTMLInputElement | null>(null);
const galleryImages = ref<Array<EditGalleryImage>>([]);
const newGalleryFiles = ref<Array<File>>([]);
const newGalleryPreviewUrls = ref<Array<string>>([]);
const snapshot = ref<EditSnapshot | null>(null);
const studentKeyword = ref<string>("");
const studentList = ref<Array<StudentImpl>>([]);
const studentLabel = ref<string>("");
const authorDisplay = ref<string>("");
const uploaderNickname = ref<string>("");
const loading = ref<boolean>(false);
const saving = ref<boolean>(false);
let coverCropper: Cropper | null = null;

const editorOptions = ref({
  modules: {
    toolbar: [
      ["bold", "italic", "underline"],
      ["blockquote", "code-block"],
    ],
  },
  placeholder: "请输入内容...",
  theme: "snow",
});

const workId = computed(() => route.query.work_id as string | undefined);
const dirtyState = computed(() => {
  if (!snapshot.value || !workForm.value) {
    return {text: false, cover: false, galleryAdditions: false, galleryDeletions: false, deletedImageIds: []};
  }
  return detectEditDirtyState({
    snapshot: snapshot.value,
    currentWork: workForm.value,
    coverFile: coverFile.value,
    galleryImages: galleryImages.value,
    newGalleryFiles: newGalleryFiles.value,
  });
});
const hasDirty = computed(() => Object.values({
  text: dirtyState.value.text,
  cover: dirtyState.value.cover,
  galleryAdditions: dirtyState.value.galleryAdditions,
  galleryDeletions: dirtyState.value.galleryDeletions,
}).some(Boolean));

const authHeaders = () => ({Authorization: token.value || ""});

const fetchEditDetail = async () => {
  if (!workId.value) {
    errorMessage("作品ID未找到");
    return;
  }
  if (!token.value) {
    errorMessage("未登录，无法编辑作品");
    return;
  }
  loading.value = true;
  try {
    const response = await baseHttp.get("/api/work/edit-detail", {
      params: {work_id: workId.value},
      headers: authHeaders(),
    });
    const data: ResponseImpl = response.data;
    if (data.code !== 0) {
      infoMessage(data.msg);
      return;
    }

    const detail = normalizeEditDetail(data.data);
    workForm.value = {...detail.work};
    content.value = detail.work.content || "<p></p>";
    coverPreviewUrl.value = detail.coverPreviewUrl;
    coverFile.value = null;
    coverCropImageValue.value = null;
    if (coverCropper) coverCropper.destroy();
    coverCropper = null;
    galleryImages.value = detail.galleryImages;
    newGalleryFiles.value = [];
    newGalleryPreviewUrls.value = [];
    studentLabel.value = detail.studentLabel;
    authorDisplay.value = detail.authorDisplay;
    uploaderNickname.value = detail.uploaderNickname;
    snapshot.value = createEditSnapshot(detail.work, detail.galleryImages);
  } catch (e) {
    console.error(e instanceof Error ? e.message : "fetch edit detail failed");
    errorMessage("网络错误");
  } finally {
    loading.value = false;
  }
};

const handleEditorChange = (html: string) => {
  if (!workForm.value) return;
  workForm.value.content = html.replace(/<script.*?>.*?<\/script>/gis, "");
};

const chooseCover = () => coverInputRef.value?.click();

const initCoverCropper = () => {
  if (!coverCropImageRef.value) return;
  if (coverCropper) coverCropper.destroy();
  coverCropper = new Cropper(coverCropImageRef.value, {
    aspectRatio: 16 / 9,
    viewMode: 1,
    dragMode: "move",
    guides: true,
    background: false,
    autoCropArea: 0.8,
    preview: ".cover_image_preview",
  });
};

const handleCoverChange = (e: Event) => {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.readAsDataURL(file);
  reader.onloadend = () => {
    // 状态边界：这里只进入“原图已选择/可裁剪”，不能把原图直接放入待保存 coverFile。
    coverCropImageValue.value = String(reader.result);
    nextTick(() => initCoverCropper());
  };
  if (input) input.value = "";
};

const confirmCroppedCover = () => {
  if (!coverCropper) {
    errorMessage("请先选择封面图并等待裁剪器加载");
    return;
  }
  const canvas = coverCropper.getCroppedCanvas({
    width: 640,
    height: 360,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: "high",
  });
  canvas?.toBlob((blob: Blob | null) => {
    if (!blob) {
      errorMessage("动态裁剪失败");
      return;
    }
    // 状态边界：只有点击“确认使用”后的裁剪结果，才会成为保存时上传的新封面。
    coverFile.value = createConfirmedCoverFile(blob);
    coverPreviewUrl.value = URL.createObjectURL(blob);
    successMessage("已确认裁剪封面，保存后生效");
  }, "image/png");
};

const chooseGallery = () => galleryInputRef.value?.click();

const handleGalleryChange = (e: Event) => {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  if (files.length === 0) return;
  newGalleryFiles.value = [...newGalleryFiles.value, ...files];
  newGalleryPreviewUrls.value = [...newGalleryPreviewUrls.value, ...files.map((file) => URL.createObjectURL(file))];
  if (input) input.value = "";
};

const removeRemoteGalleryImage = (image: EditGalleryImage) => {
  // 旧接口只给 URL 没给图片 id，删除必须等新版 content_images 返回 id 后才能安全执行。
  if (!image.id) {
    infoMessage("这张旧图片缺少删除ID，暂时只能预览");
    return;
  }
  galleryImages.value = galleryImages.value.filter((item) => item !== image);
};

const removeNewGalleryImage = (index: number) => {
  newGalleryFiles.value.splice(index, 1);
  newGalleryPreviewUrls.value.splice(index, 1);
};

const fetchStudentsByKeyword = async () => {
  if (!studentKeyword.value.trim()) {
    errorMessage("请正确输入学生名字！");
    return;
  }
  try {
    const response = await baseHttp("/api/student/list", {params: {keyword: studentKeyword.value.trim()}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) studentList.value = data.data.list;
    else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误！");
  }
};

const selectStudent = (student: StudentImpl) => {
  if (!workForm.value) return;
  workForm.value.student = student.id;
  studentLabel.value = student.cn_name;
};

const assertResponseOk = (data: ResponseImpl) => {
  if (data.code !== 0) throw new Error(data.msg || "保存失败");
};

const saveText = async () => {
  if (!workForm.value) return;
  const response = await baseHttp.put("/api/work/edit-update", toEditUpdatePayload(workForm.value), {
    headers: authHeaders(),
  });
  assertResponseOk(response.data);
};

const saveCover = async () => {
  if (!workId.value || !coverFile.value) return;
  const formData = new FormData();
  formData.append("cover_image", coverFile.value);
  const response = await baseHttp.put("/api/work/cover/replace", formData, {
    params: {work_id: workId.value},
    headers: authHeaders(),
  });
  assertResponseOk(response.data);
};

const saveGalleryDeletions = async () => {
  for (const imageId of dirtyState.value.deletedImageIds) {
    const response = await baseHttp.delete("/api/work/image/delete", {
      params: {work_image_id: imageId},
      headers: authHeaders(),
    });
    assertResponseOk(response.data);
  }
};

const saveGalleryAdditions = async () => {
  if (!workId.value) return;
  for (const file of newGalleryFiles.value) {
    const formData = new FormData();
    formData.append("file", file);
    const response = await baseHttp.post("/api/work/image/upload", formData, {
      params: {work_id: workId.value},
      headers: authHeaders(),
    });
    assertResponseOk(response.data);
  }
};

const saveAll = async () => {
  if (!workForm.value || !snapshot.value) return;
  if (!hasDirty.value) {
    infoMessage("还没有需要保存的修改哦");
    return;
  }
  saving.value = true;
  try {
    // 保存顺序按接口边界拆开：文字 -> 封面 -> 旧图删除 -> 新图追加，失败时不继续误提交后续图片操作。
    if (dirtyState.value.text) await saveText();
    if (dirtyState.value.cover) await saveCover();
    if (dirtyState.value.galleryDeletions) await saveGalleryDeletions();
    if (dirtyState.value.galleryAdditions) await saveGalleryAdditions();
    await fetchEditDetail();
    successMessage("作品修改已保存");
  } catch (e) {
    errorMessage(e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
};

onMounted(async () => {
  if (token.value) await userPinia.fetchUserInfo(token.value);
  await fetchEditDetail();
});
</script>

<template>
  <div class="container">
    <div class="title-container">
      <el-button class="back-button" text @click="router.push({name: 'ManageWork'})"><LeftOutlined /> 返回作品管理</el-button>
      <h1 class="title">编辑作品</h1>
      <p class="subtitle">文字、封面、图集会分开保存，避免图片被误覆盖。</p>
    </div>

    <div v-if="loading" class="box loading_box">加载中…</div>
    <template v-else-if="workForm">
      <div class="cover_image_box">
        <QuestionCircleOutlined v-if="!hasDirty" class="cover_status_icon warning" />
        <CheckCircleOutlined v-else class="cover_status_icon success" />
        <h2 class="cover_title">封面预览</h2>
        <div class="input_item_box">
          <input ref="coverInputRef" type="file" accept="image/*" @change="handleCoverChange" />
          <button class="file-input" type="button" @click="chooseCover">选择新封面</button>
          <el-button type="success" :disabled="!coverCropImageValue" @click="confirmCroppedCover">确认使用</el-button>
          <span class="hint">当前保存状态：{{dirtyState.cover ? '封面待保存' : '封面未修改'}}；选择图片后请先裁剪并确认。</span>
        </div>
        <div class="crop_and_preview">
          <div class="crop-area">
            <img
              ref="coverCropImageRef"
              :src="coverCropImageValue"
              v-if="coverCropImageValue"
              class="crop-image"
              alt="cover crop area" />
            <div v-else class="empty_preview compact">选择图片后在这里调整裁剪</div>
          </div>
          <div class="cover_image_preview_box">
            <div>
              <p>16:9尺寸</p>
              <div class="cover_image_preview w16h9"></div>
            </div>
            <div>
              <p>4:3尺寸</p>
              <div class="w4h3_box">
                <div class="cover_image_preview w4h3"></div>
              </div>
            </div>
            <div>
              <p>{{dirtyState.cover ? '裁剪结果(待保存)' : '当前封面'}}</p>
              <img v-if="coverPreviewUrl" class="cover_final_image" :src="coverPreviewUrl" alt="cover preview" />
              <div v-else class="empty_preview">暂无封面预览</div>
            </div>
          </div>
        </div>
      </div>

      <div class="work_info">
        <div class="work_props_box box">
          <el-form :model="workForm" label-width="auto">
            <el-form-item label="作品ID号：">
              <el-input v-model="workForm.id" disabled />
            </el-form-item>
            <el-form-item label="作品标题：" required>
              <el-input v-model="workForm.title" />
            </el-form-item>
            <el-form-item label="作品简述：">
              <el-input v-model="workForm.description" />
            </el-form-item>
            <el-form-item label="作品类型：" required>
              <el-radio-group v-model="workForm.is_claim">
                <el-radio :value="true">自创</el-radio>
                <el-radio :value="false">收录</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="作品作者：" v-if="!workForm.is_claim" required>
              <el-input v-model="workForm.author_nickname" placeholder="请输入该作品的作者在其他主流平台的昵称" />
            </el-form-item>
          </el-form>
        </div>
        <div>
          <div class="select_student_box box">
            <h2 class="small_title">作品归属学生</h2>
            <p class="current_student">当前：{{studentLabel || workForm.student || '未选择'}}</p>
            <div class="select_student_bar">
              <el-input placeholder="学生的中文或英文名" v-model="studentKeyword" @keyup.enter="fetchStudentsByKeyword" />
              <el-button type="primary" @click="fetchStudentsByKeyword" native-type="button">搜索</el-button>
            </div>
            <div class="select_student_item_box" v-if="studentList.length > 0">
              <el-button
                v-for="item in studentList"
                :key="item.id"
                @click="selectStudent(item)"
                :type="workForm.student === item.id ? 'primary' : 'default'">{{item.cn_name}}</el-button>
            </div>
          </div>
          <div class="meta_box box">
            <h2 class="small_title">编辑提示</h2>
            <p>归属显示：{{authorDisplay || '未知'}}</p>
            <p v-if="uploaderNickname">收录者：{{uploaderNickname}}</p>
            <p>分类信息当前编辑链路无法可靠预填/更新，因此本页不提供分类修改。</p>
          </div>
        </div>
      </div>

      <div class="work_content_box box">
        <QuillEditor
          v-model:content="content"
          contentType="html"
          theme="snow"
          :options="editorOptions"
          @update:content="handleEditorChange"
        />
      </div>

      <div class="gallery_box box">
        <div class="gallery_header">
          <h2 class="small_title">图集预览</h2>
          <div>
            <input ref="galleryInputRef" type="file" accept="image/*" multiple @change="handleGalleryChange" />
            <PlusSquareOutlined class="content_image_add" @click="chooseGallery" />
          </div>
        </div>
        <div class="content_image_box">
          <div class="gallery_item" v-for="item in galleryImages" :key="item.id || item.previewUrl">
            <el-image class="gallery_image" :src="item.previewUrl" fit="cover" :preview-src-list="galleryImages.map(image => image.previewUrl)" />
            <el-button class="delete_button" type="danger" size="small" @click="removeRemoteGalleryImage(item)">
              <DeleteOutlined /> 删除
            </el-button>
          </div>
          <div class="gallery_item pending" v-for="(item, index) in newGalleryPreviewUrls" :key="item">
            <el-image class="gallery_image" :src="item" fit="cover" />
            <el-button class="delete_button" type="warning" size="small" @click="removeNewGalleryImage(index)">
              <CloseCircleOutlined /> 移除待传
            </el-button>
          </div>
          <div class="empty_gallery" v-if="galleryImages.length === 0 && newGalleryPreviewUrls.length === 0">还没有图集图片，点右上角加号添加吧。</div>
        </div>
      </div>

      <div class="save_bar box">
        <div class="dirty_tags">
          <el-tag :type="dirtyState.text ? 'warning' : 'info'">文字{{dirtyState.text ? '待保存' : '无修改'}}</el-tag>
          <el-tag :type="dirtyState.cover ? 'warning' : 'info'">封面{{dirtyState.cover ? '待保存' : '无修改'}}</el-tag>
          <el-tag :type="dirtyState.galleryAdditions ? 'warning' : 'info'">新增图{{newGalleryFiles.length}}</el-tag>
          <el-tag :type="dirtyState.galleryDeletions ? 'danger' : 'info'">删除图{{dirtyState.deletedImageIds.length}}</el-tag>
        </div>
        <el-button class="upload-button" type="primary" size="large" native-type="button" :loading="saving" @click="saveAll">
          <SaveOutlined /> 保存修改
        </el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  width: 80%;
  margin: 1rem auto 0 auto;
  gap: 1rem;
}

.title-container {
  position: relative;
  text-align: center;
}

.back-button {
  position: absolute;
  left: 0;
  top: .4rem;
}

.title, .small_title {
  font-weight: bold;
  text-align: center;
  margin-bottom: 0;
}

.subtitle, .hint, .current_student, .meta_box p {
  color: #787878;
}

.work_info {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 2rem;
}

.box {
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  padding: 1rem;
  background: #ffffff;
}

.loading_box {
  text-align: center;
}

.cover_image_box {
  position: relative;
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  padding: 1rem;
  overflow: hidden;
  background: #ffffff;
  .cover_title {
    text-align: center;
    font-weight: bolder;
  }
}

.cover_status_icon {
  position: absolute;
  font-size: 20rem;
  right: -2%;
  bottom: -15%;
  transform: rotatez(-10deg);
}

.success {
  color: #6ff96080;
}

.warning {
  color: rgba(96, 231, 249, 0.7);
}

.input_item_box {
  display: flex;
  align-items: center;
  border-bottom: #9b9b9b solid 1px;
  margin-bottom: 1rem;
  gap: 1rem;
}

input[type=file] {
  display: none;
  opacity: 0;
}

.file-input {
  display: block;
  padding: .4rem .5rem;
  border: none;
  border-radius: 4px;
  background: #00AEEC;
  color: #fff;
  cursor: pointer;
  transition: 0.3s;
}

.file-input:hover {
  background: #54cbff;
}

.crop_and_preview {
  display: flex;
  align-items: end;
  gap: 1rem;
  flex-wrap: wrap;
}

.crop-area {
  position: relative;
  border: 1px dashed #bc4141;
  overflow: hidden;
  width: calc(4 * 5rem);
  height: calc(3 * 5rem);
}

.crop-image {
  max-width: 100%;
  display: block;
}

.cover_image_preview_box {
  display: flex;
  flex-direction: row;
  align-items: end;
  gap: 1rem;
  flex-wrap: wrap;
}

.cover_image_preview {
  overflow: hidden;
}

.w16h9 {
  width: calc(16 * 1rem);
  height: calc(9 * 1rem);
  border: #DE91A9FF 1px solid;
}

.w4h3_box {
  width: calc(4 * 3rem);
  height: calc(3 * 3rem);
  overflow: hidden;
  position: relative;
  border: #9197de 1px solid;
}

.w4h3 {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%) scale(1.31);
  width: calc(4 * 3rem);
  height: calc(3 * 3rem);
}

.cover_final_image, .empty_preview {
  width: calc(16 * 1rem);
  height: calc(9 * 1rem);
  border: #000000 1px solid;
  border-radius: .5rem;
  object-fit: cover;
}

.empty_preview.compact {
  width: 100%;
  height: 100%;
  border: none;
  border-radius: 0;
  text-align: center;
}

.empty_preview, .empty_gallery {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9b9b9b;
  border: #d1d1d1 1px dashed;
}

.select_student_bar {
  margin: 10px auto;
  display: flex;
  width: 70%;
}

.select_student_item_box {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: .5rem;
}

.meta_box {
  margin-top: 1rem;
}

.work_content_box {
  padding: 0;
  overflow: hidden;
  min-height: 18rem;
}

.gallery_header, .save_bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.content_image_add {
  font-size: 3rem;
  color: #00AEEC;
  cursor: pointer;
}

.content_image_box {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 1rem;
}

.gallery_item {
  position: relative;
  width: 8rem;
  .gallery_image {
    width: 8rem;
    height: 8rem;
    border-radius: .5rem;
  }
  .delete_button {
    width: 100%;
    margin-top: .4rem;
  }
}

.gallery_item.pending {
  opacity: .85;
}

.empty_gallery {
  width: 100%;
  min-height: 6rem;
}

.dirty_tags {
  display: flex;
  gap: .5rem;
  flex-wrap: wrap;
}

.upload-button {
  min-width: 10rem;
}
</style>
