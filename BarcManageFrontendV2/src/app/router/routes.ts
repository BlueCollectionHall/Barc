import type { RouteRecordRaw } from 'vue-router'

import { MANAGER_PERMISSION } from '@/shared/constants/permissions'

const AdminLayout = () => import('@/app/layouts/AdminLayout.vue')
const LoginView = () => import('@/modules/auth/views/LoginView.vue')
const DashboardView = () => import('@/modules/dashboard/views/DashboardView.vue')
const UsersListView = () => import('@/modules/users/views/UsersListView.vue')
const UsersPermissionsView = () => import('@/modules/users/views/UsersPermissionsView.vue')
const NoticesListView = () => import('@/modules/notices/views/NoticesListView.vue')
const NoticeEditorView = () => import('@/modules/notices/views/NoticeEditorView.vue')
const ForbiddenView = () => import('@/modules/system/views/ForbiddenView.vue')

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
