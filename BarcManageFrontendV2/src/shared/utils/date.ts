const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) {
    return '—'
  }

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }

  return dateFormatter.format(date)
}

export function formatRelativeGreeting(date = new Date()): string {
  const hour = date.getHours()

  if (hour >= 21 || hour < 5) {
    return '夜深了，别忘了给自己留一点收尾时间。'
  }

  if (hour < 8) {
    return '早安，今天的后台巡检从容开始。'
  }

  if (hour < 12) {
    return '上午好，先把重要事项放到视线中央。'
  }

  if (hour < 18) {
    return '下午好，公告、权限与流程都在这里。'
  }

  return '傍晚好，收束今天的管理工作吧。'
}
