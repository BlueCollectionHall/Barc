<template>
  <div class="work-list">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="状态">
        <el-select v-model="statusFilter" placeholder="全部" style="width: 130px" @change="handleFilter">
          <el-option label="全部" value="" />
          <el-option label="公开" value="PUBLIC" />
          <el-option label="私有" value="PRIVATE" />
          <el-option label="下架" value="OFF" />
          <el-option label="封禁" value="BAN" />
          <el-option label="已删除" value="DELETED" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="keywordFilter" placeholder="搜索标题或作者" clearable @keyup.enter="handleFilter" style="width: 240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleFilter">搜索</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="workList" border stripe v-loading="loading" style="width: 100%">
      <el-table-column prop="id" label="ID" width="120" show-overflow-tooltip />
      <el-table-column label="封面" width="110">
        <template #default="{ row }">
          <div v-if="row.cover_image" class="cover-thumb"><img :src="row.cover_image" alt="" /></div>
          <span v-else style="color:#ccc; font-size:12px">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
      <el-table-column label="作品作者" width="145">
        <template #default="{ row }">
          <div>{{ getWorkCreatorDisplay(row) }}</div>
          <div class="author-source">{{ getWorkCreatorSourceLabel(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90"><template #default="{ row }"><WorkStatusBadge :status="row.status" /></template></el-table-column>
      <el-table-column label="审核" width="100">
        <template #default="{ row }">
          <el-tag
            v-if="row.review_status"
            :type="row.review_status === 'APPROVED' ? 'success' : row.review_status === 'REJECTED' ? 'danger' : 'warning'"
            size="small"
          >
            {{ row.review_status === 'APPROVED' ? '已通过' : row.review_status === 'REJECTED' ? '未通过' : '审核中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="emit('edit', row)">详情</el-button>
          <template v-if="row.status !== 'DELETED'">
            <el-button v-if="row.status === 'PUBLIC' || row.status === 'PRIVATE'" size="small" type="warning" @click="emit('ban', row)">封禁</el-button>
            <el-button v-if="row.status === 'PUBLIC' || row.status === 'PRIVATE'" size="small" type="warning" @click="emit('off', row)">下架</el-button>
            <el-button v-if="row.status === 'BAN' || row.status === 'OFF'" size="small" type="success" @click="emit('restore', row)">恢复</el-button>
          </template>
          <el-button v-if="row.status !== 'DELETED'" size="small" type="danger" @click="emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination v-model:current-page="currentPage" :page-size="10" :total="total" layout="total, prev, pager, next" @current-change="handlePageChange" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getWorkListManage } from '../api/workManage'
import WorkStatusBadge from './WorkStatusBadge.vue'
import { getWorkCreatorDisplay, getWorkCreatorSourceLabel } from '../utils/workAttribution'

const emit = defineEmits<{ edit: [row: any]; ban: [row: any]; off: [row: any]; restore: [row: any]; delete: [row: any] }>()
const workList = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)
const statusFilter = ref('')
const keywordFilter = ref('')

const fetchData = async () => {
  loading.value = true
  try {
    const result = await getWorkListManage({ page_num: currentPage.value, page_size: 10, params: { status: statusFilter.value || undefined, keyword: keywordFilter.value || undefined } })
    workList.value = result.list ?? []
    total.value = result.total ?? 0
  } finally { loading.value = false }
}
const handleFilter = () => { currentPage.value = 1; fetchData() }
const handlePageChange = (page: number) => { currentPage.value = page; fetchData() }
onMounted(() => fetchData())
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}
.author-source {
  color: var(--barc-text-soft);
  font-size: 0.75rem;
}
.cover-thumb {
  width: 80px;
  height: 45px;
  overflow: hidden;
  border-radius: 4px;
}
.cover-thumb img {
  width: 100%;
  display: block;
}
</style>
