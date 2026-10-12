/** 下拉选项逐页加载，适配后端单页 100 条上限；不再用超大 size 隐式请求全表。 */
export async function loadAllOptions<T extends { id: string }>(
  load: (page: number, size: number) => Promise<{ list: T[]; total: number }>,
): Promise<{ list: T[]; total: number }> {
  const list: T[] = []
  const seen = new Set<string>()
  for (let page = 1; page <= 10001; page += 1) {
    const result = await load(page, 100)
    const fresh = result.list.filter((item) => !seen.has(item.id))
    fresh.forEach((item) => seen.add(item.id))
    list.push(...fresh)
    if (list.length >= result.total) return { list, total: result.total }
    // 防止旧服务忽略页码或并发变化导致永远重复同一页，显式提示重试。
    if (fresh.length === 0) throw new Error('选项数据已变化，请重新加载')
  }
  throw new Error('选项过多，请缩小筛选范围')
}
