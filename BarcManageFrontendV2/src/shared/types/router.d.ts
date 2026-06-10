import 'vue-router'

import type { FeedbackType } from '@/modules/feedback/api/feedbackManage'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    subtitle?: string
    requiresAuth?: boolean
    requiresManager?: boolean
    hiddenInMenu?: boolean
    menuLabel?: string
    menuGroup?: string
    menuGroupLabel?: string
    menuOrder?: number
    groupOrder?: number
    minManagerPermission?: number
    minManagerPermissionBit?: number
    feedbackType?: FeedbackType
  }
}

export {}
