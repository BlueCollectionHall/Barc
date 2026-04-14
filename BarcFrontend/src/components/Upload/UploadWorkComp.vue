<script setup lang="ts">
import {nextTick, onBeforeMount, onMounted, ref, watch} from "vue";
import type {WorkImpl} from "@/interfaces/WorkImpl.ts";
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  QuestionCircleOutlined
} from "@ant-design/icons-vue";

// 生命周期钩子相关
onMounted(() => {
  fetchWorkCategories();
})

// 封面图相关
import Cropper from 'cropperjs';
import 'cropperjs/dist/cropper.css';
import {errorMessage, infoMessage, successMessage} from "@/utils/MessageAlert.ts";
import {useUserPinia} from "@/stores/UserPinia.ts";
import {storeToRefs} from "pinia";
import {useRoute} from "vue-router";
// 封面图相关 - 响应式变量
const coverStatus = ref<number>(0);
const cropImageValue = ref<string | null>(null);
const croppedImageBase64Value = ref<string | null>(null);
const croppedImageFileValue = ref<FormData | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);
const imageRef = ref<HTMLImageElement | null>(null);
let cropper: Cropper | null = null;
// 封面图相关 - 处理图片选择
const selectCoverImageFile = (e: Event) => {
  cropImageValue.value = null;
  const {files} = e.target as HTMLInputElement;
  if (!files || !files.length) return;
  const file = files[0];
  const reader = new FileReader();
  reader.readAsDataURL(file);
  reader.onloadend = () => {
    cropImageValue.value = String(reader.result);
    if (inputRef.value) inputRef.value.value = "";
    nextTick(() => {initCropper()});
  }
};
// 封面图相关 - 初始化裁剪器
const initCropper = () => {
  if (!imageRef.value) return;
  // 销毁旧实例
  if (cropper) {
    cropper.destroy();
  }
  // 创建新实例
  cropper = new Cropper(imageRef.value as HTMLImageElement, {
    aspectRatio: 16 / 9,  // 16:9 比例
    viewMode: 1,          // 限制裁剪框在图片内
    dragMode: 'move',     // 允许移动图片
    guides: true,         // 显示辅助线
    background: false,    // 禁用背景
    autoCropArea: 0.8,    // 初始裁剪区域占比
    preview: ".cover_image_preview",  // 预览图
  });
};
// 获取裁剪结果
const getCroppedImage = () => {
  if (!cropper) {
    errorMessage("图片加载失败");
    coverStatus.value = 2;
    return;
  }
  const canvas = cropper.getCroppedCanvas({
    width: 640,         // 输出宽度（16:9对应高度为1080）
    height: 360,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: "high"
  });
  canvas?.toBlob((blob: Blob | null) => {
    if (!blob) {
      errorMessage("动态裁剪失败");
      coverStatus.value = 2;
      return;
    }
    const formData = new FormData();
    formData.append("cover_image", blob, "123");
    croppedImageFileValue.value = formData;
    croppedImageBase64Value.value = URL.createObjectURL(blob);
    coverStatus.value = 1;
    successMessage("已成功选择封面图");
  });
};

const userPinia = useUserPinia();
// const router = useRouter();
const route = useRoute();

const {userBasic} = storeToRefs(userPinia);
const workForm = ref<WorkImpl>({
  id: "", author: "", author_nickname: null, banner_image: "", cover_image: "", content: null, description: null, is_claim: false, status: "PUBLIC", title: "", view_count: 0, like_count: 0, student: "", uploader: null, created_at: new Date(), updated_at: new Date()
});

// onMounted(() => {
//   const username: string = route.query.username as string;
//   if (username !== userBasic.value?.username) {
//     errorMessage("路径与已登录信息不匹配");
//     router.back();
//     return;
//   }
// })

// 获取所有作品顶级分类
const workCategories = ref<Array<CategoryImpl>>([]);
const selectCategory = ref<string | null>(null);

const fetchWorkCategories = async () => {
  await baseHttp.get("/api/category/all_top_categories")
    .then(res => {
      const data: ResponseImpl = res.data;
      if (data.code === 0) {
        workCategories.value = data.data;
      } else errorMessage("获取所有分类信息失败！网络问题？请刷新界面！");
    }).catch(err => {
      console.error(err);
      errorMessage("网络错误！");
    });
}

// 获取作品所属学生ID
const studentList = ref<Array<StudentImpl>>([]);
const studentKeyword = ref<string | null>(null);

const fetchStudentsByKeyword = async () => {
  if (!studentKeyword.value || studentKeyword.value.length === 0) {
    errorMessage("请正确输入学生名字！");
    return;
  }
  try {
    const response = await baseHttp("/api/student/students_by_keyword", {params: {keyword: studentKeyword.value}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      studentList.value = data.data;
    } else infoMessage(data.msg);
  } catch {
    errorMessage("网络错误！");
  }
}

watch(() => workForm.value.is_claim, (newVal: boolean) => {
  if (userBasic.value === null) {
    errorMessage("未登录禁止上传作品");
    return;
  }
  if (newVal) {
    workForm.value.author = userBasic.value.uuid;
  } else {
    workForm.value.uploader = userBasic.value.uuid;
  }
});
watch(() => workForm.value.content, newVal => {
  sm.value = newVal;
})
const sm = ref<string | null>(null);

// 富文本编辑器
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';
// 编辑器内容（双向绑定）
const content = ref('<p></p>')

// 编辑器配置
const editorOptions = ref({
  modules: {
    toolbar: [
      ['bold', 'italic', 'underline'],
      ['blockquote', 'code-block'],
      // [{ list: 'ordered' }, { list: 'bullet' }],
      // [{ align: [] }],
      // ['link', 'image']
    ]
  },
  placeholder: '请输入内容...',
  theme: 'snow'
})

// 内容变化处理
const handleEditorChange = (html: string) => {
  // 过滤危险标签（可选）
  workForm.value.content = html.replace(/<script.*?>.*?<\/script>/gis, '')
}

// 图片上传处理（示例）
// const uploadImage = async (file: File) => {
//   const formData = new FormData()
//   formData.append('file', file)
//
//   // 调用上传API（示例）
//   const res = await fetch('/api/upload', {
//     method: 'POST',
//     body: formData
//   })
//
//   return (await res.json()).url
// }
// 内容图片
import {
  PlusSquareOutlined,
  LeftOutlined,
  RightOutlined,
  DoubleRightOutlined,
  PlusOutlined,
  MinusOutlined,
  RedoOutlined,
  DeleteOutlined,
} from "@ant-design/icons-vue";
import type {ResponseImpl} from "@/interfaces/ResponseImpl.ts";
import {baseHttp} from "@/utils/https.ts";
import type {StudentImpl} from "@/interfaces/BaImpl.ts";
import type {CategoryImpl} from "@/interfaces/CategoryImpl.ts";
const contentImageInputRef = ref<HTMLInputElement | null>(null);

const contentImageFileList = ref<Array<File>>([]);
const contentImagePreviewList = ref<Array<string>>([]);

const contentImageInputClicked = () => {
  if (!contentImageInputRef.value) return;
  contentImageInputRef.value.click();
}

const handleContentImageChange = (e: Event) => {
  const input: HTMLInputElement = e.target as HTMLInputElement;
  if (!input.files) return;
  const files = Array.from(input.files);
  // 生成预览URL
  // const newImages = files.map(file => ({file, previewURL: URL.createObjectURL(file)}));
  contentImageFileList.value = [...contentImageFileList.value, ...files];
  // 将数组写入ref数据
  // contentImageFileList.value = files;
  contentImagePreviewList.value = contentImageFileList.value.map((file: File) => URL.createObjectURL(file));
}

const deleteContentImageByIndex = (index: number) => {
  contentImageFileList.value.splice(index, 1);
  contentImagePreviewList.value.splice(index, 1);
  successMessage("删除成功");
}

// 上传文章内容
const uploadWorkForm = async () => {
  if (userBasic.value === null) {
    errorMessage("您尚未登录！");
    return;
  }
  if (route.query.username !== userBasic.value.username) {
    errorMessage("登录信息与路径不匹配！");
    return;
  }
  if (croppedImageFileValue.value === null) {
    errorMessage("未设置封面图！");
    return;
  }
  if (workForm.value.student.length === 0) {
    errorMessage("未选择作品归属的学生！");
    return;
  }
  if (workForm.value.title.length === 0) {
    errorMessage("未设置标题！");
    return;
  }
  if (!workForm.value.is_claim && (workForm.value.author_nickname === null || workForm.value.author_nickname.length === 0)) {
    errorMessage("[收录]类型需要说明来源作者！");
    return;
  }
  // 创建FormData对象
  const formData = new FormData();
  // 添加JSON表单数据
  formData.append(
    "form",
    JSON.stringify({
      ...workForm.value,
      content: content.value // 确保富文本内容被包含
    })
  );
  // 添加分类信息
  if (selectCategory.value && selectCategory.value.length > 0) {
    formData.append(
      "category_id",
      selectCategory.value
    );
  }
  // 添加封面图（从裁切结果获取）
  const coverImageBlob = croppedImageFileValue.value.get("cover_image") as Blob;
  if (coverImageBlob) {
    formData.append("cover_image", coverImageBlob);
  }
  // 添加内容图片
  contentImageFileList.value.forEach(file => {
    formData.append("files", file);
  });
  try {
    const response = await baseHttp("/api/work/upload", {
      headers: {Authorization: window.localStorage.getItem("token")},
      method: "POST",
      data: formData,
    })
    const data: ResponseImpl = response.data;
    successMessage(data.msg);
  } catch {
    errorMessage("网络错误！")
  }
}
</script>

<template>
  <div class="container">
    <div class="title-container">
      <h1 class="title">发布/收录作品</h1>
    </div>
    <div class="cover_image_box">
      <QuestionCircleOutlined v-if="coverStatus === 0" class="cover_status_icon warning" />
      <CheckCircleOutlined v-else-if="coverStatus === 1" class="cover_status_icon success" />
      <CloseCircleOutlined v-else-if="coverStatus === 2" class="cover_status_icon failed" />

      <h2 class="cover_title">上传封面</h2>
      <!-- 图片上传 -->
      <div class="input_item_box">
        <input
          type="file"
          accept="image/*"
          @change="selectCoverImageFile"
          id="file-input"/>
        <label class="file-input" for="file-input">
          上传图片
        </label>
        <el-button type="success" @click="getCroppedImage">确认使用</el-button>
      </div>
      <div class="crop_and_preview">
        <!-- 裁剪区域 -->
        <div class="crop-area">
          <img
            ref="imageRef"
            :src="cropImageValue"
            v-if="cropImageValue"
            class="crop-image"
            alt="crop-area"/>
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
          <div v-if=croppedImageBase64Value>
            <p>最终图片(16:9)</p>
            <img class="cover_final_image" :src="croppedImageBase64Value" alt="cover_final_image" />
          </div>
        </div>

      </div>
      <!-- 裁剪结果 -->
<!--      <button @click="getCroppedImage">保存图片</button>-->
<!--      <div v-if="croppedImageBase64Value" class="result">-->
<!--        <h3>裁剪结果：</h3>-->
<!--        <img :src="croppedImageBase64Value" class="cropped-img">-->
<!--        <button @click="getCroppedImage">保存图片</button>-->
<!--      </div>-->
    </div>
    <div class="work_info">
      <div class="work_props_box box">
        <el-form :model="workForm" label-width="auto">
          <el-form-item label="作品ID号：">
            <el-input v-model="workForm.id" placeholder="您可以自定义ID号，但如果已被占用，将会被打回！"/>
          </el-form-item>
          <el-form-item label="作品标题：" required>
            <el-input v-model="workForm.title"/>
          </el-form-item>
          <el-form-item label="作品简述：">
            <el-input v-model="workForm.description"/>
          </el-form-item>
          <el-form-item label="作品类型：" required>
            <el-radio-group v-model="workForm.is_claim">
              <el-radio :value="true">自创</el-radio>
              <el-radio :value="false">收录</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="作品作者：" v-if="!workForm.is_claim" required>
            <el-input v-model="workForm.author_nickname" placeholder="请输入该作品的作者在其他主流平台的昵称"/>
          </el-form-item>
        </el-form>
      </div>
      <div>
        <div class="select_student_box box">
          <h2 class="title">选择作品所属学生</h2>
          <div class="select_student_bar">
            <el-input placeholder="学生的中文或英文名" v-model="studentKeyword"/>
            <el-button type="primary" @click="fetchStudentsByKeyword" native-type="button">搜索</el-button>
          </div>
          <div class="select_student_item_box" v-if="studentList.length > 0">
            <el-button
              v-for="item in studentList"
              :key="item.id"
              @click="workForm.student = item.id"
              :type="workForm.student === item.id? 'primary': 'default'">{{item.cn_name}}</el-button>
          </div>
        </div>
        <div class="select_category_box box">
          <h2 class="title">选择作品所属分类</h2>
          <div class="select_category_item_box" v-if="workCategories.length > 0">
            <el-button @click="selectCategory = null" :type="selectCategory? 'default': 'primary'">无分类</el-button>
            <el-button
              v-for="item in workCategories"
              :key="item.id"
              @click="selectCategory = item.id"
              :type="selectCategory === item.id? 'primary': 'default'">{{item.name}}</el-button>
          </div>
          <div v-else>
            分类不存在
          </div>
        </div>
      </div>
    </div>

    <div class="work_info_box">

      <div class="work_content_box box">
        <div class="ql-container">
          <QuillEditor
            v-model:content="content"
            contentType="html"
            theme="snow"
            :options="editorOptions"
            @update:content="handleEditorChange"
          />
        </div>

        <div class="content_image_box">
          <el-image
            style="width: 100px; height: 100px"
            v-for="(item, index) in contentImagePreviewList"
            :key="index"
            :src="item"
            :initial-index="index"
            show-progress
            fit="cover"
            :preview-src-list="contentImagePreviewList"
          >
            <template
              #toolbar="{ actions, prev, next, setActiveItem }"
            >
              <el-icon @click="prev"><LeftOutlined /></el-icon>
              <el-icon @click="next"><RightOutlined /></el-icon>
              <el-icon @click="setActiveItem(contentImagePreviewList.length - 1)">
                <DoubleRightOutlined />
              </el-icon>
              <el-icon @click="actions('zoomOut')"><MinusOutlined /></el-icon>
              <el-icon
                @click="actions('zoomIn', { enableTransition: false, zoomRate: 1 })"
              >
                <PlusOutlined />
              </el-icon>
              <el-icon
                @click="actions('clockwise')">
                <RedoOutlined />
              </el-icon>
              <el-icon @click="deleteContentImageByIndex(index)"><DeleteOutlined /></el-icon>
            </template>
          </el-image>
          <input type="file" accept="image/*" @change="handleContentImageChange" ref="contentImageInputRef" style="display: none" multiple/>
          <PlusSquareOutlined class="content_image_add" @click="contentImageInputClicked" />
        </div>
      </div>
    </div>
    <el-button class="upload-button" type="primary" size="large" native-type="button" @click="uploadWorkForm">上传作品文章</el-button>
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
  text-align: center;
}

.title {
  font-weight: bold;
  text-align: center;
  margin-bottom: 0;
}

.work_info {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 2rem;
}
.box {
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  padding: 1rem;
}
.cover_image_box {
  position: relative;
  box-shadow: 0 0 0.1rem 0.1rem rgb(0 0 0 / 10%);
  padding: 1rem;
  overflow: hidden;
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
.failed {
  color: rgba(249, 96, 96, 0.7);
}
.warning {
  color: rgba(96, 231, 249, 0.7);
}

.select_student_bar {
  margin: 10px auto;
  display: flex;
  width: 50%;
}

.input_item_box {
  display: flex;
  align-items: center;
  /*justify-content: center;*/
  border-bottom: #9b9b9b solid 1px;
  margin-bottom: 1rem;
}
input[type=file] {
  display: none;
  opacity: 0;
}
.file-input {
  display: block;
  padding: .4rem .5rem;
  margin-right: 1rem;
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

.result {
  margin-top: 30px;
  text-align: center;
}

.cropped-img {
  max-width: 100%;
  border: 1px solid #eee;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
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
.cover_final_image {
  width: calc(16 * 1rem);
  height: calc(9 * 1rem);
  border: #000000 1px solid;
}

.work_content_box {
  padding: 0;
  overflow: hidden;
}
.ql-container {
  height: 100%;
}

.content_image_add {
  font-size: 3rem;
  color: #00AEEC;
}

.upload-button {
  margin-bottom: 2rem;
}
</style>
