<template>
  <el-drawer v-model="visible" :title="title" size="480px" :before-close="handleClose">
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <!-- ID 字段 -->
      <el-form-item v-if="!isEdit && entityType === 'student'" label="ID">
        <el-input :model-value="idPreview" disabled placeholder="输入英文名后自动生成" />
        <div v-if="idConflict" class="error-text">ID 已存在</div>
      </el-form-item>

      <el-form-item v-if="!isEdit && entityType !== 'student'" label="ID" prop="id">
        <el-input v-model="form.id" @blur="checkId" placeholder="输入唯一标识" />
        <div v-if="idConflict" class="error-text">ID 已存在</div>
      </el-form-item>

      <el-form-item v-if="isEdit" label="ID">
        <el-input :model-value="form.id" disabled />
      </el-form-item>

      <el-form-item label="英文名" prop="en_name">
        <el-input v-model="form.en_name" :disabled="isEdit && entityType === 'student'" :placeholder="entityType === 'student' && !isEdit ? 'North Abydos' : ''" />
      </el-form-item>

      <el-form-item label="中文名" prop="cn_name">
        <el-input v-model="form.cn_name" />
      </el-form-item>
      <el-form-item label="日文名">
        <el-input v-model="form.jp_name" />
      </el-form-item>
      <el-form-item label="韩文名">
        <el-input v-model="form.kr_name" />
      </el-form-item>
      <el-form-item v-if="entityType !== 'club'" label="介绍">
        <el-input v-model="form.introduce" type="textarea" rows="3" />
      </el-form-item>

      <!-- 跨级重选上级 -->
      <el-form-item v-if="entityType === 'club'" label="所属学园">
        <el-select v-model="form.school_id" placeholder="选择学园">
          <el-option v-for="s in schoolOptions" :key="s.id" :label="s.cn_name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="entityType === 'student'" label="所属学园">
        <el-select v-model="form.school_id" placeholder="选择学园" @change="handleSchoolChange">
          <el-option v-for="s in schoolOptions" :key="s.id" :label="s.cn_name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="entityType === 'student'" label="所属部团">
        <el-select v-model="form.club_id" placeholder="选择部团">
          <el-option v-for="c in filteredClubOptions" :key="c.id" :label="c.cn_name" :value="c.id" />
        </el-select>
      </el-form-item>

      <!-- 图片字段 -->
      <el-form-item v-for="field in imageFields" :key="field.key" :label="field.label">
        <ImageUploadField 
          v-model="form[field.key]" 
          :upload-url="buildUploadUrl(field.key)"
          :entity-id="isEdit ? initialData?.id : undefined"
          :field-key="field.key"
          :save-fn="getSaveFn(field.key)"
          @saved="handleImageSaved"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="submit" :disabled="idConflict || submitting">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ImageUploadField from './ImageUploadField.vue'
import { checkSchoolIdAvailable } from '../api/school'
import { checkClubIdAvailable } from '../api/club'
import { checkStudentIdAvailable, updateStudentAvatarSquare, updateStudentAvatarRectangle, updateStudentBodyImage } from '../api/student'

const props = defineProps<{
  modelValue: boolean
  entityType: 'school' | 'club' | 'student'
  isEdit: boolean
  initialData?: Record<string, any>
  parentId?: string
  schoolOptions?: { id: string; cn_name: string }[]
  clubOptions?: { id: string; cn_name: string }[]
  schoolName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'submit', data: Record<string, any>): void
  (e: 'school-change', schoolId: string): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const form = reactive<Record<string, any>>({
  id: '',
  en_name: '',
  cn_name: '',
  jp_name: '',
  kr_name: '',
  introduce: '',
  school_id: '',
  club_id: '',
  logo: '',
  beautify_logo: '',
  bg: '',
  avatar_square: '',
  avatar_rectangle: '',
  body_image: '',
})
const formRef = ref()
const idPreview = ref('')
const idConflict = ref(false)
const submitting = ref(false)

watch(() => form.en_name, (val) => {
  if (!props.isEdit && props.entityType === 'student') {
    idPreview.value = val ? deriveId(val) : ''
  }
})

const title = computed(() => {
  const action = props.isEdit ? '修改' : '新增'
  const map = { school: '学园', club: '部团', student: '学生' }
  return `${action}${map[props.entityType]}`
})

// 过滤后的部团选项（只显示当前学园下的部团）
const filteredClubOptions = computed(() => {
  if (!form.school_id) {
    return props.clubOptions || []
  }
  // 注意：这里需要父组件传递的 clubOptions 已经按学园过滤
  // 如果 clubOptions 是全部部团，需要在父组件中处理过滤
  return props.clubOptions || []
})

// 学园切换处理
function handleSchoolChange(schoolId: string) {
  form.club_id = ''  // 清空部团选择
  emit('school-change', schoolId)
}

// 获取图片保存函数
function getSaveFn(fieldKey: string) {
  if (props.entityType !== 'student') return undefined
  switch (fieldKey) {
    case 'avatar_square':
      return updateStudentAvatarSquare
    case 'avatar_rectangle':
      return updateStudentAvatarRectangle
    case 'body_image':
      return updateStudentBodyImage
    default:
      return undefined
  }
}

// 图片保存成功后的回调
function handleImageSaved() {
  // 可以在这里刷新数据或执行其他操作
}

const imageFields = computed(() => {
  if (props.entityType === 'school') {
    return [
      { key: 'logo', label: 'Logo' },
      { key: 'beautify_logo', label: '美化 Logo' },
      { key: 'bg', label: '背景图' },
    ]
  }
  if (props.entityType === 'club') {
    return [
      { key: 'logo', label: 'Logo' },
      { key: 'bg', label: '背景图' },
    ]
  }
  return [
    { key: 'avatar_square', label: '方形头像' },
    { key: 'avatar_rectangle', label: '矩形头像' },
    { key: 'body_image', label: '立绘' },
  ]
})

const rules = computed(() => {
  const r: Record<string, any> = {
    cn_name: [{ required: true, message: '中文名必填', trigger: 'blur' }],
  }
  if (props.entityType === 'student' && !props.isEdit) {
    r.en_name = [{ required: true, message: '英文名必填', trigger: 'blur' }]
  }
  if (!props.isEdit && props.entityType !== 'student') {
    r.id = [{ required: true, message: 'ID 必填', trigger: 'blur' }]
  }
  return r
})

function buildUploadUrl(field: string) {
  let entityId = ''
  if (props.isEdit) {
    entityId = props.initialData?.id || ''
  } else {
    entityId = props.entityType === 'student' ? idPreview.value : form.id
  }
  // 如果 entityId 为空，返回空字符串（禁用上传）
  if (!entityId) {
    return ''
  }
  const prefix = `${props.entityType}s/${entityId}/${field}`
  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  return `${apiBase}/api/file/upload?path=${encodeURIComponent(prefix)}`
}

function deriveId(enName: string) {
  return enName.toLowerCase().replace(/ /g, '_')
}

async function checkId() {
  if (props.isEdit) return
  let id = ''
  if (props.entityType === 'student') {
    if (!form.en_name) return
    id = deriveId(form.en_name)
    idPreview.value = id
  } else {
    id = form.id
    if (!id) return
    idPreview.value = id
  }
  let ok = false
  if (props.entityType === 'school') ok = await checkSchoolIdAvailable(id)
  else if (props.entityType === 'club') ok = await checkClubIdAvailable(id)
  else ok = await checkStudentIdAvailable(id)
  idConflict.value = !ok
}

watch(() => props.modelValue, (val) => {
  if (val) {
    Object.assign(form, props.initialData || {})
    if (!props.isEdit) {
      form.id = ''
      form.en_name = ''
      form.cn_name = ''
      form.jp_name = ''
      form.kr_name = ''
      form.introduce = ''
      form.school_id = props.entityType === 'club' ? (props.parentId || '') : ''
      form.club_id = props.entityType === 'student' ? (props.parentId || '') : ''
      imageFields.value.forEach(f => form[f.key] = '')
      idPreview.value = ''
      idConflict.value = false
    } else {
      idPreview.value = form.id || ''
      if (props.entityType === 'club') form.school_id = props.initialData?.school || props.parentId || ''
      if (props.entityType === 'student') {
        form.school_id = props.initialData?.school || ''
        form.club_id = props.initialData?.club || props.parentId || ''
      }
    }
  }
})

function handleClose() {
  visible.value = false
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  
  // 将空字符串转换为 null
  const toNull = (v: any) => (v === '' || v === undefined ? null : v)
  
  const payload: Record<string, any> = {
    id: form.id,
    en_name: form.en_name,
    cn_name: form.cn_name,
    jp_name: toNull(form.jp_name),
    kr_name: toNull(form.kr_name),
    introduce: toNull(form.introduce),
  }
  if (props.entityType === 'school') {
    payload.logo = toNull(form.logo)
    payload.beautify_logo = toNull(form.beautify_logo)
    payload.bg = toNull(form.bg)
  } else if (props.entityType === 'club') {
    payload.logo = toNull(form.logo)
    payload.bg = toNull(form.bg)
  }
  // 学生图片字段单独保存，不包含在表单提交中
  if (!props.isEdit && props.entityType === 'student') {
    payload.id = deriveId(form.en_name)
  }
  if (props.entityType === 'club') {
    payload.school = toNull(form.school_id)
  }
  if (props.entityType === 'student') {
    payload.school = toNull(form.school_id)
    payload.club = toNull(form.club_id)
  }
  emit('submit', payload)
}
</script>

<style scoped>
.id-preview {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}
.error-text {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}
</style>
