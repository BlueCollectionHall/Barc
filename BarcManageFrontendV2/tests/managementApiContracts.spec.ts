import { beforeEach, expect, it, vi } from 'vitest'
import * as schools from '../src/modules/school-club-student/api/school'
import * as clubs from '../src/modules/school-club-student/api/club'
import * as students from '../src/modules/school-club-student/api/student'
import * as comments from '../src/modules/works/api/commentManage'
import * as works from '../src/modules/works/api/workManage'
import * as messages from '../src/modules/messages/api/messages.service'
import * as users from '../src/modules/users/api/users.service'
import * as backgrounds from '../src/modules/backgrounds/api/backgrounds.service'
import * as festivals from '../src/modules/system/api/festivals.service'
import * as notices from '../src/modules/notices/api/notices.service'
import * as official from '../src/modules/users/api/officialAccount.service'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../src/shared/api/http', () => ({ http }))
beforeEach(() => { vi.resetAllMocks(); for (const fn of Object.values(http)) fn.mockResolvedValue('ok') })
const entity = { id: 'entity', en_name: 'Entity', cn_name: '资料', logo_key: 'objects/image' }
it('基础资料按父级真实分页，学生图片保存保持 key 且关系参数单独传递', async () => {
  await schools.fetchSchoolList('搜索', 2, 20); expect(http.get).toHaveBeenLastCalledWith('/api/school/list', { params: { keyword: '搜索', page: 2, size: 20 } })
  await clubs.fetchClubList('s', '', 2, 20); expect(http.get).toHaveBeenLastCalledWith('/api/club/list', { params: { school_id: 's', keyword: '', page: 2, size: 20 } })
  await clubs.fetchClubList('', '', 1, 100); expect(http.get.mock.lastCall?.[1].params).not.toHaveProperty('school_id')
  await students.fetchStudentList('c', '', 3, 20); expect(http.get.mock.lastCall?.[1].params).toMatchObject({ club_id: 'c', page: 3 })
  await students.fetchStudentList('', '', 1, 20); expect(http.get.mock.lastCall?.[1].params).not.toHaveProperty('club_id')
  await schools.createSchool(entity); expect(http.post).toHaveBeenLastCalledWith('/api/school', entity)
  await clubs.createClub(entity, 's'); expect(http.post).toHaveBeenLastCalledWith('/api/club', entity, { params: { school_id: 's' } })
  await students.createStudent(entity, 'c'); expect(http.post).toHaveBeenLastCalledWith('/api/student', entity, { params: { club_id: 'c' } })
  await schools.updateSchool('e', entity); expect(http.put).toHaveBeenLastCalledWith('/api/school/e', entity)
  await clubs.updateClub('e', { ...entity, school: 'new' }); expect(http.put.mock.lastCall?.[1]).toMatchObject({ school: 'new' })
  await students.updateStudent('e', entity, 'new-club'); expect(http.put).toHaveBeenLastCalledWith('/api/student/e', entity, { params: { club_id: 'new-club' } })
  await students.updateStudent('e', entity); expect(http.put.mock.lastCall?.[2]).toEqual({ params: undefined })
  for (const [field, save] of [['avatar_square', students.updateStudentAvatarSquare], ['avatar_rectangle', students.updateStudentAvatarRectangle], ['body_image', students.updateStudentBodyImage]] as const) {
    await save('e', 'objects/new'); expect(http.put).toHaveBeenLastCalledWith(`/api/student/e/${field}`, null, { params: { value: 'objects/new' } })
  }
  await clubs.previewClubMove('e', 's'); expect(http.get).toHaveBeenLastCalledWith('/api/club/e/move-preview', { params: { school_id: 's' } })
  for (const [prefix, get, check, remove] of [['school', schools.fetchSchoolById, schools.checkSchoolIdAvailable, schools.deleteSchool], ['club', clubs.fetchClubById, clubs.checkClubIdAvailable, clubs.deleteClub], ['student', students.fetchStudentById, students.checkStudentIdAvailable, students.deleteStudent]] as const) {
    await get('e'); expect(http.get).toHaveBeenLastCalledWith(`/api/${prefix}/e`)
    await check('e'); expect(http.get).toHaveBeenLastCalledWith(`/api/${prefix}/check_id_available`, { params: { id: 'e' } })
    await remove('e'); expect(http.delete).toHaveBeenLastCalledWith(`/api/${prefix}/e`)
  }
})
it('评论管理不再调用公开入口，删除回复和投诉目标各自有明确参数', async () => {
  await comments.getCommentsByWork('w', 3); expect(http.get).toHaveBeenLastCalledWith('/comment/work/manage/page', { params: { work_id: 'w', page: 3, size: 10 } })
  await comments.getManagementReplies('c', 2); expect(http.get).toHaveBeenLastCalledWith('/comment/work/manage/replies/page', { params: { parent_id: 'c', page: 2, size: 5 } })
  await comments.getCommentById('r'); expect(http.get).toHaveBeenLastCalledWith('/comment/work/manage/detail', { params: { comment_id: 'r' } })
  await comments.deleteWorkComment('c'); expect(http.delete).toHaveBeenLastCalledWith('/comment/work/delete_comment', { params: { comment_id: 'c' } })
  await comments.deleteWorkReply('r'); expect(http.delete).toHaveBeenLastCalledWith('/comment/work/delete_reply', { params: { reply_id: 'r' } })
  await comments.processCommentComplaint('f', 'COMPLETED', '完成'); expect(http.put).toHaveBeenLastCalledWith('/feedback/update', { id: 'f', type: 'COMMENT', status: 'COMPLETED', echo: '完成' })
  http.get.mockRejectedValueOnce(new Error('用户不存在')); await expect(comments.getUserArchive('u')).resolves.toBeNull()
  http.get.mockResolvedValueOnce({ nickname: '会员' }); await expect(comments.getUserArchive('u')).resolves.toEqual({ nickname: '会员' })
  await comments.getCommentComplaints(2); expect(http.get.mock.lastCall?.[1].params).toMatchObject({ type: 'COMMENT', page: 2, is_manager: true })
})
it('作品认领和审核契约保留证据页、版本与历史状态', async () => {
  await works.getWorkListManage({ page_num: 2, page_size: 10 }); expect(http.post.mock.lastCall?.[1]).toEqual({ page_num: 2, page_size: 10 })
  await works.getWorkDetailManage('w'); await works.getWorkEditDetail('w'); await works.getClaimWorkDetail('w'); expect(http.get).toHaveBeenLastCalledWith('/api/work/manage/claim/detail', { params: { work_id: 'w' } })
  await works.getClaimsPage(3, 'APPROVED', 'w'); expect(http.get.mock.lastCall?.[1].params).toEqual({ page: 3, size: 20, status: 'APPROVED', work_id: 'w' })
  await works.approveClaim('c', false); expect(http.post).toHaveBeenLastCalledWith('/api/work/manage/claim/approve', { claim_id: 'c', approved: false })
  await works.assignAuthor('w', 'u'); expect(http.post.mock.lastCall?.[1]).toEqual({ work_id: 'w', author_uuid: 'u' })
  await works.revokeClaim('w'); expect(http.post.mock.lastCall?.[1]).toEqual({ work_id: 'w' })
  await works.getClaimHistory('w'); expect(http.get.mock.lastCall?.[1].params).toEqual({ work_id: 'w' })
  await works.updateWorkStatus('w', 'PUBLIC', '恢复'); expect(http.put.mock.lastCall?.[1]).toEqual({ work_id: 'w', status: 'PUBLIC', remark: '恢复' })
  await works.updateWorkContent({ id: 'w', content: '正文' }); expect(http.put).toHaveBeenLastCalledWith('/api/work/manage/update', { id: 'w', content: '正文' })
  await works.searchUsers('name'); expect(http.get).toHaveBeenLastCalledWith('/user/query/search', { params: { keyword: 'name' } })
  await works.searchWorks('title'); expect(http.get).toHaveBeenLastCalledWith('/api/work/manage/search-work', { params: { keyword: 'title' } })
  await works.getOperationLogs({ page_num: 2, page_size: 20 }); expect(http.post.mock.lastCall?.[1]).toEqual({ page_num: 2, page_size: 20 })
})
it('留言批量删除使用 JSON 请求体，编辑和单删不会串用参数', async () => {
  await messages.fetchMessagesByPage({ page_num: 2, page_size: 15 }); expect(http.post).toHaveBeenLastCalledWith('/comment/message_board/admin/list', { page_num: 2, page_size: 15 })
  await messages.updateMessage({ message_id: 'm', content: '正文', reason: '说明' }); expect(http.put.mock.lastCall?.[1]).toEqual({ message_id: 'm', content: '正文', reason: '说明' })
  await messages.batchDeleteMessages({ message_ids: ['a', 'b'], reason: '说明' }); expect(http.delete).toHaveBeenLastCalledWith('/comment/message_board/admin/batch_delete', { data: { message_ids: ['a', 'b'], reason: '说明' } })
  await messages.deleteMessage('m'); expect(http.delete).toHaveBeenLastCalledWith('/comment/message_board/delete', { params: { message_id: 'm' } })
})
it('背景上传使用 multipart，重排提交完整列表，节日选项保留停用项', async () => {
  const file = new File(['png'], 'image.png', { type: 'image/png' }); await backgrounds.uploadBackground(file, 'home')
  const body = http.post.mock.lastCall?.[1] as FormData; expect(body.get('file')).toBe(file); expect(body.get('module')).toBe('home')
  await backgrounds.fetchBackgroundModules(); expect(http.get).toHaveBeenLastCalledWith('/api/background/modules')
  await backgrounds.fetchBackgroundList('home'); expect(http.get.mock.lastCall?.[1].params).toEqual({ module: 'home' })
  const order = [{ id: 'a', sort_order: 1 }, { id: 'b', sort_order: 2 }]; await backgrounds.reorderBackground(order); expect(http.put).toHaveBeenLastCalledWith('/api/background/reorder', order)
  await backgrounds.setBackgroundEnabled('a', false); expect(http.put.mock.lastCall?.[2]).toEqual({ params: { enabled: false } })
  await backgrounds.updateBackgroundScene('a', null, 'festival'); expect(http.put.mock.lastCall?.[1]).toEqual({ time_period: null, festival: 'festival' })
  await backgrounds.deleteBackground('a'); expect(http.delete).toHaveBeenLastCalledWith('/api/background/a')
  http.get.mockResolvedValueOnce([{ id: 'f', name: '节日', enabled: false }, { id: 'g', name: '节日二', enabled: true }])
  await expect(backgrounds.fetchFestivalSceneOptions()).resolves.toEqual([{ value: null, label: '无' }, { value: 'f', label: '节日（已关闭）', disabled: true }, { value: 'g', label: '节日二', disabled: false }])
  http.get.mockRejectedValueOnce(new Error('失败')); await expect(backgrounds.fetchFestivalSceneOptions()).resolves.toEqual([{ value: null, label: '无' }])
})
it('节日、公告及官方账号请求保留管理契约', async () => {
  const festival = { id: 'f', name: '节日', start_month: 1, start_day: 1, end_month: 1, end_day: 2, enabled: true, priority: 0 }
  await festivals.fetchFestivals(); await festivals.fetchFestivalOptions(); expect(http.get).toHaveBeenLastCalledWith('/api/festival/options')
  await festivals.createFestival(festival); expect(http.post.mock.lastCall?.[1]).toEqual(festival)
  await festivals.updateFestival('f', festival); expect(http.put).toHaveBeenLastCalledWith('/api/festival/admin/f', festival)
  await festivals.setFestivalEnabled('f', false); expect(http.put.mock.lastCall?.[2]).toEqual({ params: { enabled: false } })
  await festivals.deleteFestival('f'); expect(http.delete).toHaveBeenLastCalledWith('/api/festival/admin/f')
  const notice = { id: 'n', title: '公告', content: '<p>正文</p>', author: 'u', created_at: '', updated_at: '' }
  await notices.fetchNoticesByPage({ page_num: 1, page_size: 10 }); await notices.fetchNotice('n'); expect(http.get.mock.lastCall?.[1].params).toEqual({ notice_id: 'n' })
  await notices.createNotice(notice); await notices.updateNotice(notice); expect(http.put).toHaveBeenLastCalledWith('/notice/update', notice)
  await notices.deleteNotice('n'); expect(http.delete.mock.lastCall?.[1].params).toEqual({ notice_id: 'n' })
  await official.fetchOfficialAccounts(); expect(http.get).toHaveBeenLastCalledWith('/user/official/list')
  await official.searchOfficialAccountCandidates('u'); expect(http.get).toHaveBeenLastCalledWith('/user/query/search', { params: { keyword: 'u' } })
})
it('用户管理分页、权限与封禁使用 UUID，而非展示用户名', async () => {
  await users.queryUsersByPage({ page_num: 2, page_size: 20, params: { gender: 'OTHER' } }); expect(http.post.mock.lastCall?.[1]).toMatchObject({ page_num: 2 })
  await users.fetchAllIdentities(); await users.fetchPermissionsByIdentity('MANAGER'); expect(http.get.mock.lastCall?.[1].params).toEqual({ identity: 'MANAGER' })
  await users.fetchMyPermissionNearMax(); expect(http.get).toHaveBeenLastCalledWith('/user/permission/me_permission_near_max')
  await users.banUser({ userId: 'uuid', banType: 1, reason: '说明', durationDays: 3 }); expect(http.post.mock.lastCall?.[1]).toMatchObject({ userId: 'uuid', durationDays: 3 })
  await users.unbanUser({ userId: 'uuid', reason: '解除' }); expect(http.post).toHaveBeenLastCalledWith('/user/ban/unban', { userId: 'uuid', reason: '解除' })
  await users.getBanHistory('uuid', 2, 15); expect(http.get.mock.lastCall?.[1].params).toEqual({ userId: 'uuid', page: 2, size: 15 })
  await users.getUserBanStatus('uuid'); expect(http.get.mock.lastCall?.[1].params).toEqual({ userId: 'uuid' })
})
