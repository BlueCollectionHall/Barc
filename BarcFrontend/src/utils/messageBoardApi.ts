import { baseHttp } from '@/utils/https'
import type { ResponseImpl } from '@/interfaces/ResponseImpl'
import type { MessageBoardImpl } from '@/interfaces/MessageBoardImpl'

/** 留言内容放入请求体，列表按服务端真实分页读取。 */
export async function postBoardMessage(content: string, token: string): Promise<void> {
  const res = await baseHttp.post<ResponseImpl>('/comment/message_board/upload', { content }, { headers: { Authorization: token } })
  if (res.data.code !== 0) throw new Error(res.data.msg || '留言失败')
}

export async function fetchBoardPage(page: number): Promise<{ list: MessageBoardImpl[]; total: number }> {
  const res = await baseHttp.get<ResponseImpl>('/comment/message_board/page', { params: { page, size: 20 } })
  if (res.data.code !== 0) throw new Error(res.data.msg || '加载留言失败')
  return res.data.data as { list: MessageBoardImpl[]; total: number }
}
