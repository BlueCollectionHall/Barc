<script setup lang="ts">
import { ref, watch, nextTick, computed } from "vue";
import {
  MessageOutlined,
  SendOutlined,
  DeleteOutlined,
  CloseOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons-vue";
import { useUserPinia } from "@/stores/UserPinia.ts";
import { storeToRefs } from "pinia";
import {
  fetchCommentsByWork,
  uploadComment,
  uploadReply,
  deleteComment,
  deleteReply,
  reportComment,
  fetchUserArchive,
} from "@/utils/commentApi.ts";
import { errorMessage, successMessage } from "@/utils/MessageAlert.ts";
import type { WorkCommentImpl, WorkCommentReplyImpl } from "@/interfaces/WorkImpl.ts";
import type { CommentDisplayImpl, ReplyDisplayImpl } from "@/interfaces/CommentImpl.ts";
import type { FeedBackImpl } from "@/interfaces/FeedbackImpl.ts";

const props = defineProps<{
  workId: string;
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: "update:visible", value: boolean): void;
  (e: "commentCount", count: number): void;
}>();

const userPinia = useUserPinia();
const { userBasic } = storeToRefs(userPinia);

// ========== 状态 ==========
const loading = ref(false);
const allComments = ref<Array<CommentDisplayImpl>>([]);
const displayCount = ref(10); // 每次展示条数
const inputContent = ref("");

// 回复状态
const replyTarget = ref<{
  commentId: string;
  replyUserId: string;
  replyUserNickname: string;
} | null>(null);

// 举报状态
const reportVisible = ref(false);
const reportTarget = ref<CommentDisplayImpl | ReplyDisplayImpl | null>(null);
const reportContent = ref("");

// 用户缓存
const userCache = ref<Map<string, { nickname: string; avatar: string | null }>>(new Map());

// ========== 计算属性 ==========
const token = computed(() => window.localStorage.getItem("token") || "");

const displayComments = computed(() => allComments.value.slice(0, displayCount.value));

const hasMore = computed(() => displayCount.value < allComments.value.length);

const totalCommentCount = computed(() => {
  let count = allComments.value.length;
  for (const c of allComments.value) {
    if (c.replies) count += c.replies.length;
  }
  return count;
});

// ========== 用户信息获取（带缓存） ==========
async function getUserInfo(uuid: string): Promise<{ nickname: string; avatar: string | null }> {
  if (userCache.value.has(uuid)) {
    return userCache.value.get(uuid)!;
  }
  const result = await fetchUserArchive(uuid);
  const info = result || { nickname: "未知用户", avatar: null };
  userCache.value.set(uuid, info);
  return info;
}

// ========== 加载评论 ==========
async function loadComments() {
  if (!props.workId) return;
  loading.value = true;
  try {
    const rawComments = await fetchCommentsByWork(props.workId);
    // 转换为展示模型（填充用户昵称）
    const displayList: Array<CommentDisplayImpl> = [];
    for (const c of rawComments) {
      const authorInfo = await getUserInfo(c.author);
      const displayComment: CommentDisplayImpl = {
        ...c,
        authorNickname: authorInfo.nickname,
        authorAvatar: authorInfo.avatar,
        replies: [],
      };
      if (c.replies && c.replies.length > 0) {
        const replyDisplays: Array<ReplyDisplayImpl> = [];
        for (const r of c.replies) {
          const replyAuthorInfo = await getUserInfo(r.author);
          let replyUserNickname: string | null = null;
          if (r.reply_user) {
            const replyUserInfo = await getUserInfo(r.reply_user);
            replyUserNickname = replyUserInfo.nickname;
          }
          replyDisplays.push({
            ...r,
            authorNickname: replyAuthorInfo.nickname,
            authorAvatar: replyAuthorInfo.avatar,
            replyUserNickname,
          });
        }
        displayComment.replies = replyDisplays;
      }
      displayList.push(displayComment);
    }
    allComments.value = displayList;
    displayCount.value = 10;
    emit("commentCount", totalCommentCount.value);
  } catch (e) {
    console.error(e);
    errorMessage("加载评论失败");
  } finally {
    loading.value = false;
  }
}

// ========== 滚动加载更多 ==========
function onScroll(e: Event) {
  const target = e.target as HTMLElement;
  if (!target) return;
  // 距离底部 50px 时加载更多
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 50) {
    if (hasMore.value && !loading.value) {
      displayCount.value += 10;
    }
  }
}

// ========== 发布评论 ==========
async function handleSubmitComment() {
  const content = inputContent.value.trim();
  if (!content) return;
  if (!token.value) {
    errorMessage("请先登录后再评论");
    return;
  }
  try {
    if (replyTarget.value) {
      // 回复
      const success = await uploadReply(
        replyTarget.value.commentId,
        replyTarget.value.replyUserId,
        content,
        token.value,
      );
      if (success) {
        successMessage("回复成功");
        replyTarget.value = null;
        inputContent.value = "";
        await loadComments();
      }
    } else {
      // 新评论
      const success = await uploadComment(props.workId, content, token.value);
      if (success) {
        successMessage("评论成功");
        inputContent.value = "";
        await loadComments();
      }
    }
  } catch (e) {
    console.error(e);
    errorMessage("操作失败");
  }
}

// ========== 回复 ==========
function handleReply(comment: CommentDisplayImpl | ReplyDisplayImpl, isReply: boolean) {
  if (!token.value) {
    errorMessage("请先登录后再回复");
    return;
  }
  if (isReply) {
    const r = comment as ReplyDisplayImpl;
    replyTarget.value = {
      commentId: r.parent_id,
      replyUserId: r.author,
      replyUserNickname: r.authorNickname,
    };
  } else {
    const c = comment as CommentDisplayImpl;
    replyTarget.value = {
      commentId: c.id,
      replyUserId: c.author,
      replyUserNickname: c.authorNickname,
    };
  }
}

function cancelReply() {
  replyTarget.value = null;
  inputContent.value = "";
}

// ========== 删除 ==========
async function handleDeleteComment(commentId: string) {
  if (!token.value) return;
  try {
    const success = await deleteComment(commentId, token.value);
    if (success) {
      successMessage("删除成功");
      await loadComments();
    }
  } catch (e) {
    console.error(e);
    errorMessage("删除失败");
  }
}

async function handleDeleteReply(replyId: string) {
  if (!token.value) return;
  try {
    const success = await deleteReply(replyId, token.value);
    if (success) {
      successMessage("删除成功");
      await loadComments();
    }
  } catch (e) {
    console.error(e);
    errorMessage("删除失败");
  }
}

// ========== 举报 ==========
function openReport(target: CommentDisplayImpl | ReplyDisplayImpl) {
  reportTarget.value = target;
  reportContent.value = "";
  reportVisible.value = true;
}

async function handleSubmitReport() {
  if (!reportContent.value.trim()) {
    errorMessage("请填写举报原因");
    return;
  }
  if (!reportTarget.value) return;
  try {
    const feedback: FeedBackImpl = {
      id: "",
      target_id: reportTarget.value.id,
      ipv4: null,
      ipv6: null,
      author: userBasic.value?.uuid || null,
      email: userBasic.value?.email || null,
      content: reportContent.value.trim(),
      echo: null,
      type: "COMMENT",
      status: "PENDING",
      created_at: new Date(),
      updated_at: new Date(),
    };
    const success = await reportComment(feedback);
    if (success) {
      successMessage("举报已提交");
      reportVisible.value = false;
      reportTarget.value = null;
    }
  } catch (e) {
    console.error(e);
    errorMessage("举报失败");
  }
}

// ========== 工具函数 ==========
function formatTime(dateStr: string | Date): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function getAvatarChar(nickname: string): string {
  return nickname?.charAt(0) || "?";
}

function truncateNick(name: string, maxLen = 10): string {
  if (!name) return "";
  return name.length > maxLen ? name.slice(0, maxLen) + "…" : name;
}

// ========== 监听 ==========
watch(
  () => props.visible,
  (val) => {
    if (val) {
      displayCount.value = 10;
      replyTarget.value = null;
      inputContent.value = "";
      loadComments();
    }
  },
);

function handleClose() {
  emit("update:visible", false);
}
</script>

<template>
  <a-modal
    :open="visible"
    :footer="null"
    :width="560"
    @cancel="handleClose"
    :closable="false"
    :bodyStyle="{ padding: '0', height: '520px', display: 'flex', flexDirection: 'column' }"
    wrapClassName="comment-modal"
  >
    <!-- 头部 -->
    <div class="comment-header">
      <span class="comment-title"><MessageOutlined /> 评论区 ({{ totalCommentCount }})</span>
      <span class="comment-close" @click="handleClose"><CloseOutlined /></span>
    </div>

    <!-- 评论列表 -->
    <div class="comment-list" @scroll="onScroll">
      <a-spin :spinning="loading" tip="加载中...">
        <div v-if="allComments.length === 0 && !loading" class="empty-tip">
          暂无评论，快来抢沙发吧~
        </div>

        <div v-for="comment in displayComments" :key="comment.id" class="comment-item">
          <!-- 主评论 -->
          <div class="comment-main">
            <div class="comment-avatar">
              <img v-if="comment.authorAvatar" :src="comment.authorAvatar" class="avatar-img" />
              <span v-else>{{ getAvatarChar(comment.authorNickname) }}</span>
            </div>
            <div class="comment-body">
              <div class="comment-meta">
                <a-tooltip :title="comment.authorNickname" v-if="comment.authorNickname.length > 10">
                  <span class="comment-nickname">{{ truncateNick(comment.authorNickname) }}</span>
                </a-tooltip>
                <span class="comment-nickname" v-else>{{ comment.authorNickname }}</span>
                <span class="comment-time">{{ formatTime(comment.created_at) }}</span>
              </div>
              <div class="comment-content">{{ comment.content }}</div>
              <div class="comment-actions">
                <span class="action-btn" @click="handleReply(comment, false)">回复</span>
                <span class="action-btn" @click="openReport(comment)">举报</span>
                <a-popconfirm
                  v-if="userBasic?.uuid === comment.author"
                  title="确定要删除这条评论吗？"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handleDeleteComment(comment.id)"
                >
                  <span class="action-btn danger">删除</span>
                </a-popconfirm>
              </div>

              <!-- 子回复列表 -->
              <div v-if="comment.replies && comment.replies.length > 0" class="reply-list">
                <div v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                  <div class="comment-avatar small">
                    <img v-if="reply.authorAvatar" :src="reply.authorAvatar" class="avatar-img" />
                    <span v-else>{{ getAvatarChar(reply.authorNickname) }}</span>
                  </div>
                  <div class="comment-body">
                    <div class="comment-meta">
                      <a-tooltip :title="reply.authorNickname" v-if="reply.authorNickname.length > 10">
                        <span class="comment-nickname">{{ truncateNick(reply.authorNickname) }}</span>
                      </a-tooltip>
                      <span class="comment-nickname" v-else>{{ reply.authorNickname }}</span>
                      <span v-if="reply.replyUserNickname" class="reply-to">
                        <a-tooltip :title="reply.replyUserNickname" v-if="reply.replyUserNickname.length > 10">
                          @{{ truncateNick(reply.replyUserNickname) }}
                        </a-tooltip>
                        <template v-else>@{{ reply.replyUserNickname }}</template>
                      </span>
                      <span class="comment-time">{{ formatTime(reply.created_at) }}</span>
                    </div>
                    <div class="comment-content">{{ reply.content }}</div>
                    <div class="comment-actions">
                      <span class="action-btn" @click="handleReply(reply, true)">回复</span>
                      <span class="action-btn" @click="openReport(reply)">举报</span>
                      <a-popconfirm
                        v-if="userBasic?.uuid === reply.author"
                        title="确定要删除这条回复吗？"
                        ok-text="确定"
                        cancel-text="取消"
                        @confirm="handleDeleteReply(reply.id)"
                      >
                        <span class="action-btn danger">删除</span>
                      </a-popconfirm>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载更多提示 -->
        <div v-if="hasMore && !loading" class="load-more" @click="displayCount += 10">
          点击加载更多...
        </div>
        <div v-if="!hasMore && allComments.length > 0" class="no-more">
          — 没有更多评论了 —
        </div>
      </a-spin>
    </div>

    <!-- 输入区域 -->
    <div class="comment-input-area">
      <div v-if="replyTarget" class="reply-tag">
        回复 @{{ replyTarget.replyUserNickname }}
        <span class="cancel-reply" @click="cancelReply"><CloseOutlined /></span>
      </div>
      <div class="input-row">
        <a-textarea
          v-model:value="inputContent"
          :placeholder="replyTarget ? `回复 @${replyTarget.replyUserNickname}...` : '写下你的评论...'"
          :autoSize="{ minRows: 1, maxRows: 3 }"
          :maxlength="500"
          @pressEnter="(e: KeyboardEvent) => { if (!e.shiftKey) { e.preventDefault(); handleSubmitComment(); } }"
        />
        <a-button
          type="primary"
          :disabled="!inputContent.trim()"
          @click="handleSubmitComment"
          class="send-btn"
        >
          <SendOutlined />
        </a-button>
      </div>
    </div>

    <!-- 举报弹窗 -->
    <a-modal
      v-model:open="reportVisible"
      title="举报评论"
      ok-text="提交举报"
      cancel-text="取消"
      @ok="handleSubmitReport"
      :width="400"
    >
      <div class="report-info">
        被举报内容：{{ reportTarget?.content?.slice(0, 50) }}{{ (reportTarget?.content?.length ?? 0) > 50 ? '...' : '' }}
      </div>
      <br />
      <a-textarea
        v-model:value="reportContent"
        placeholder="请描述举报原因..."
        :rows="3"
        :maxlength="200"
      />
    </a-modal>
  </a-modal>
</template>

<style scoped>
.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.comment-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.comment-close {
  cursor: pointer;
  font-size: 16px;
  color: #999;
  padding: 4px;
}
.comment-close:hover { color: #333; }

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  min-height: 0;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px 0;
  font-size: 14px;
}

.comment-item {
  margin-bottom: 16px;
}
.comment-main {
  display: flex;
  gap: 10px;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #384a8720;
  color: #384a87;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: bold;
  flex-shrink: 0;
  overflow: hidden;
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.comment-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 13px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.comment-nickname {
  font-size: 13px;
  font-weight: 600;
  color: #384a87;
}
.reply-to {
  font-size: 12px;
  color: #fe4b7b;
  background: #fe4b7b10;
  padding: 0 6px;
  border-radius: 4px;
}
.comment-time {
  font-size: 11px;
  color: #bbb;
  margin-left: auto;
}

.comment-content {
  margin-top: 4px;
  font-size: 14px;
  color: #444;
  line-height: 1.6;
  word-break: break-all;
}

.comment-actions {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}
.action-btn {
  font-size: 12px;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
}
.action-btn:hover { color: #384a87; }
.action-btn.danger:hover { color: #fe4b7b; }

/* 子回复 */
.reply-list {
  margin-top: 8px;
  padding: 8px 0 8px 12px;
  border-left: 2px solid #384a8720;
  background: #fafafa;
  border-radius: 0 8px 8px 0;
}
.reply-item {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.reply-item:last-child { margin-bottom: 0; }

/* 加载更多 */
.load-more {
  text-align: center;
  color: #384a87;
  font-size: 13px;
  padding: 12px 0;
  cursor: pointer;
}
.load-more:hover { text-decoration: underline; }
.no-more {
  text-align: center;
  color: #ccc;
  font-size: 12px;
  padding: 12px 0;
}

/* 输入区域 */
.comment-input-area {
  border-top: 1px solid #f0f0f0;
  padding: 12px 16px;
  flex-shrink: 0;
}
.reply-tag {
  font-size: 12px;
  color: #fe4b7b;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.cancel-reply {
  cursor: pointer;
  font-size: 12px;
  color: #999;
}
.cancel-reply:hover { color: #333; }

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-row :deep(.ant-input) {
  resize: none;
}
.send-btn {
  flex-shrink: 0;
}

.report-info {
  background: #f5f5f5;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: #666;
  word-break: break-all;
}
</style>

<!-- 全局样式（非 scoped，用于修改 a-modal 内部样式） -->
<style>
.comment-modal .ant-modal-content {
  padding: 0;
  overflow: hidden;
}
.comment-modal .ant-modal-body {
  padding: 0 !important;
  height: 520px;
  display: flex;
  flex-direction: column;
}
</style>
