<script setup lang="ts">
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

import type { NoticeDraft } from '@/shared/types/notice'
import { sanitizeHtmlContent } from '@/shared/utils/html'

const props = defineProps<{
  draft: NoticeDraft
  mode: 'create' | 'edit'
  loading?: boolean
  contentStatus: {
    description: string
    label: string
    tone: 'success' | 'warning'
  }
}>()

const emit = defineEmits<{
  (event: 'update:draft', value: NoticeDraft): void
  (event: 'submit'): void
  (event: 'cancel'): void
}>()

const toolbarOptions = {
  modules: {
    toolbar: [
      [{ header: [1, 2, 3, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      ['blockquote', 'code-block'],
      [{ list: 'ordered' }, { list: 'bullet' }, { indent: '-1' }, { indent: '+1' }],
      [{ align: [] }],
      ['link', 'clean'],
    ],
  },
  placeholder: '请写清公告对象、时间安排、影响范围与执行说明…',
  theme: 'snow',
}

function updateDraft<K extends keyof NoticeDraft>(key: K, value: NoticeDraft[K]): void {
  emit('update:draft', {
    ...props.draft,
    [key]: value,
  })
}

function updateContent(content: string): void {
  updateDraft('content', sanitizeHtmlContent(content))
}
</script>

<template>
  <div class="editor-form">
    <section class="glass-panel editor-form__surface">
      <div class="editor-form__title-row">
        <div>
          <div class="editor-form__eyebrow">{{ mode === 'create' ? 'Create' : 'Edit' }}</div>
          <h2 class="editor-form__title">{{ mode === 'create' ? '发布新公告' : '更新公告内容' }}</h2>
        </div>

        <div class="editor-form__actions">
          <el-button @click="emit('cancel')">取消返回</el-button>
          <el-button type="primary" :loading="loading" @click="emit('submit')">
            {{ mode === 'create' ? '确认发布' : '保存修改' }}
          </el-button>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="公告标题">
          <el-input :model-value="draft.title" maxlength="120" show-word-limit @update:model-value="updateDraft('title', $event)" />
        </el-form-item>
      </el-form>
    </section>

    <section class="glass-panel editor-form__surface editor-form__surface--editor">
      <div class="editor-form__editor-shell">
        <div class="editor-form__editor-intro">
          <section class="editor-form__guidance" data-testid="editor-guidance">
            <div class="editor-form__eyebrow">Editor guidance</div>
            <h3 class="editor-form__section-title">安全富文本正文</h3>
            <p class="editor-form__section-description">
              支持标题、强调、引用、代码块、列表、缩进、对齐与链接。提交前会自动清洗富文本，只保留后端当前安全支持的格式。
            </p>

            <ul class="editor-form__guidance-list">
              <li>建议先写清公告对象、执行时间、影响范围与需要完成的动作。</li>
              <li>从其他地方粘贴的内容也会被清洗，脚本、危险链接和嵌入不会保留。</li>
              <li>如需强调重点，优先使用标题、加粗、列表、引用或链接，而不是复杂样式。</li>
            </ul>
          </section>

          <div
            class="editor-form__status"
            :class="`editor-form__status--${props.contentStatus.tone}`"
            data-testid="editor-content-status"
            role="status"
            aria-live="polite"
          >
            <span class="editor-form__status-label">{{ props.contentStatus.label }}</span>
            <span class="editor-form__status-description">{{ props.contentStatus.description }}</span>
          </div>
        </div>

      <QuillEditor
        class="editor-form__editor"
        content-type="html"
        theme="snow"
        :content="draft.content"
        :options="toolbarOptions"
        @update:content="updateContent"
      />
      </div>
    </section>
  </div>
</template>

<style scoped>
.editor-form {
  display: flex;
  flex-direction: column;
  gap: var(--barc-space-4);
}

.editor-form__surface {
  padding: var(--barc-space-5);
}

.editor-form__surface--editor {
  display: flex;
  flex-direction: column;
  gap: var(--barc-space-4);
  min-height: 460px;
}

.editor-form__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--barc-space-4);
  margin-bottom: var(--barc-space-4);
}

.editor-form__eyebrow {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.editor-form__title {
  margin: var(--barc-space-1) 0 0;
  font-family: var(--barc-font-display);
}

.editor-form__actions {
  display: flex;
  gap: var(--barc-space-3);
}

.editor-form__editor-shell {
  display: flex;
  min-height: 100%;
  flex: 1;
  flex-direction: column;
  gap: var(--barc-space-4);
}

.editor-form__editor-intro {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--barc-space-4);
}

.editor-form__guidance {
  display: grid;
  gap: var(--barc-space-2);
  max-width: 42rem;
}

.editor-form__section-title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: 1.2rem;
}

.editor-form__section-description {
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.8;
}

.editor-form__guidance-list {
  display: grid;
  gap: var(--barc-space-2);
  margin: 0;
  padding-left: 1.2rem;
  color: var(--barc-text-soft);
  line-height: 1.8;
}

.editor-form__status {
  display: grid;
  min-width: 16rem;
  gap: var(--barc-space-1);
  padding: var(--barc-space-3) var(--barc-space-4);
  border: 1px solid var(--barc-border);
  border-radius: var(--barc-radius-md);
  background:
    radial-gradient(circle at top right, var(--barc-bg-accent), transparent 48%),
    linear-gradient(160deg, var(--barc-surface-strong), var(--barc-surface));
  box-shadow: var(--barc-shadow-sm);
}

.editor-form__status--warning {
  box-shadow:
    inset 4px 0 0 var(--barc-danger),
    var(--barc-shadow-sm);
}

.editor-form__status--success {
  box-shadow:
    inset 4px 0 0 var(--barc-success),
    var(--barc-shadow-sm);
}

.editor-form__status-label {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.editor-form__status-description {
  color: var(--barc-text-soft);
  line-height: 1.6;
}

.editor-form__editor {
  min-height: 0;
  flex: 1;
}

.editor-form__editor :deep(.ql-toolbar.ql-snow) {
  border: 1px solid var(--barc-border);
  border-top-left-radius: var(--barc-radius-sm);
  border-top-right-radius: var(--barc-radius-sm);
  background: var(--barc-surface-strong);
}

.editor-form__editor :deep(.ql-container.ql-snow) {
  overflow: hidden;
  border: 1px solid var(--barc-border);
  border-top: none;
  border-bottom-right-radius: var(--barc-radius-md);
  border-bottom-left-radius: var(--barc-radius-md);
}

.editor-form__editor :deep(.ql-editor) {
  min-height: 320px;
  background: var(--barc-surface-strong);
  color: var(--barc-text);
  line-height: 1.8;
}

@media (max-width: 900px) {
  .editor-form__title-row {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-form__actions {
    justify-content: flex-end;
  }

  .editor-form__editor-intro {
    flex-direction: column;
  }

  .editor-form__status {
    min-width: 0;
    width: 100%;
  }
}
</style>
