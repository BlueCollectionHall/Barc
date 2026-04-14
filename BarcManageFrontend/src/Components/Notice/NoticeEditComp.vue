<script setup lang="ts">
import "@/Styles/ContainerAni.css";
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';
import {onMounted, ref, watch} from "vue";
import type {NoticeImpl} from "@/Interfaces/NoticeImpl.ts";
import {baseHttp} from "@/Utils/Request.ts";
import {errorMessage, successMessage, warningMessage} from "@/Utils/MessageAlert.ts";
import type {ResponseImpl} from "@/Interfaces/ResponseImpl.ts";
import {useRouter, useRoute} from "vue-router";

const router = useRouter();
const route = useRoute();

const noticeForm = ref<NoticeImpl | null>(null);

// 编辑器配置
const editorOptions = ref({
  modules: {
    toolbar: [
      ['bold', 'italic', 'underline'],
      ['blockquote', 'code-block'],
      // [{ list: 'ordered' }, { list: 'bullet' }],
      [{ align: [] }],
      // ['link', 'image']
    ]
  },
  placeholder: '请输入内容...',
  theme: 'snow'
})

// 内容变化处理
const handleEditorChange = (html: string) => {
  console.log(html);
  // 过滤危险标签（可选）
  if (noticeForm.value) {
    noticeForm.value.content = html.replace(/<script.*?>.*?<\/script>/gis, '');
  }
}

// 获取公告内容
const fetchNotice = async (notice_id: string) => {
  try {
    const response = await baseHttp.get("/notice/only", {params: {notice_id}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      noticeForm.value = data.data;
    } else warningMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

// 修改公告
const updateNotice = async () => {
  if (!noticeForm.value) {
    errorMessage("公告信息不存在");
    return;
  }
  try {
    const response = await baseHttp.put("/notice/update", noticeForm.value, {headers: {Authorization: localStorage.getItem("token")}});
    const data: ResponseImpl = response.data;
    if (data.code === 0) {
      successMessage(data.data);
      router.back();
    }
    else warningMessage(data.data);
  } catch (error) {
    console.error(error);
    errorMessage("网络异常");
  }
}

onMounted(() => {
  const noticeId = route.query.notice_id as string | null | undefined;
  if (noticeId) {
    fetchNotice(noticeId);
  } else {
    errorMessage("公告ID参数不存在！");
    router.back();
  }
})
</script>

<template>
  <div class="container" v-if="noticeForm">
    <div class="title">
      <h1>发布新公告</h1>
    </div>
    <div class="title_input">
      <span>公告标题</span>
      <el-input v-model="noticeForm.title" placeholder="请填写标题"/>
      <div class="buttons">
        <el-button type="primary" native-type="button" @click="updateNotice">确认修改</el-button>
        <el-button type="warning" >取消返回</el-button>
      </div>
    </div>
    <div class="ql-container">
      <QuillEditor
          class="ql-editor"
          v-model:content="noticeForm.content"
          contentType="html"
          theme="snow"
          :options="editorOptions"
          @update:content="handleEditorChange"
      />
    </div>
  </div>
  <div class="container" v-else>加载中…</div>
</template>

<style scoped>
.container {
  animation: container_in 1s ease forwards;
  background-color: rgba(84, 92, 100, .5);
  backdrop-filter: blur(5px);
  box-shadow: white 0 0 5px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  .title {
    background-color: rgba(255, 255, 255, .5);
    padding: 0 1rem;
    text-align: center;

  }
  .title_input {
    color: rgba(255, 255, 255, 1);
    text-shadow: #deefff 0 0 5px;
    padding: .1rem 1rem;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 1rem;
    .buttons {
      display: flex;
      align-items: center;
      text-align: center;
    }
    span {
      text-align: center;
      min-width: 10rem;
    }
  }
  .ql-container {
    background-color: rgba(255, 255, 255, .5);
  }

}
:deep(.ql-editor) {
  background-color: rgba(255, 255, 255, 1);
  height: 30rem;
  padding: 0 8px;
}
</style>