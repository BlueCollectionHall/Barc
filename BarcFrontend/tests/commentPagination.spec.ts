import { beforeEach, describe, expect, it, vi } from 'vitest'
const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/utils/https.ts', () => ({ baseHttp: { get } }))
import { fetchCommentsPage, fetchRepliesPage } from '@/utils/commentApi'

/** 主评论和回复都向服务端翻页，旧的本地截断不能替代真实分页。 */
describe('评论分页', () => {
  beforeEach(() => get.mockReset())
  it('读取第二页主评论并保持总数', async () => {
    const page = { list: [], total: 99, page_num: 2, page_size: 10, total_page: 10 }
    get.mockResolvedValue({ data: { code: 0, data: page } })
    expect(await fetchCommentsPage('work-1', 2)).toEqual(page)
    expect(get).toHaveBeenCalledWith('/comment/work/page', { params: { work_id: 'work-1', page: 2, size: 10 } })
  })
  it('回复继续读取与首屏预览一致的分页容量', async () => {
    get.mockResolvedValue({ data: { code: 0, data: { list: [], total: 8 } } })
    await fetchRepliesPage('comment-1', 2)
    expect(get).toHaveBeenCalledWith('/comment/work/replies/page', { params: { parent_id: 'comment-1', page: 2, size: 5 } })
  })
  it('隐藏作品的拒绝响应不能当作空页成功', async () => {
    get.mockResolvedValue({ data: { code: 1, msg: '作品不可查看' } })
    await expect(fetchCommentsPage('private')).rejects.toThrow('作品不可查看')
  })
})
