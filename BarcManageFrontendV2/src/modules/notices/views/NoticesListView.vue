<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import RoutePageShell from '@/app/components/RoutePageShell.vue'
import { useAuthStore } from '@/app/stores/auth'
import { deleteNotice, fetchNoticesByPage } from '@/modules/notices/api/notices.service'
import { MANAGER_PERMISSION } from '@/shared/constants/permissions'
import { getErrorMessage, type PageResult } from '@/shared/types/api'
import type { NoticeRecord } from '@/shared/types/notice'
import { formatDateTime } from '@/shared/utils/date'
import { sanitizeHtmlContent } from '@/shared/utils/html'
import { showError, showSuccess } from '@/shared/utils/message'

interface NoticeFeedback {
  description: string
  title: string
  tone: 'success' | 'info'
}

const NOTICE_FEEDBACK_KEY = 'barc.notice.feedback'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pagination = reactive({
  page_num: 1,
  page_size: 8,
})

const loading = ref(false)
const pageResult = ref<PageResult<NoticeRecord> | null>(null)
const deletingIds = ref<string[]>([])
const noticeFeedback = ref<NoticeFeedback | null>(null)

const canDelete = computed(() => (authStore.userArchive?.permission ?? 0) >= MANAGER_PERMISSION.ADMINISTRATOR)

function parsePageQuery(rawPage: unknown): number {
  const parsedPage = Number(rawPage)
  return Number.isFinite(parsedPage) && parsedPage > 0 ? parsedPage : 1
}

function currentListQuery(page = pagination.page_num): Record<string, string> {
  return {
    page: String(page),
  }
}

async function syncPageQuery(page = pagination.page_num): Promise<void> {
  await router.replace({
    query: {
      ...route.query,
      page: String(page),
    },
  })
}

async function consumeRouteFeedback(): Promise<void> {
  const action = typeof route.query.noticeAction === 'string' ? route.query.noticeAction : undefined
  if (!action) {
    return
  }

  let nextFeedback: NoticeFeedback | null = null

  if (action === 'created') {
    nextFeedback = {
      tone: 'success',
      title: '公告已发布',
      description: '列表已回到当前页上下文，你可以继续检查新公告是否需要调整。',
    }
  } else if (action === 'updated') {
    nextFeedback = {
      tone: 'info',
      title: '公告已更新',
      description: '列表已经刷新，并保留了你离开时的分页上下文。',
    }
  }

  if (!nextFeedback) {
    return
  }

  noticeFeedback.value = nextFeedback

  if (typeof window !== 'undefined') {
    window.sessionStorage.setItem(NOTICE_FEEDBACK_KEY, JSON.stringify(nextFeedback))
  }

  const nextQuery = { ...route.query }
  delete nextQuery.noticeAction
  await router.replace({ query: nextQuery })
}

function restoreFlashFeedback(): void {
  if (typeof window === 'undefined') {
    return
  }

  const serializedFeedback = window.sessionStorage.getItem(NOTICE_FEEDBACK_KEY)
  if (!serializedFeedback) {
    return
  }

  try {
    noticeFeedback.value = JSON.parse(serializedFeedback) as NoticeFeedback
  } finally {
    window.sessionStorage.removeItem(NOTICE_FEEDBACK_KEY)
  }
}

async function loadNotices(): Promise<void> {
  loading.value = true

  try {
    pageResult.value = await fetchNoticesByPage({
      page_num: pagination.page_num,
      page_size: pagination.page_size,
      params: {},
    })
  } catch (error) {
    showError(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function isDeleting(noticeId: string): boolean {
  return deletingIds.value.includes(noticeId)
}

async function removeNotice(noticeId: string): Promise<void> {
  if (isDeleting(noticeId)) {
    return
  }

  try {
    await ElMessageBox.confirm('删除后将直接影响线上公告展示，是否继续？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })

    deletingIds.value = [...deletingIds.value, noticeId]
    const message = await deleteNotice(noticeId)

    if ((pageResult.value?.list.length ?? 0) === 1 && pagination.page_num > 1) {
      pagination.page_num -= 1
      await syncPageQuery(pagination.page_num)
    }

    showSuccess(message || '公告已删除。')
    await loadNotices()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }

    showError(getErrorMessage(error))
  } finally {
    deletingIds.value = deletingIds.value.filter((id) => id !== noticeId)
  }
}

function handlePageChange(page: number): void {
  pagination.page_num = page
  void syncPageQuery(page)
  void loadNotices()
}

function authorLabel(notice: NoticeRecord): string {
  if (notice.author === authStore.userArchive?.uuid) {
    return '我'
  }

  return notice.author
}

function sanitizedPreview(content: string): string {
  return sanitizeHtmlContent(content)
}

function goToNewNotice(): void {
  void router.push({
    name: 'notices-new',
    query: currentListQuery(),
  })
}

function goToEditNotice(noticeId: string): void {
  void router.push({
    name: 'notices-edit',
    params: { noticeId },
    query: currentListQuery(),
  })
}

onMounted(async () => {
  restoreFlashFeedback()
  pagination.page_num = parsePageQuery(route.query.page)
  await consumeRouteFeedback()
  await syncPageQuery(pagination.page_num)
  await loadNotices()
})
</script>

<template>
  <RoutePageShell
    title="公告列表"
    eyebrow="Notice List"
  >
    <template #actions>
      <el-button plain @click="loadNotices">刷新</el-button>
      <el-button type="primary" @click="goToNewNotice">发布新公告</el-button>
    </template>

    <transition name="fade-slide">
      <el-alert
        v-if="noticeFeedback"
        class="notice-feedback glass-panel"
        :title="noticeFeedback.title"
        :description="noticeFeedback.description"
        :type="noticeFeedback.tone"
        show-icon
        closable
        @close="noticeFeedback = null"
      />
    </transition>

    <section class="notice-grid" v-loading="loading">
      <article v-for="notice in pageResult?.list ?? []" :key="notice.id" class="glass-panel notice-card">
        <div class="notice-card__meta">
          <span>作者：{{ authorLabel(notice) }}</span>
          <span>更新时间：{{ formatDateTime(notice.updated_at) }}</span>
        </div>

        <h2 class="notice-card__title">{{ notice.title }}</h2>

        <div class="notice-card__preview" v-html="sanitizedPreview(notice.content)" />

        <div class="notice-card__actions">
          <el-button plain @click="goToEditNotice(notice.id)">编辑</el-button>
          <el-button v-if="canDelete" type="danger" plain :loading="isDeleting(notice.id)" :disabled="isDeleting(notice.id)" @click="removeNotice(notice.id)">删除</el-button>
        </div>
      </article>

      <el-empty v-if="!loading && (pageResult?.list.length ?? 0) === 0" description="暂无公告" class="glass-panel empty-state" />
    </section>

    <section class="glass-panel pagination-card" v-if="pageResult">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pagination.page_num"
        :page-size="pagination.page_size"
        :total="pageResult.total"
        @current-change="handlePageChange"
      />
    </section>
  </RoutePageShell>
</template>

<style scoped>
.notice-feedback {
  margin-bottom: 1rem;
}

.notice-grid {
  display: grid;
  gap: 1rem;
}

.notice-card,
.pagination-card,
.empty-state {
  padding: 1.25rem;
}

.notice-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.notice-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  color: var(--barc-text-soft);
  font-size: 0.92rem;
}

.notice-card__title {
  margin: 0;
  font-family: var(--barc-font-display);
  font-size: 1.5rem;
}

.notice-card__preview {
  max-height: 7.4rem;
  overflow: hidden;
  color: var(--barc-text-soft);
  line-height: 1.8;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 1) 70%, rgba(0, 0, 0, 0.18) 100%);
}

.notice-card__preview :deep(p) {
  margin: 0.25rem 0;
}

.notice-card__actions,
.pagination-card {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.pagination-card {
  justify-content: center;
}
</style>
