import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createFriendLink,
  deleteFriendLink,
  fetchFriendLinks,
  moveFriendLink,
  updateFriendLink,
  uploadFriendLinkLogo,
} from '@/modules/friend-link/api/friendLink.service'

/**
 * 友情链接接口封装测试
 *
 * 重点：
 * 1、路径与方法必须与后端 Controller 契约一致（路径漂移会导致线上 404）；
 * 2、上传走 FormData 且字段名为 file（与后端 @RequestParam("file") 对齐）；
 * 3、上移 / 下移通过 params 传 direction，避免手拼 URL 的编码问题。
 */

const { get, post, put, delete: del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/shared/api/http', () => ({
  http: { get, post, put, delete: del },
}))

describe('friendLink.service', () => {
  beforeEach(() => {
    get.mockResolvedValue([])
    post.mockResolvedValue({})
    put.mockResolvedValue([])
    del.mockResolvedValue(null)
  })

  it('fetchFriendLinks 请求管理端列表接口', async () => {
    await fetchFriendLinks()
    expect(get).toHaveBeenCalledWith('/api/friend-link/admin/list')
  })

  it('uploadFriendLinkLogo 以 FormData + file 字段上传', async () => {
    const file = new File(['x'], 'cover.png', { type: 'image/png' })
    await uploadFriendLinkLogo(file)

    expect(post).toHaveBeenCalledTimes(1)
    const [url, form] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe('/api/friend-link/upload')
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('file')).toBe(file)
  })

  it('createFriendLink 提交完整 payload', async () => {
    const payload = {
      title: '某站',
      url: 'https://example.com',
      logo_key: 'friendlink/logo/a.png',
      intro: '简介',
    }
    await createFriendLink(payload)
    expect(post).toHaveBeenCalledWith('/api/friend-link/admin/create', payload)
  })

  it('updateFriendLink 走 PUT /admin/{id}', async () => {
    const payload = { title: '某站', url: 'https://example.com', logo_key: null, intro: null }
    await updateFriendLink('id-1', payload)
    expect(put).toHaveBeenCalledWith('/api/friend-link/admin/id-1', payload)
  })

  it('moveFriendLink 用 params 传 direction', async () => {
    await moveFriendLink('id-1', 'up')
    expect(put).toHaveBeenCalledWith('/api/friend-link/admin/id-1/move', undefined, {
      params: { direction: 'up' },
    })
  })

  it('deleteFriendLink 走 DELETE /admin/{id}', async () => {
    await deleteFriendLink('id-2')
    expect(del).toHaveBeenCalledWith('/api/friend-link/admin/id-2')
  })
})
