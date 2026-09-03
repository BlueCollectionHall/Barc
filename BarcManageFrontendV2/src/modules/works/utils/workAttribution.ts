import type { WorkRecord } from '@/modules/works/api/workManage'

type WorkAttributionRecord = Pick<
  WorkRecord,
  'is_claim' | 'author' | 'author_nickname' | 'author_display'
>

/**
 * “作品作者”与“平台归属”是两条不同语义：
 * 已认领作品的作者来自 author 账号；收录作品的原作者来自 author_nickname。
 */
export function getWorkCreatorDisplay(work: WorkAttributionRecord): string {
  if (work.is_claim) return work.author_display || work.author || '未知平台作者'
  return work.author_nickname || '未标注站外原作者'
}

export function getWorkCreatorSourceLabel(work: WorkAttributionRecord): string {
  return work.is_claim ? '平台作者' : '站外原作者'
}
