import 'vue-router'

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
  }
}

export {}
