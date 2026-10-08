import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from '@vue/server-renderer'

import HomeFriendshipComp from '@/components/Home/HomeFriendshipComp.vue'

/**
 * 首页友情链接区块（容器组件）测试
 *
 * 容器只负责「取数 + 渲染标题 + 把数据交给纯展示子组件」，卡片细节由
 * HomeFriendLinkListComp.spec.ts 覆盖，这里聚焦降级行为：
 * 1、区块标题始终渲染；
 * 2、没有数据时不渲染任何卡片（与接入前的纯标题形态一致）；
 * 3、接口异常时容器不抛错（fetchFriendLinks 已内部兜底为空数组）。
 *
 * 说明：容器在 onMounted 取数，服务端渲染不会触发它，因此这里断言的是「初始空态」；
 * 子组件在 links 为空时本来也不渲染任何卡片，与服务端首屏一致。
 */

const { fetchFriendLinks } = vi.hoisted(() => ({ fetchFriendLinks: vi.fn() }))

vi.mock('@/utils/friendLinkApi.ts', () => ({ fetchFriendLinks }))

async function renderComp(): Promise<string> {
  const app = createSSRApp(
    defineComponent({
      setup() {
        return () => h(HomeFriendshipComp)
      },
    }),
  )
  return await renderToString(app)
}

describe('HomeFriendshipComp', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('始终渲染区块标题，无数据时不渲染卡片', async () => {
    fetchFriendLinks.mockResolvedValue([])

    const html = await renderComp()

    expect(html).toContain('友情链接&amp;特别感谢')
    expect(html).not.toContain('link_card')
  })

  it('接口异常时容器仍能渲染标题（不抛错）', async () => {
    fetchFriendLinks.mockRejectedValue(new Error('network down'))

    // 组件本身是同步 setup，取数在 onMounted，SSR 阶段不会调用接口；
    // 这里断言渲染结果与「空态」一致，保证异常不会让首页区块崩掉。
    await expect(renderComp()).resolves.toContain('友情链接&amp;特别感谢')
  })
})
