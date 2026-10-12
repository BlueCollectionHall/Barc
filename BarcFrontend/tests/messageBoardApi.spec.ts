import { describe, expect, it, vi } from 'vitest'
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/utils/https', () => ({ baseHttp: { get, post } }))
import { fetchBoardPage, postBoardMessage } from '@/utils/messageBoardApi'

/** 留言新契约避免 GET 写入及不可达的历史页。 */
describe('留言接口', () => {
  it('提交正文与认证头', async () => {
    post.mockResolvedValue({ data: { code: 0 } })
    await postBoardMessage('留言', 'token')
    expect(post).toHaveBeenCalledWith('/comment/message_board/upload', { content: '留言' }, { headers: { Authorization: 'token' } })
  })
  it('读取后续页且保留总数', async () => {
    get.mockResolvedValue({ data: { code: 0, data: { list: [], total: 500 } } })
    expect((await fetchBoardPage(3)).total).toBe(500)
    expect(get).toHaveBeenCalledWith('/comment/message_board/page', { params: { page: 3, size: 20 } })
  })
})
