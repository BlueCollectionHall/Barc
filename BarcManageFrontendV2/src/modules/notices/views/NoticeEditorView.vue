<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import { createNotice, fetchNotice, updateNotice } from '@/modules/notices/api/notices.service'
import { getErrorMessage } from '@/shared/types/api'
import type { NoticeDraft, NoticeRecord } from '@/shared/types/notice'
import { sanitizeHtmlContent } from '@/shared/utils/html'
import { showError, showSuccess } from '@/shared/utils/message'

type NoticeAction = 'created' | 'updated'

interface NoticeEditorContentStatus {
  description: string
  label: string
  tone: 'success' | 'warning'
}

const NoticeEditorForm = defineAsyncComponent(() => import('@/modules/notices/components/NoticeEditorForm.vue'))

const route = useRoute()
const router = useRouter()

const noticeId = computed(() => {
  const rawValue = route.params.noticeId
  return typeof rawValue === 'string' ? rawValue : undefined
})
const listPageQuery = computed(() => {
  const rawPage = route.query.page
  return typeof rawPage === 'string' && rawPage.trim().length > 0 ? rawPage : undefined
})
const mode = computed<'create' | 'edit'>(() => (noticeId.value ? 'edit' : 'create'))
const hydrating = ref(false)
const submitting = ref(false)
const allowSilentNavigation = ref(false)
const draft = ref<NoticeDraft>({
  title: '',
  content: '',
})
const initialDraft = ref<NoticeDraft>({
  title: '',
  content: '',
})
const loadedNotice = ref<NoticeRecord | null>(null)

const isBusy = computed(() => hydrating.value || submitting.value)
const isDirty = computed(() => {
  const currentTitle = draft.value.title.trim()
  const initialTitle = initialDraft.value.title.trim()
  const currentContent = sanitizeHtmlContent(draft.value.content)
  const initialContent = sanitizeHtmlContent(initialDraft.value.content)

  return currentTitle !== initialTitle || currentContent !== initialContent
})
const contentStatus = computed<NoticeEditorContentStatus>(() => {
  if (isVisuallyEmptyRichText(draft.value.content)) {
    return {
      tone: 'warning',
      label: '正文待补充',
      description: '当前正文在清洗后仍会视为空内容，提交前请补充可见文字、列表或引用。',
    }
  }

  return {
    tone: 'success',
    label: '正文已就绪',
    description: '已检测到可见正文；提交时仍会继续按后端安全规则清洗。',
  }
})

function buildListQuery(action?: NoticeAction): Record<string, string> {
  return {
    ...(listPageQuery.value ? { page: listPageQuery.value } : {}),
    ...(action ? { noticeAction: action } : {}),
  }
}

function isVisuallyEmptyRichText(content: string): boolean {
  const sanitized = sanitizeHtmlContent(content)
  if (!sanitized.trim()) {
    return true
  }

  if (typeof DOMParser === 'undefined') {
    return sanitized.replace(/<[^>]*>/g, '').replace(/&nbsp;/gi, '').trim().length === 0
  }

  const documentFragment = new DOMParser().parseFromString(sanitized, 'text/html')
  const textContent = documentFragment.body.textContent?.replace(/\u00a0/g, ' ').trim() ?? ''
  const hasRichMedia = Boolean(documentFragment.body.querySelector('img,video,iframe,embed,object,canvas,svg'))

  return textContent.length === 0 && !hasRichMedia
}

function syncInitialDraft(nextDraft: NoticeDraft): void {
  initialDraft.value = {
    id: nextDraft.id,
    title: nextDraft.title,
    content: nextDraft.content,
    author: nextDraft.author,
  }
}

async function navigateBackToList(action?: NoticeAction): Promise<void> {
  allowSilentNavigation.value = true

  try {
    await router.replace({
      name: 'notices-list',
      query: buildListQuery(action),
    })
  } finally {
    allowSilentNavigation.value = false
  }
}

async function confirmDiscardIfNeeded(): Promise<boolean> {
  if (allowSilentNavigation.value || submitting.value || !isDirty.value) {
    return true
  }

  try {
    await ElMessageBox.confirm('当前公告还有未保存的修改，确定要离开吗？', '放弃公告编辑', {
      type: 'warning',
      distinguishCancelAndClose: true,
      confirmButtonText: '离开页面',
      cancelButtonText: '继续编辑',
    })
    return true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }

    showError(getErrorMessage(error))
    return false
  }
}

async function loadNotice(): Promise<void> {
  if (!noticeId.value) {
    syncInitialDraft(draft.value)
    return
  }

  hydrating.value = true

  try {
    loadedNotice.value = await fetchNotice(noticeId.value)
    draft.value = {
      id: loadedNotice.value.id,
      title: loadedNotice.value.title,
      content: sanitizeHtmlContent(loadedNotice.value.content),
      author: loadedNotice.value.author,
    }
    syncInitialDraft(draft.value)
  } catch (error) {
    showError(getErrorMessage(error))
    await navigateBackToList()
  } finally {
    hydrating.value = false
  }
}

async function submit(): Promise<void> {
  if (isBusy.value) {
    return
  }

  if (!draft.value.title.trim()) {
    showError('公告标题不能为空。')
    return
  }

  if (isVisuallyEmptyRichText(draft.value.content)) {
    showError('公告内容不能为空。')
    return
  }

  submitting.value = true

  try {
    const normalizedContent = sanitizeHtmlContent(draft.value.content)

    if (mode.value === 'create') {
      const message = await createNotice({
        title: draft.value.title.trim(),
        content: normalizedContent,
      })
      showSuccess(message || '公告已发布。')
      await navigateBackToList('created')
      return
    }

    if (loadedNotice.value) {
      const message = await updateNotice({
        ...loadedNotice.value,
        title: draft.value.title.trim(),
        content: normalizedContent,
      })
      showSuccess(message || '公告已更新。')
      await navigateBackToList('updated')
    }
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

async function handleCancel(): Promise<void> {
  if (await confirmDiscardIfNeeded()) {
    await navigateBackToList()
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent): string | void {
  if (allowSilentNavigation.value || submitting.value || !isDirty.value) {
    return undefined
  }

  event.preventDefault()
  event.returnValue = ''
  return ''
}

onBeforeRouteLeave(async () => {
  return confirmDiscardIfNeeded()
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void loadNotice()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <RoutePageShell
    eyebrow="Notices"
    :title="mode === 'create' ? '发布公告' : '编辑公告'"
    :subtitle="mode === 'create' ? '新公告会沿用统一富文本编辑器。' : '作者本人或更高权限管理员可修改已有公告。'"
  >
    <section v-if="mode === 'edit' && hydrating" data-testid="editor-loading" class="glass-panel loading-state">
      <div class="loading-state__eyebrow">Loading</div>
      <h2 class="loading-state__title">正在加载公告内容</h2>
      <p class="loading-state__description">在编辑数据完全到位前，表单会保持锁定，避免你在旧内容上误操作。</p>
    </section>

    <NoticeEditorForm
      v-else
      :draft="draft"
      :mode="mode"
      :loading="submitting"
      :content-status="contentStatus"
      @update:draft="draft = $event"
      @submit="submit"
      @cancel="handleCancel"
    />
  </RoutePageShell>
</template>

<style scoped>
.loading-state {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  justify-content: center;
  gap: var(--barc-space-3);
  padding: var(--barc-space-6);
}

.loading-state__eyebrow {
  color: var(--barc-accent-strong);
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.loading-state__title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: 1.6rem;
}

.loading-state__description {
  max-width: 36rem;
  margin: 0;
  color: var(--barc-text-soft);
  line-height: 1.8;
}
</style>
