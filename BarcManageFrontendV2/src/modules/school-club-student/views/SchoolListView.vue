<template>
  <div class="school-list-view">
    <div class="toolbar">
      <el-input v-model="keyword" :placeholder="searchPlaceholder" clearable @change="loadPage(1)" style="width: 240px;" />
      <el-radio-group v-model="mode" @change="loadPage(1)">
        <el-radio-button label="school">学园</el-radio-button>
        <el-radio-button label="club">部团</el-radio-button>
        <el-radio-button label="student">学生</el-radio-button>
      </el-radio-group>
      <el-button v-if="mode === 'school'" type="primary" @click="openCreate">新增学园</el-button>
    </div>
    <div class="card-grid">
      <SchoolCard
        v-if="mode === 'school'"
        v-for="school in list"
        :key="school.id"
        :id="school.id"
        :name="school.cn_name"
        :logo="school.logo"
        @click="goToClubs"
        @edit="openEdit"
        @delete="handleDelete"
      />
      <ClubCard
        v-if="mode === 'club'"
        v-for="club in list"
        :key="club.id"
        :id="club.id"
        :name="club.cn_name"
        :logo="club.logo"
        @click="goToStudents"
        @edit="openEditClub"
        @delete="handleDeleteClub"
      />
      <StudentCard
        v-if="mode === 'student'"
        v-for="student in list"
        :key="student.id"
        :id="student.id"
        :name="student.cn_name"
        :avatar="student.avatar_rectangle"
        @click="openEditStudent"
        @edit="openEditStudent"
        @delete="handleDeleteStudent"
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
      :entity-type="drawerEntityType"
      :is-edit="isEdit"
      :initial-data="editData"
      :parent-id="parentId"
      :school-options="schoolOptions"
      :club-options="clubOptions"
      @submit="handleSubmit"
      @school-change="handleSchoolChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SchoolCard from '../components/SchoolCard.vue'
import ClubCard from '../components/ClubCard.vue'
import StudentCard from '../components/StudentCard.vue'
import EntityFormDrawer from '../components/EntityFormDrawer.vue'
import { fetchSchoolList, createSchool, updateSchool, deleteSchool } from '../api/school'
import { fetchClubList, updateClub, deleteClub } from '../api/club'
import { fetchStudentList, updateStudent, deleteStudent } from '../api/student'
import type { School } from '../api/school'
import type { Club } from '../api/club'
import type { Student } from '../api/student'

const router = useRouter()
const mode = ref<'school' | 'club' | 'student'>('school')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref<any[]>([])
const drawerVisible = ref(false)
const isEdit = ref(false)
const editData = ref<Partial<any>>({})
const drawerEntityType = ref<'school' | 'club' | 'student'>('school')
const parentId = ref<string>('')  // 新增：用于传递父级ID
const schoolOptions = ref<{ id: string; cn_name: string }[]>([])
const clubOptions = ref<{ id: string; cn_name: string }[]>([])

const searchPlaceholder = computed(() => {
  const map = { school: '搜索学园...', club: '搜索部团...', student: '搜索学生...' }
  return map[mode.value]
})

async function loadPage(p = 1) {
  page.value = p
  try {
    let res: any
    if (mode.value === 'school') {
      res = await fetchSchoolList(keyword.value, page.value, size.value)
    } else if (mode.value === 'club') {
      res = await fetchClubList('', keyword.value, page.value, size.value)
    } else {
      res = await fetchStudentList('', keyword.value, page.value, size.value)
    }
    list.value = res.list
    total.value = res.total
  } catch {
    ElMessage.error('加载列表失败')
  }
}

function goToClubs(id: string) {
  router.push({ name: 'clubs-list', params: { schoolId: id } })
}

function goToStudents(id: string) {
  router.push({ name: 'students-list', params: { schoolId: list.value.find((c: Club) => c.id === id)?.school, clubId: id } })
}

function openCreate() {
  drawerEntityType.value = 'school'
  isEdit.value = false
  editData.value = {}
  drawerVisible.value = true
}

function openEdit(id: string) {
  const s = list.value.find((x: School) => x.id === id)
  if (!s) return
  drawerEntityType.value = 'school'
  isEdit.value = true
  editData.value = { ...s }
  drawerVisible.value = true
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('删除该学园将同时软删除其下所有部团与学生，是否继续？', '确认删除', { type: 'warning' })
    await deleteSchool(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function openEditClub(id: string) {
  const c = list.value.find((x: Club) => x.id === id)
  if (!c) return
  drawerEntityType.value = 'club'
  isEdit.value = true
  editData.value = { ...c }
  parentId.value = c.school || ''  // 设置 parentId 为部团所属的学园ID
  await loadSchoolOptions()
  drawerVisible.value = true
}

async function handleDeleteClub(id: string) {
  try {
    await ElMessageBox.confirm('删除该部团将同时软删除其下所有学生，是否继续？', '确认删除', { type: 'warning' })
    await deleteClub(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function openEditStudent(id: string) {
  const s = list.value.find((x: Student) => x.id === id)
  if (!s) return
  drawerEntityType.value = 'student'
  isEdit.value = true
  editData.value = { ...s }
  await loadSchoolOptions()
  await loadClubOptions(s.school)  // 根据学生的 school_id 加载对应的部团列表
  drawerVisible.value = true
}

async function handleDeleteStudent(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该学生吗？', '确认删除', { type: 'warning' })
    await deleteStudent(id)
    ElMessage.success('删除成功')
    loadPage()
  } catch (e) {
    // cancel
  }
}

async function loadSchoolOptions() {
  try {
    const res = await fetchSchoolList('', 1, 9999)
    schoolOptions.value = res.list.map((s: School) => ({ id: s.id, cn_name: s.cn_name }))
  } catch {
    schoolOptions.value = []
  }
}

async function loadClubOptions(schoolId?: string) {
  try {
    const res = await fetchClubList(schoolId || '', '', 1, 9999)
    clubOptions.value = res.list.map((c: Club) => ({ id: c.id, cn_name: c.cn_name }))
  } catch {
    clubOptions.value = []
  }
}

// 学园切换处理
async function handleSchoolChange(schoolId: string) {
  await loadClubOptions(schoolId)
}

async function handleSubmit(data: Record<string, any>) {
  try {
    if (drawerEntityType.value === 'school') {
      if (isEdit.value) {
        await updateSchool(data.id, data)
      } else {
        await createSchool(data as School)
      }
    } else if (drawerEntityType.value === 'club') {
      // school 字段已在 EntityFormDrawer 中设置，后端从 request body 读取
      await updateClub(data.id, data)
    } else if (drawerEntityType.value === 'student') {
      const clubId = data.club
      const { school, club, school_id, club_id, ...rest } = data
      await updateStudent(data.id, rest, clubId)
    }
    ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
    drawerVisible.value = false
    loadPage()
  } catch {
    ElMessage.error('操作失败')
  }
}

onMounted(() => loadPage(1))
</script>

<style scoped>
.school-list-view {
  padding: 12px 16px 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
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
