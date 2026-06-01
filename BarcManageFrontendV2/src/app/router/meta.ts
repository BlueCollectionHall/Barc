export const ROUTE_REASON = {
  AUTH_REQUIRED: 'auth-required',
  MANAGER_ONLY: 'manager-only',
  SESSION_EXPIRED: 'session-expired',
} as const

export type RouteReason = (typeof ROUTE_REASON)[keyof typeof ROUTE_REASON]
