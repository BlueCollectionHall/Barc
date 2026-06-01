<template>
  <div class="work-detail" v-if="detail">
    <!-- 上半部分：封面 + 基本信息 -->
    <div class="header">
      <div class="cover-box">
        <span :class="['claim-tag', detail.work.is_claim ? 'claimed' : '']">{{ detail.work.is_claim ? '已认领' : '待认领' }}</span>
        <img v-if="detail.cover_image_url" :src="detail.cover_image_url" class="cover-img" />
      </div>
      <div class="info">
        <h1 class="title">{{ detail.work.title }}</h1>
        <div class="meta">
          <span>上传：{{ detail.work.created_at }}</span>
          <span>更新：{{ detail.work.updated_at }}</span>
        </div>
        <div class="author">
          <template v-if="detail.work.is_claim">
            <span>作者：{{ detail.author_display }}</span>
          </template>
          <template v-else>
            <span>暂归属：{{ detail.author_display || '未知' }}</span>
            <span>收录者：{{ detail.uploader_nickname }}</span>
            <span v-if="detail.work.author_nickname">原作者：{{ detail.work.author_nickname }}</span>
          </template>
        </div>
        <div class="stats">
          <span class="stat"><el-icon><View /></el-icon> {{ detail.work.view_count }}</span>
          <span class="stat">❤ {{ detail.work.like_count }}</span>
          <WorkStatusBadge :status="detail.work.status" />
        </div>
        <div class="chain">
          <el-tag v-if="detail.school_name" size="small" type="info">{{ detail.school_name }}</el-tag>
          <span v-if="detail.school_name && detail.club_name" style="color:#999">→</span>
          <el-tag v-if="detail.club_name" size="small" type="info">{{ detail.club_name }}</el-tag>
          <span v-if="detail.club_name && detail.student_name" style="color:#999">→</span>
          <el-tag v-if="detail.student_name" size="small">{{ detail.student_name }}</el-tag>
        </div>
        <!-- 管理操作按钮 -->
        <div class="actions">
          <template v-if="detail.work.status !== 'DELETED'">
            <el-button v-if="detail.work.status === 'PUBLIC' || detail.work.status === 'PRIVATE'" type="warning" @click="emit('ban')">封禁</el-button>
            <el-button v-if="detail.work.status === 'PUBLIC' || detail.work.status === 'PRIVATE'" type="warning" @click="emit('off')">下架</el-button>
            <el-button v-if="detail.work.status === 'BAN' || detail.work.status === 'OFF'" type="success" @click="emit('restore')">恢复公开</el-button>
          </template>
          <el-button v-if="detail.work.status !== 'DELETED'" type="danger" @click="emit('delete')">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 内容切换 -->
    <div class="tabs">
      <div :class="['tab', tab === 'content' ? 'active' : '']" @click="tab = 'content'">显示内容</div>
      <div :class="['tab', tab === 'images' ? 'active' : '']" @click="tab = 'images'">显示图集</div>
    </div>

    <!-- 内容区 -->
    <div v-if="tab === 'content'" class="body" v-html="detail.work.content" />

    <!-- 图集 -->
    <div v-else-if="detail.content_image_urls?.length" class="gallery">
      <el-image v-for="(u,i) in detail.content_image_urls" :key="i" :src="u"
        style="width:200px" fit="contain" :preview-src-list="detail.content_image_urls" :initial-index="i" />
    </div>
    <div v-else style="color:#999;padding:20px">暂无图集</div>
  </div>
  <div v-else style="text-align:center;padding:40px;color:#999">加载中…</div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { View } from '@element-plus/icons-vue'
import { getWorkEditDetail } from '../api/workManage'
import WorkStatusBadge from './WorkStatusBadge.vue'
import type { WorkEditDetail } from '../api/workManage'

const props = defineProps<{ workId: string }>()
const emit = defineEmits<{ ban: []; off: []; restore: []; delete: [] }>()

const detail = ref<WorkEditDetail | null>(null)
const tab = ref<'content' | 'images'>('content')

onMounted(async () => {
  try { detail.value = await getWorkEditDetail(props.workId) } catch { /* */ }
})

// 通过 expose 让父组件传 workId 并加载
async function load(id: string) {
  try { detail.value = await getWorkEditDetail(id) } catch { /* */ }
}
defineExpose({ load })
</script>

<style scoped>
.work-detail { max-width: 960px; margin: 0 auto; }
.header { display: flex; gap: 24px; align-items: flex-start; }
.cover-box { flex-shrink: 0; width: 288px; height: 162px; overflow: hidden; border-radius: 12px; position: relative; box-shadow: 0 0 8px rgba(0,0,0,.1); }
.cover-img { width: 100%; display: block; }
.claim-tag { position: absolute; background: #fe4b7b; color: #fff; padding: 4px 40px; font-weight: bold; right: -30px; top: 16px; transform: rotate(45deg); font-size: 12px; white-space: nowrap; }
.claim-tag.claimed { background: #2faa41; }
.info { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.title { margin: 0; font-size: 1.4rem; }
.meta { display: flex; gap: 32px; color: #999; font-size: 13px; }
.author { display: flex; flex-direction: column; gap: 2px; font-size: 14px; }
.stats { display: flex; align-items: center; gap: 16px; }
.stat { display: flex; align-items: center; gap: 4px; }
.chain { display: flex; align-items: center; gap: 6px; }
.actions { display: flex; gap: 8px; }
.tabs { display: flex; border-bottom: 1px solid #ebeef5; margin: 24px 0 16px; }
.tab { padding: 8px 24px; cursor: pointer; border-radius: 8px 8px 0 0; color: #606266; transition: .2s; }
.tab:hover { background: #f0f2f5; }
.tab.active { color: #409eff; border-bottom: 2px solid #409eff; }
.body { padding: 8px 0; line-height: 1.8; }
.gallery { display: flex; flex-wrap: wrap; gap: 12px; padding: 8px 0; }
</style>
