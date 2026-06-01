import type { RouteRecordRaw } from 'vue-router'

import { MANAGER_PERMISSION } from '@/shared/constants/permissions'

const AdminLayout = () => import('@/app/layouts/AdminLayout.vue')
const LoginView = () => import('@/modules/auth/views/LoginView.vue')
const DashboardView = () => import('@/modules/dashboard/views/DashboardView.vue')
const UsersListView = () => import('@/modules/users/views/UsersListView.vue')
const UsersPermissionsView = () => import('@/modules/users/views/UsersPermissionsView.vue')
const NoticesListView = () => import('@/modules/notices/views/NoticesListView.vue')
const NoticeEditorView = () => import('@/modules/notices/views/NoticeEditorView.vue')
const SchoolListView = () => import('@/modules/school-club-student/views/SchoolListView.vue')
const ClubListView = () => import('@/modules/school-club-student/views/ClubListView.vue')
const StudentListView = () => import('@/modules/school-club-student/views/StudentListView.vue')
const ForbiddenView = () => import('@/modules/system/views/ForbiddenView.vue')
const WorksListView = () => import('@/modules/works/views/WorksListView.vue')
const WorkDetailView = () => import('@/modules/works/views/WorkDetailView.vue')
const ClaimsListView = () => import('@/modules/works/views/ClaimsListView.vue')
const ComplaintsListView = () => import('@/modules/works/views/ComplaintsListView.vue')
const OperationLogView = () => import('@/modules/works/views/OperationLogView.vue')
const MessagesListView = () => import('@/modules/messages/views/MessagesListView.vue')

export const adminChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'dashboard',
    component: DashboardView,
    meta: {
      title: '控制台',
      subtitle: '把当前账号、模块入口与阶段重点集中在一个轻量首页。',
      requiresAuth: true,
      requiresManager: true,
      menuLabel: '仪表盘',
      menuGroup: 'dashboard',
      menuGroupLabel: '概览',
      groupOrder: 10,
      menuOrder: 10,
    },
  },
  {
    path: 'users/list',
    name: 'users-list',
    component: UsersListView,
    meta: {
      title: '用户列表',
      subtitle: '查看用户分页结果，并保留新建用户的入口。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: MANAGER_PERMISSION.THI_MAINTAINER,
      menuLabel: '用户列表',
      menuGroup: 'users',
      menuGroupLabel: '用户',
      groupOrder: 20,
      menuOrder: 10,
    },
  },
  {
    path: 'users/permissions',
    name: 'users-permissions',
    component: UsersPermissionsView,
    meta: {
      title: '权限调整',
      subtitle: '按真实权限选项管理账号角色，不再沿用 V1 的组合勾选误导。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: MANAGER_PERMISSION.THI_MAINTAINER,
      menuLabel: '权限调整',
      menuGroup: 'users',
      menuGroupLabel: '用户',
      groupOrder: 20,
      menuOrder: 20,
    },
  },
  {
    path: 'notices',
    name: 'notices-list',
    component: NoticesListView,
    meta: {
      title: '公告列表',
      subtitle: '延续 V1 的列表、新增、编辑节奏，但把交互收束到统一壳层。',
      requiresAuth: true,
      requiresManager: true,
      menuLabel: '公告列表',
      menuGroup: 'notices',
      menuGroupLabel: '公告',
      groupOrder: 30,
      menuOrder: 10,
    },
  },
  {
    path: 'notices/new',
    name: 'notices-new',
    component: NoticeEditorView,
    meta: {
      title: '发布公告',
      subtitle: '面向管理员的新公告编辑器。',
      requiresAuth: true,
      requiresManager: true,
      hiddenInMenu: true,
    },
  },
  {
    path: 'notices/:noticeId/edit',
    name: 'notices-edit',
    component: NoticeEditorView,
    meta: {
      title: '编辑公告',
      subtitle: '支持作者本人或更高权限管理员修改公告。',
      requiresAuth: true,
      requiresManager: true,
      hiddenInMenu: true,
    },
  },
  {
    path: 'schools',
    name: 'schools-list',
    component: SchoolListView,
    meta: {
      title: '学园列表',
      subtitle: '管理所有学园及其下属的部团与学生。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      menuLabel: '学园、部团、学生',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 50,
    },
  },
  {
    path: 'schools/:schoolId/clubs',
    name: 'clubs-list',
    component: ClubListView,
    meta: {
      title: '部团列表',
      subtitle: '管理该学园下的所有部团。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      hiddenInMenu: true,
    },
  },
  {
    path: 'schools/:schoolId/clubs/:clubId/students',
    name: 'students-list',
    component: StudentListView,
    meta: {
      title: '学生列表',
      subtitle: '管理该部团下的所有学生。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermission: 16,
      hiddenInMenu: true,
    },
  },
  {
    path: 'works/list',
    name: 'works-list',
    component: WorksListView,
    meta: {
      title: '作品管理',
      subtitle: '管理全部作品，支持封禁、下架、删除、恢复和内容修改。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.SEC_MAINTAINER,
      menuLabel: '作品管理',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 40,
    },
  },
  {
    path: 'works/:workId/detail',
    name: 'works-detail',
    component: WorkDetailView,
    meta: {
      title: '作品详情',
      subtitle: '查看作品详情，可执行封禁/下架/删除/恢复操作。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.SEC_MAINTAINER,
      hiddenInMenu: true,
    },
  },
  {
    path: 'works/claims',
    name: 'works-claims',
    component: ClaimsListView,
    meta: {
      title: '认领管理',
      subtitle: '审批认领申请、撤销认领、指派作者。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.SEC_MAINTAINER,
      menuLabel: '认领管理',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 41,
    },
  },
  {
    path: 'works/complaints',
    name: 'works-complaints',
    component: ComplaintsListView,
    meta: {
      title: '投诉处理',
      subtitle: '查看和处理作品投诉，处理时可联动封禁/下架/删除。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.SEC_MAINTAINER,
      menuLabel: '投诉处理',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 42,
    },
  },
  {
    path: 'works/logs',
    name: 'works-logs',
    component: OperationLogView,
    meta: {
      title: '操作日志',
      subtitle: '查看所有作品管理操作的审计日志。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.SEC_MAINTAINER,
      menuLabel: '操作日志',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 43,
    },
  },
  {
    path: 'messages/list',
    name: 'messages-list',
    component: MessagesListView,
    meta: {
      title: '留言管理',
      subtitle: '查看、搜索、编辑和删除用户留言板内容。',
      requiresAuth: true,
      requiresManager: true,
      minManagerPermissionBit: MANAGER_PERMISSION.FIR_MAINTAINER,
      menuLabel: '留言管理',
      menuGroup: 'content',
      menuGroupLabel: '内容管理',
      groupOrder: 50,
      menuOrder: 44,
    },
  },
  {
    path: '403',
    name: 'forbidden',
    component: ForbiddenView,
    meta: {
      title: '无权限',
      subtitle: '当前账号已登录，但缺少访问这个模块的能力。',
      requiresAuth: true,
      requiresManager: true,
      hiddenInMenu: true,
    },
  },
]

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录',
      subtitle: '仅 MANAGER 身份可进入 BARC 管理后台。',
      hiddenInMenu: true,
    },
  },
  {
    path: '/',
    component: AdminLayout,
    children: [
      {
        path: '',
        redirect: { name: 'dashboard' },
      },
      ...adminChildren,
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: { name: 'dashboard' },
  },
]
