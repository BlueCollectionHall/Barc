import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ElMessage, ElMessageBox, ElForm, ElFormItem } from 'element-plus'
import EntityFormDrawer from '../src/modules/school-club-student/components/EntityFormDrawer.vue'
import SchoolListView from '../src/modules/school-club-student/views/SchoolListView.vue'
import ClubListView from '../src/modules/school-club-student/views/ClubListView.vue'
import StudentListView from '../src/modules/school-club-student/views/StudentListView.vue'
import SchoolCard from '../src/modules/school-club-student/components/SchoolCard.vue'
import ClubCard from '../src/modules/school-club-student/components/ClubCard.vue'
import StudentCard from '../src/modules/school-club-student/components/StudentCard.vue'
import EntityBreadcrumb from '../src/modules/school-club-student/components/EntityBreadcrumb.vue'
import { defineComponent, h } from 'vue'
import { clickText, uiStubs } from './uiHarness'

const api = vi.hoisted(() => ({ fetchSchoolList: vi.fn(), fetchSchoolById: vi.fn(), createSchool: vi.fn(), updateSchool: vi.fn(), deleteSchool: vi.fn(), checkSchoolIdAvailable: vi.fn(), fetchClubList: vi.fn(), fetchClubById: vi.fn(), createClub: vi.fn(), updateClub: vi.fn(), deleteClub: vi.fn(), checkClubIdAvailable: vi.fn(), previewClubMove: vi.fn(), fetchStudentList: vi.fn(), fetchStudentById: vi.fn(), createStudent: vi.fn(), updateStudent: vi.fn(), deleteStudent: vi.fn(), checkStudentIdAvailable: vi.fn(), updateStudentAvatarSquare: vi.fn(), updateStudentAvatarRectangle: vi.fn(), updateStudentBodyImage: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('../src/modules/school-club-student/api/school', () => api)
vi.mock('../src/modules/school-club-student/api/club', () => api)
vi.mock('../src/modules/school-club-student/api/student', () => api)
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => ({ params: { schoolId: 'school', clubId: 'club' } }) }))
vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }))
enableAutoUnmount(afterEach)
const school = { id: 'school', en_name: 'School', cn_name: '学园' }
const club = { id: 'club', en_name: 'Club', cn_name: '部团', school: 'school' }
const student = { id: 'student', en_name: 'Student', cn_name: '学生', school: 'school', club: 'club', avatar_rectangle_key: 'images/portrait', body_image_key: 'images/body' }
beforeEach(() => {
  vi.resetAllMocks(); setActivePinia(createPinia()); vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm')
  api.fetchSchoolList.mockResolvedValue({ list: [school], total: 1 }); api.fetchSchoolById.mockResolvedValue(school)
  api.fetchClubList.mockResolvedValue({ list: [club], total: 1 }); api.fetchClubById.mockResolvedValue(club)
  api.fetchStudentList.mockResolvedValue({ list: [student], total: 1 }); api.fetchStudentById.mockResolvedValue(student)
  api.checkSchoolIdAvailable.mockResolvedValue(true); api.checkClubIdAvailable.mockResolvedValue(true); api.checkStudentIdAvailable.mockResolvedValue(true)
  api.previewClubMove.mockResolvedValue({ affected_students: 23 })
})
const Drawer = defineComponent({ name: 'ElDrawer', props: ['modelValue', 'title'], setup: (props, { slots }) => () => props.modelValue ? h('section', [h('header', props.title), slots.default?.(), slots.footer?.()]) : null })
const formStubs = { ...uiStubs, ElForm, ElFormItem, 'el-form-item': ElFormItem, 'el-drawer': Drawer, ImageUploadField: true }
it.each(['school', 'club', 'student'] as const)('新建 %s 从英文名生成 ID，只提交图片 key', async entityType => {
  const wrapper = mount(EntityFormDrawer, { props: { modelValue: false, entityType, isEdit: false, parentId: 'parent' }, global: { stubs: formStubs } })
  await wrapper.setProps({ modelValue: true }); await flushPromises()
  const inputs = wrapper.findAllComponents({ name: 'ElInput' })
  inputs[1]!.vm.$emit('update:modelValue', 'New Entity'); inputs[2]!.vm.$emit('update:modelValue', '新增资料'); await flushPromises()
  expect(inputs[0]!.props('modelValue')).toBe('new_entity')
  for (const image of wrapper.findAllComponents({ name: 'ImageUploadField' })) image.vm.$emit('update:modelValue', `objects/${image.props('fieldKey')}`)
  await flushPromises(); await clickText(wrapper, '保存')
  const payload = wrapper.emitted('submit')?.[0]?.[0] as Record<string, unknown>
  expect(payload).toMatchObject({ id: 'new_entity', en_name: 'New Entity', cn_name: '新增资料' })
  expect(Object.keys(payload).some(key => key.endsWith('_url'))).toBe(false)
  if (entityType === 'student') expect(payload).toMatchObject({ club: 'parent', avatar_rectangle_key: 'objects/avatar_rectangle', body_image_key: 'objects/body_image' })
  if (entityType === 'club') expect(payload.school).toBe('parent')
  await clickText(wrapper, '取消'); expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
})
it('重复 ID 与缺少学生必需图片都会阻止提交', async () => {
  const wrapper = mount(EntityFormDrawer, { props: { modelValue: false, entityType: 'student', isEdit: false }, global: { stubs: formStubs } })
  await wrapper.setProps({ modelValue: true }); await flushPromises()
  const inputs = wrapper.findAllComponents({ name: 'ElInput' }); inputs[1]!.vm.$emit('update:modelValue', 'New'); inputs[2]!.vm.$emit('update:modelValue', '新学生'); await flushPromises()
  api.checkStudentIdAvailable.mockResolvedValueOnce(false); await clickText(wrapper, '保存')
  expect(wrapper.text()).toContain('ID 已存在'); expect(wrapper.emitted('submit')).toBeUndefined()
  inputs[1]!.vm.$emit('update:modelValue', 'Other'); await flushPromises(); await clickText(wrapper, '保存')
  expect(ElMessage.error).toHaveBeenCalledWith('请上传学生矩形头像和立绘'); expect(wrapper.emitted('submit')).toBeUndefined()
})
it('编辑部团显示迁移学生数量，旧签名 URL 仅展示不写回', async () => {
  const wrapper = mount(EntityFormDrawer, { props: { modelValue: false, entityType: 'club', isEdit: true, initialData: { ...club, logo: 'https://old.example/logo', logo_key: 'objects/logo', logo_url: 'https://signed.example/logo' } }, global: { stubs: formStubs } })
  await wrapper.setProps({ modelValue: true }); await flushPromises()
  expect(wrapper.findAllComponents({ name: 'ElInput' })[1]!.props('disabled')).toBe(true)
  wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'new-school'); await flushPromises()
  expect(api.previewClubMove).toHaveBeenCalledWith('club', 'new-school'); expect(wrapper.text()).toContain('23 名学生')
  await clickText(wrapper, '保存')
  expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({ id: 'club', school: 'new-school', logo_key: 'objects/logo' })
  api.previewClubMove.mockRejectedValueOnce(new Error('无法预览')); wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'another-school'); await flushPromises()
  expect(ElMessage.error).toHaveBeenCalledWith('无法预览')
})
it('学生图片独立保存后刷新展示 URL，学园变更清空旧部团', async () => {
  api.fetchStudentById.mockResolvedValue({ ...student, avatar_rectangle_url: 'https://signed.example/new' })
  const wrapper = mount(EntityFormDrawer, { props: { modelValue: false, entityType: 'student', isEdit: true, initialData: student }, global: { stubs: formStubs } })
  await wrapper.setProps({ modelValue: true }); await flushPromises()
  const images = wrapper.findAllComponents({ name: 'ImageUploadField' })
  expect(images.map(i => i.props('saveFn'))).toEqual([api.updateStudentAvatarSquare, api.updateStudentAvatarRectangle, api.updateStudentBodyImage])
  images[1]!.vm.$emit('saved'); await flushPromises(); expect(images[1]!.props('previewUrl')).toBe('https://signed.example/new')
  const select = wrapper.findAllComponents({ name: 'ElSelect' })[0]!; select.vm.$emit('update:modelValue', 'new-school'); select.vm.$emit('change', 'new-school'); await flushPromises()
  expect(wrapper.emitted('school-change')).toEqual([['new-school']]); expect(wrapper.findAllComponents({ name: 'ElSelect' })[1]!.props('modelValue')).toBe('')
  await clickText(wrapper, '保存'); expect(wrapper.emitted('submit')?.[0]?.[0]).not.toHaveProperty('body_image_key')
})
const viewStubs = { ...uiStubs, EntityFormDrawer: true, SchoolCard: true, ClubCard: true, StudentCard: true, EntityBreadcrumb: true }
it('学园总览创建、编辑、导航、切换类型与学生关系提交保持一致', async () => {
  const wrapper = mount(SchoolListView, { global: { stubs: viewStubs } }); await flushPromises()
  const schoolCard = wrapper.findComponent({ name: 'SchoolCard' }); schoolCard.vm.$emit('click', 'school'); expect(router.push).toHaveBeenCalledWith({ name: 'clubs-list', params: { schoolId: 'school' } })
  await clickText(wrapper, '新增学园'); let drawer = wrapper.findComponent({ name: 'EntityFormDrawer' }); drawer.vm.$emit('submit', school); await flushPromises(); expect(api.createSchool).toHaveBeenCalledWith(school)
  schoolCard.vm.$emit('edit', 'school'); await flushPromises(); drawer.vm.$emit('submit', school); await flushPromises(); expect(api.updateSchool).toHaveBeenCalledWith('school', school)
  schoolCard.vm.$emit('delete', 'school'); await flushPromises(); expect(api.deleteSchool).toHaveBeenCalledWith('school')
  const modes = wrapper.findComponent({ name: 'ElRadioGroup' }); modes.vm.$emit('update:modelValue', 'club'); modes.vm.$emit('change', 'club'); await flushPromises()
  const clubCard = wrapper.findComponent({ name: 'ClubCard' }); clubCard.vm.$emit('click', 'club'); expect(router.push).toHaveBeenCalledWith({ name: 'students-list', params: { schoolId: 'school', clubId: 'club' } })
  clubCard.vm.$emit('edit', 'club'); await flushPromises(); drawer = wrapper.findComponent({ name: 'EntityFormDrawer' }); drawer.vm.$emit('submit', club); await flushPromises(); expect(api.updateClub).toHaveBeenCalledWith('club', club)
  clubCard.vm.$emit('delete', 'club'); await flushPromises(); expect(api.deleteClub).toHaveBeenCalledWith('club')
  modes.vm.$emit('update:modelValue', 'student'); modes.vm.$emit('change', 'student'); await flushPromises()
  const studentCard = wrapper.findComponent({ name: 'StudentCard' }); studentCard.vm.$emit('edit', 'student'); await flushPromises()
  drawer.vm.$emit('school-change', 'new-school'); await flushPromises(); expect(api.fetchClubList).toHaveBeenLastCalledWith('new-school', '', 1, 100)
  drawer.vm.$emit('submit', { ...student, school_id: 'forged', club_id: 'forged' }); await flushPromises()
  expect(api.updateStudent).toHaveBeenCalledWith('student', expect.not.objectContaining({ school: 'school' }), 'club')
  expect(api.updateStudent.mock.calls[0]![1]).not.toHaveProperty('club_id')
  studentCard.vm.$emit('delete', 'student'); await flushPromises(); expect(api.deleteStudent).toHaveBeenCalledWith('student')
})
it.each(['club', 'student'] as const)('%s 子列表正确加载父级、创建、修改和删除', async type => {
  const wrapper = mount(type === 'club' ? ClubListView : StudentListView, { global: { stubs: viewStubs } }); await flushPromises()
  expect(api.fetchSchoolById).toHaveBeenCalledWith('school')
  await clickText(wrapper, type === 'club' ? '新增部团' : '新增学生')
  const drawer = wrapper.findComponent({ name: 'EntityFormDrawer' }); const data = type === 'club' ? club : student
  drawer.vm.$emit('submit', data); await flushPromises()
  expect(type === 'club' ? api.createClub : api.createStudent).toHaveBeenCalledWith(data, type === 'club' ? 'school' : 'club')
  const card = wrapper.findComponent({ name: type === 'club' ? 'ClubCard' : 'StudentCard' }); card.vm.$emit('edit', data.id); await flushPromises()
  drawer.vm.$emit('submit', data); await flushPromises(); expect(type === 'club' ? api.updateClub : api.updateStudent).toHaveBeenCalled()
  card.vm.$emit('delete', data.id); await flushPromises(); expect(type === 'club' ? api.deleteClub : api.deleteStudent).toHaveBeenCalledWith(data.id)
  if (type === 'club') { card.vm.$emit('click', 'club'); expect(router.push).toHaveBeenCalledWith({ name: 'students-list', params: { schoolId: 'school', clubId: 'club' } }) }
  else { drawer.vm.$emit('school-change', 'new-school'); await flushPromises(); expect(api.fetchClubList).toHaveBeenLastCalledWith('new-school', '', 1, 100) }
  wrapper.findComponent({ name: 'ElPagination' }).vm.$emit('current-change', 2); await flushPromises()
  expect(type === 'club' ? api.fetchClubList : api.fetchStudentList).toHaveBeenLastCalledWith(type === 'club' ? 'school' : 'club', '', 2, 20)
})
it.each([SchoolCard, ClubCard, StudentCard])('基础资料卡片把编辑、删除与打开事件分别传递', async Card => {
  const wrapper = mount(Card, { props: { id: 'id', name: '资料' } })
  await wrapper.trigger('click'); expect(wrapper.emitted('click')).toEqual([['id']])
  const dropdown = wrapper.findComponent({ name: 'ElDropdown' }); dropdown.vm.$emit('command', 'edit'); dropdown.vm.$emit('command', 'delete'); dropdown.vm.$emit('command', 'ignored')
  expect(wrapper.emitted('edit')).toEqual([['id']]); expect(wrapper.emitted('delete')).toEqual([['id']])
})
it('层级导航返回对应上级', async () => {
  const wrapper = mount(EntityBreadcrumb, { props: { schoolId: 'school', schoolName: '学园', clubName: '部团' }, global: { stubs: uiStubs } })
  await clickText(wrapper, '返回'); expect(router.push).toHaveBeenLastCalledWith({ name: 'clubs-list', params: { schoolId: 'school' } })
  await wrapper.setProps({ clubName: '' }); await clickText(wrapper, '返回'); expect(router.push).toHaveBeenLastCalledWith({ name: 'schools-list' })
})
