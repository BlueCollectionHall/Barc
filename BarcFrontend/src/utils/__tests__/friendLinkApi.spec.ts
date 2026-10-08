import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchFriendLinks } from '@/utils/friendLinkApi.ts'

/**
 * 友链公开接口封装测试
 *
 * 关注点：
 * 1、只有 code === 0 且 data 是数组时才返回数据；
 * 2、非 0 code、data 非数组、请求抛错三种情况一律兜底为空数组
 *    —— 首页据此保持「只有标题、没有卡片」的降级形态，不出现报错。
 */

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/https.ts', () => ({
  baseHttp: { get },
}))

describe('fetchFriendLinks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('code=0 且 data 为数组时返回友链列表', async () => {
    const list = [{ title: 'A', url: 'https://a.com', intro: null, logo_url: null }]
    get.mockResolvedValue({ data: { code: 0, data: list } })

    await expect(fetchFriendLinks()).resolves.toEqual(list)
    expect(get).toHaveBeenCalledWith('/api/friend-link/all')
  })

  it('code 非 0 时兜底为空数组', async () => {
    get.mockResolvedValue({ data: { code: 1, data: null } })
    await expect(fetchFriendLinks()).resolves.toEqual([])
  })

  it('data 不是数组时兜底为空数组', async () => {
    get.mockResolvedValue({ data: { code: 0, data: { unexpected: true } } })
    await expect(fetchFriendLinks()).resolves.toEqual([])
  })

  it('请求异常时兜底为空数组（不向调用方抛错）', async () => {
    get.mockRejectedValue(new Error('network down'))
    await expect(fetchFriendLinks()).resolves.toEqual([])
  })
})
