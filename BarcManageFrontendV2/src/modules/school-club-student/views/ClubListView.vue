<template>
  <div class="club-list-view">
    <EntityBreadcrumb :school-id="schoolId" :school-name="schoolName" />
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索部团..." clearable @change="loadPage(1)" style="width: 240px;" />
      <el-button type="primary" @click="openCreate">新增部团</el-button>
    </div>
    <div class="card-grid">
      <ClubCard
        v-for="club in list"
        :key="club.id"
        :id="club.id"
        :name="club.cn_name"
        :logo="club.logo"
        @click="goToStudents"
        @edit="openEdit"
        @delete="handleDelete"
      />
    </div>
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadPage"
      />
    </div>
    <EntityFormDrawer
      v-model="drawerVisible"
      entity-type="club"
      :is-edit="isEdit"
      :initial-data="editData"
      :parent-id="schoolId"
      :school-options="schoolOptions"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ClubCard from '../components/ClubCard.vue'
import EntityBreadcrumb from '../components/EntityBreadcrumb.vue'
import EntityFormDrawer from '../components/EntityFormDrawer.vue'
import { fetchClubList, createClub, updateClub, deleteClub } from '../api/club'
import { fetchSchoolList, fetchSchoolById } from '../api/school'
import type { Club } from '../api/club'
import { useEntityCacheStore } from '../store/entityCache'

const route = useRoute()
const router = useRouter()
const schoolId = computed(() => route.params.schoolId as string)
const cache = useEntityCacheStore()

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<Club[]>([])
const drawerVisible = ref(false)
const isEdit = ref(false)
const editData = ref<Partial<Club>>({})
const schoolOptions = ref<{ id: string; cn_name: string }[]>([])

const schoolName = computed(() => cache.getSchoolName(schoolId.value))

async function loadPage(p = 1) {
  page.value = p
  try {
    const res = await fetchClubList(schoolId.value, keyword.value, page.value, size.value)
    list.value = res.list
    total.value = res.total
  } catch {
    ElMessage.error('加载部团列表失败')
  }
}

function goToStudents(id: string) {
  router.push({ name: 'students-list', params: { schoolId: schoolId.value, clubId: id } })
}

async function openCreate() {
  isEdit.value = false
  editData.value = {}
  await loadSchoolOptions()
  drawerVisible.value = true
}

async function openEdit(id: string) {
  const c = list.value.find(x => x.id === id)
  if (!c) return
  isEdit.value = true
  editData.value = { ...c }
  await loadSchoolOptions()
  drawerVisible.value = true
}

async function loadSchoolOptions() {
  try {
    const res = await fetchSchoolList('', 1, 9999)
    schoolOptions.value = res.list.map((s: any) => ({ id: s.id, cn_name: s.cn_name }))
  } catch {
    schoolOptions.value = []
  }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('删除该部团将同时软删除其下所有学生，是否继续？', '确认删除', { type: 'warning' })
    await deleteClub(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function handleSubmit(data: Record<string, any>) {
  if (isEdit.value) {
    await updateClub(data.id, data)
    ElMessage.success('修改成功')
  } else {
    await createClub(data as Club, schoolId.value)
    ElMessage.success('创建成功')
  }
  drawerVisible.value = false
  loadPage()
}

onMounted(async () => {
  if (!cache.getSchoolName(schoolId.value)) {
    try {
      const s = await fetchSchoolById(schoolId.value)
      cache.setSchoolName(schoolId.value, s.cn_name)
    } catch (e) {
      // ignore
    }
  }
  loadPage(1)
})
</script>

<style scoped>
.club-list-view {
  padding: 12px 16px 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-top: 12px;
  margin-bottom: 16px;
}
.card-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
