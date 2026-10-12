<template>
  <div class="work-comment-manage" v-loading="loading">
    <div class="section-header">
      <h3>主评论 ({{ totalCount }})</h3>
    </div>

    <div v-if="comments.length === 0 && !loading" class="empty-state">
      暂无评论
    </div>

    <div v-for="comment in comments" :key="comment.id" class="comment-card">
      <!-- 主评论 -->
      <div class="comment-main">
        <div class="comment-avatar">
          <img v-if="comment.authorAvatar" :src="comment.authorAvatar" class="avatar-img" />
          <span v-else>{{ getInitial(comment.authorNickname) }}</span>
        </div>
        <div class="comment-body">
          <div class="comment-meta">
            <el-tooltip :content="comment.authorNickname" placement="top" :disabled="comment.authorNickname.length <= 10">
              <span class="nickname">{{ truncateNick(comment.authorNickname) }}</span>
            </el-tooltip>
            <span class="time">{{ formatTime(comment.created_at) }}</span>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div v-if="canDelete(comment.author)" class="comment-actions">
            <el-popconfirm
              title="确定要删除这条评论及其所有回复吗？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              @confirm="handleDeleteComment(comment.id)"
            >
              <template #reference>
                <el-button type="danger" size="small" text>删除评论</el-button>
              </template>
            </el-popconfirm>
          </div>

          <span>回复 {{ comment.reply_count }}</span>
          <!-- 子回复 -->
          <div v-if="comment.replies && comment.replies.length > 0" class="reply-section">
            <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
              <div class="comment-avatar small">
                <img v-if="reply.authorAvatar" :src="reply.authorAvatar" class="avatar-img" />
                <span v-else>{{ getInitial(reply.authorNickname) }}</span>
              </div>
              <div class="comment-body">
                <div class="comment-meta">
                  <el-tooltip :content="reply.authorNickname" placement="top" :disabled="reply.authorNickname.length <= 10">
                    <span class="nickname">{{ truncateNick(reply.authorNickname) }}</span>
                  </el-tooltip>
                  <span v-if="reply.replyUserNickname" class="reply-to">
                    <el-tooltip :content="reply.replyUserNickname" placement="top" :disabled="reply.replyUserNickname.length <= 10">
                      @{{ truncateNick(reply.replyUserNickname) }}
                    </el-tooltip>
                  </span>
                  <span class="time">{{ formatTime(reply.created_at) }}</span>
                </div>
                <div class="comment-content">{{ reply.content }}</div>
                <div v-if="canDelete(reply.author)" class="comment-actions">
                  <el-popconfirm
                    title="确定要删除这条回复吗？"
                    confirm-button-text="确定"
                    cancel-button-text="取消"
                    @confirm="handleDeleteReply(reply.id)"
                  >
                    <template #reference>
                      <el-button type="danger" size="small" text>删除</el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
            </div>
          </div>
          <el-button v-if="comment.replies.length < comment.reply_count" :loading="replyLoading.has(comment.id)" @click="loadMoreReplies(comment)">更多回复</el-button>
        </div>
      </div>
    </div>
    <el-pagination v-model:current-page="currentPage" :page-size="10" :total="totalCount" layout="total, prev, pager, next" @current-change="load" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/app/stores/auth'
import {
  getCommentsByWork,
  getManagementReplies,
  deleteWorkComment,
  deleteWorkReply,
  getUserArchive,
  type ManageReplyRecord,
} from '../api/commentManage'

const props = defineProps<{ workId: string }>()

interface CommentDisplay {
  id: string; work_id: string; author: string; content: string; created_at: string
  authorNickname: string; authorAvatar: string | null; replies: ReplyDisplay[]; reply_count: number
}
interface ReplyDisplay extends ManageReplyRecord {
  authorNickname: string; authorAvatar: string | null; replyUserNickname: string | null
}

const loading = ref(false)
const comments = ref<CommentDisplay[]>([])

const totalCount = ref(0)
const currentPage = ref(1)
const replyLoading = ref(new Set<string>())
const auth = useAuthStore()
const moderator = computed(() => {
  const account = auth.userArchive
  const permission = account?.permission || 0
  return account?.identity === 'MANAGER' && permission > 0 && permission <= 63 && ((permission & 2) !== 0 || permission >= 16)
})
// 可查看评论的作品管理员不一定能删除；按钮与后端治理规则保持一致。
const canDelete = (author: string) => moderator.value || auth.userArchive?.uuid === author
let requestSequence = 0

async function enrichReply(reply: ManageReplyRecord): Promise<ReplyDisplay> {
  const [author, target] = await Promise.all([getUserArchive(reply.author), reply.reply_user ? getUserArchive(reply.reply_user) : null])
  return { ...reply, authorNickname: author?.nickname || '未知用户', authorAvatar: author?.avatar || null, replyUserNickname: target?.nickname || null }
}

/** 分页读取并丢弃切换作品前仍在进行的请求，避免旧作品评论覆盖新页面。 */
async function load() {
  if (!props.workId) return
  const sequence = ++requestSequence
  loading.value = true
  try {
    const result = await getCommentsByWork(props.workId, currentPage.value)
    const list = await Promise.all(result.list.map(async comment => {
      const author = await getUserArchive(comment.author)
      return { ...comment, authorNickname: author?.nickname || '未知用户', authorAvatar: author?.avatar || null,
        replies: await Promise.all((comment.replies || []).map(enrichReply)) }
    }))
    if (sequence !== requestSequence) return
    comments.value = list
    totalCount.value = result.total
    if (list.length === 0 && currentPage.value > 1 && result.total > 0) {
      currentPage.value = Math.max(1, Math.ceil(result.total / 10))
      await load()
    }
  } catch { if (sequence === requestSequence) ElMessage.error('评论加载失败') }
  finally { if (sequence === requestSequence) loading.value = false }
}

async function loadMoreReplies(comment: CommentDisplay) {
  if (replyLoading.value.has(comment.id)) return
  replyLoading.value.add(comment.id)
  try {
    const result = await getManagementReplies(comment.id, Math.floor(comment.replies.length / 5) + 1)
    const replies = await Promise.all(result.list.map(enrichReply))
    comment.replies.push(...replies.filter(reply => !comment.replies.some(existing => existing.id === reply.id)))
    comment.reply_count = result.total
  } catch { ElMessage.error('回复加载失败') }
  finally { replyLoading.value.delete(comment.id) }
}

async function handleDeleteComment(commentId: string) {
  try {
    await deleteWorkComment(commentId)
    ElMessage.success('评论已删除')
    await load()
  } catch { ElMessage.error('删除失败') }
}

async function handleDeleteReply(replyId: string) {
  try {
    await deleteWorkReply(replyId)
    ElMessage.success('回复已删除')
    await load()
  } catch { ElMessage.error('删除失败') }
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function getInitial(name: string): string {
  return name?.charAt(0) || '?'
}

function truncateNick(name: string, maxLen = 10): string {
  if (!name) return ''
  return name.length > maxLen ? name.slice(0, maxLen) + '…' : name
}

watch(() => props.workId, () => { currentPage.value = 1; comments.value = []; totalCount.value = 0; requestSequence++; if (props.workId) load() }, { immediate: true })
defineExpose({ load })
</script>

<style scoped>
.work-comment-manage { max-width: 900px; }
.section-header { margin-bottom: 16px; }
.section-header h3 { margin: 0; font-size: 16px; color: #303133; }
.empty-state { text-align: center; color: #999; padding: 40px 0; font-size: 14px; }

.comment-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px; margin-bottom: 12px;
}
.comment-main { display: flex; gap: 12px; }
.comment-avatar {
  width: 36px; height: 36px; border-radius: 50%; background: #409eff20; color: #409eff;
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold; flex-shrink: 0;
  overflow: hidden;
}
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.comment-avatar.small { width: 28px; height: 28px; font-size: 12px; }
.comment-body { flex: 1; min-width: 0; }
.comment-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
.nickname { font-size: 13px; font-weight: 600; color: #409eff; }
.reply-to { font-size: 12px; color: #f56c6c; background: #fef0f0; padding: 0 6px; border-radius: 4px; }
.time { font-size: 12px; color: #c0c4cc; margin-left: auto; }
.comment-content { font-size: 14px; color: #606266; line-height: 1.6; word-break: break-all; }
.comment-actions { margin-top: 6px; }

.reply-section {
  margin-top: 10px; padding: 10px 0 10px 14px; border-left: 2px solid #409eff20;
  background: #fafafa; border-radius: 0 6px 6px 0;
}
.reply-item { display: flex; gap: 8px; margin-bottom: 10px; }
.reply-item:last-child { margin-bottom: 0; }
</style>
