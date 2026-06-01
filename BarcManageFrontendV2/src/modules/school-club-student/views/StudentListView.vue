<template>
  <div class="student-list-view">
    <EntityBreadcrumb :school-id="schoolId" :school-name="schoolName" :club-name="clubName" />
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索学生..." clearable @change="loadPage(1)" style="width: 240px;" />
      <el-button type="primary" @click="openCreate">新增学生</el-button>
    </div>
    <div class="card-grid">
      <StudentCard
        v-for="student in list"
        :key="student.id"
        :id="student.id"
        :name="student.cn_name"
        :avatar="student.avatar_rectangle"
        @click="openEdit"
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
      entity-type="student"
      :is-edit="isEdit"
      :initial-data="editData"
      :parent-id="clubId"
      :school-options="schoolOptions"
      :club-options="clubOptions"
      :school-name="schoolName"
      @submit="handleSubmit"
      @school-change="handleSchoolChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StudentCard from '../components/StudentCard.vue'
import EntityBreadcrumb from '../components/EntityBreadcrumb.vue'
import EntityFormDrawer from '../components/EntityFormDrawer.vue'
import { fetchStudentList, createStudent, updateStudent, deleteStudent } from '../api/student'
import { fetchClubList, fetchClubById } from '../api/club'
import { fetchSchoolList, fetchSchoolById } from '../api/school'
import type { Student } from '../api/student'
import { useEntityCacheStore } from '../store/entityCache'

const route = useRoute()
const schoolId = computed(() => route.params.schoolId as string)
const clubId = computed(() => route.params.clubId as string)
const cache = useEntityCacheStore()

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<Student[]>([])
const drawerVisible = ref(false)
const isEdit = ref(false)
const editData = ref<Partial<Student>>({})
const clubOptions = ref<{ id: string; cn_name: string }[]>([])
const schoolOptions = ref<{ id: string; cn_name: string }[]>([])

const schoolName = computed(() => cache.getSchoolName(schoolId.value))
const clubName = computed(() => cache.getClubName(clubId.value))

async function loadPage(p = 1) {
  page.value = p
  try {
    const res = await fetchStudentList(clubId.value, keyword.value, page.value, size.value)
    list.value = res.list
    total.value = res.total
  } catch {
    ElMessage.error('加载学生列表失败')
  }
}

async function openCreate() {
  isEdit.value = false
  editData.value = {}
  await loadSchoolOptions()
  await loadClubOptions()
  drawerVisible.value = true
}

async function openEdit(id: string) {
  const s = list.value.find(x => x.id === id)
  if (!s) return
  isEdit.value = true
  editData.value = { ...s }
  await loadSchoolOptions()
  await loadClubOptions(s.school)  // 根据学生的 school_id 加载对应的部团列表
  drawerVisible.value = true
}

async function loadClubOptions(schoolId?: string) {
  try {
    const res = await fetchClubList(schoolId || '', '', 1, 9999)
    clubOptions.value = res.list.map((c: any) => ({ id: c.id, cn_name: c.cn_name }))
  } catch {
    clubOptions.value = []
  }
}

// 学园切换处理
async function handleSchoolChange(schoolId: string) {
  await loadClubOptions(schoolId)
}

// 加载学园选项
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
    await ElMessageBox.confirm('确定删除该学生吗？', '确认删除', { type: 'warning' })
    await deleteStudent(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function handleSubmit(data: Record<string, any>) {
  if (isEdit.value) {
    // EntityFormDrawer 发送 payload.club = form.club_id，所以用 data.club
    const clubId = data.club
    // 移除 school/club 字段，避免与 @RequestParam 冲突（后端通过 clubId 查询 school）
    const { school, club, school_id, club_id, ...rest } = data
    await updateStudent(data.id, rest, clubId)
    ElMessage.success('修改成功')
  } else {
    await createStudent(data as Student, data.club)
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
  if (!cache.getClubName(clubId.value)) {
    try {
      const c = await fetchClubById(clubId.value)
      cache.setClubName(clubId.value, c.cn_name)
    } catch (e) {
      // ignore
    }
  }
  loadPage(1)
})
</script>

<style scoped>
.student-list-view {
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
