<template>
  <div class="comment-complaints-view">
    <div class="section-header">
      <h3>评论投诉处理</h3>
      <span class="subtitle">处理用户对评论内容的投诉举报，支持忽略或删除评论</span>
    </div>

    <el-table :data="displayComplaints" v-loading="loading" border stripe style="width:100%">
      <el-table-column label="被投诉评论" width="140">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="openContentView(row)">
            查看内容
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="投诉人" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.complainantName">{{ row.complainantName }}</span>
          <span v-else style="color:#999">匿名</span>
        </template>
      </el-table-column>
      <el-table-column label="投诉原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.content || '(无详细说明)' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="投诉人邮箱" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.email || '(未提供)' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="投诉时间" width="160">
        <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING' || row.status === 'PROCESSING'">
            <el-button type="primary" size="small" text @click="openProcessDialog(row, 'ignore')">
              忽略
            </el-button>
            <el-button type="danger" size="small" text @click="openProcessDialog(row, 'delete')">
              删评论
            </el-button>
          </template>
          <span v-else style="color:#999;font-size:12px">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 查看评论内容弹窗 -->
    <el-dialog v-model="contentVisible" title="被投诉评论内容" width="500px">
      <div v-if="viewComment" class="comment-preview">
        <div class="preview-meta">
          {{ viewComment.type === 'reply' ? '子回复' : '主评论' }} | ID：{{ viewComment.id }} | 时间：{{ formatTime(viewComment.created_at) }}
        </div>
        <div class="preview-content">{{ viewComment.content }}</div>
      </div>
      <div v-else style="text-align:center;color:#999;padding:20px">评论已被删除或不存在</div>
      <template #footer>
        <el-button @click="contentVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="450px">
      <el-form label-width="80px">
        <el-form-item label="被投诉评论">
          <span style="font-size:13px;color:#606266">{{ pendingRow?.target_id || '—' }}</span>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input v-model="processRemark" type="textarea" :rows="3" placeholder="输入处理备注（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmProcess">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getCommentComplaints,
  processCommentComplaint,
  deleteWorkComment,
  deleteWorkReply,
  getCommentById,
  getUserArchive,
  type ManageCommentComplaintRecord,
  type CommentContentResult,
} from '../api/commentManage'

interface DisplayComplaint extends ManageCommentComplaintRecord {
  complainantName: string
}

const loading = ref(false)
const complaints = ref<ManageCommentComplaintRecord[]>([])
const displayComplaints = ref<DisplayComplaint[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const processRemark = ref('')
const pendingRow = ref<ManageCommentComplaintRecord | null>(null)
const pendingAction = ref<'ignore' | 'delete'>('ignore')

// 查看评论内容
const contentVisible = ref(false)
const viewComment = ref<CommentContentResult | null>(null)

async function load() {
  loading.value = true
  try {
    complaints.value = await getCommentComplaints()
    // 为每个投诉获取投诉人昵称
    const enriched: DisplayComplaint[] = []
    for (const c of complaints.value) {
      let complainantName = ''
      if (c.author) {
        const archive = await getUserArchive(c.author)
        if (archive) {
          complainantName = archive.nickname
        }
      }
      enriched.push({ ...c, complainantName })
    }
    displayComplaints.value = enriched
  } catch {
    ElMessage.error('加载投诉列表失败')
  } finally {
    loading.value = false
  }
}

async function openContentView(row: ManageCommentComplaintRecord) {
  contentVisible.value = true
  viewComment.value = null
  try {
    const comment = await getCommentById(row.target_id)
    viewComment.value = comment
  } catch {
    viewComment.value = null
  }
}

function openProcessDialog(row: ManageCommentComplaintRecord, action: 'ignore' | 'delete') {
  pendingRow.value = row
  pendingAction.value = action
  dialogTitle.value = action === 'delete' ? '删除被投诉评论' : '忽略投诉'
  processRemark.value = ''
  dialogVisible.value = true
}

async function confirmProcess() {
  if (!pendingRow.value) return
  const targetId = pendingRow.value.target_id

  try {
    if (pendingAction.value === 'delete') {
      // 先判断投诉目标是主评论还是子回复
      let deleted = false
      try {
        const info = await getCommentById(targetId)
        if (info) {
          if (info.type === 'comment') {
            await deleteWorkComment(targetId)
            deleted = true
          } else if (info.type === 'reply') {
            await deleteWorkReply(targetId)
            deleted = true
          }
        }
        // info 为 null 说明已被删除，deleted 保持 false
      } catch {
        // getCommentById 失败，尝试直接删（兜底）
      }
      if (!deleted) {
        // 内容已不存在或查询失败，尝试两种删除方式兜底
        try { await deleteWorkComment(targetId); deleted = true } catch { /* */ }
        if (!deleted) {
          try { await deleteWorkReply(targetId); deleted = true } catch { /* */ }
        }
      }
      if (!deleted) {
        ElMessage.warning('评论删除失败，可能已被删除')
      }
    }

    // 更新投诉状态
    await processCommentComplaint(
      pendingRow.value.id,
      'COMPLETED',
      processRemark.value || (pendingAction.value === 'delete' ? '已删除被投诉评论' : '投诉已忽略'),
    )
    ElMessage.success('处理完成')
    dialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('处理失败')
  }
}

function statusTag(status: string): 'warning' | 'success' | 'info' | 'danger' {
  if (status === 'PENDING') return 'warning'
  if (status === 'PROCESSING') return 'info'
  if (status === 'COMPLETED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待处理', PROCESSING: '处理中', COMPLETED: '已完成', REJECTED: '已驳回',
  }
  return map[status] || status
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => load())
</script>

<style scoped>
.comment-complaints-view { padding: 4px 0; }
.section-header { margin-bottom: 16px; }
.section-header h3 { margin: 0 0 4px 0; font-size: 16px; color: #303133; }
.subtitle { font-size: 13px; color: #909399; }

.comment-preview { }
.preview-meta { font-size: 12px; color: #909399; margin-bottom: 12px; }
.preview-content {
  background: #f5f7fa; padding: 12px 16px; border-radius: 6px;
  font-size: 14px; color: #303133; line-height: 1.7; word-break: break-all;
  max-height: 300px; overflow-y: auto;
}
</style>
