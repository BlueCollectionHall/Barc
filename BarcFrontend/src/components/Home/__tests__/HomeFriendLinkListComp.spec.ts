import { describe, expect, it } from 'vitest'
import { createSSRApp, defineComponent, h } from 'vue'
import { renderToString } from '@vue/server-renderer'

import HomeFriendLinkListComp from '@/components/Home/HomeFriendLinkListComp.vue'
import type { FriendLinkItem } from '@/utils/friendLinkApi.ts'

/**
 * 友情链接卡片列表（纯展示组件）测试
 *
 * 为什么用 renderToString：用户端没有 @vue/test-utils，vitest 跑在 node 环境（无 DOM），
 * 无法挂载组件；@vue/server-renderer 是 vue 自带的既有依赖，可直接在 node 下渲染出真实 HTML。
 *
 * 覆盖三条渲染约定：
 * 1、卡片链接以新标签页打开且带 rel=noopener noreferrer；
 * 2、logo_url 为空时不渲染卡片内 img（不裂图）；
 * 3、links 为空时不渲染任何卡片。
 */

const LINK_WITH_LOGO: FriendLinkItem = {
  title: '蔚蓝同好会',
  url: 'https://a.example.com',
  intro: '同好交流站点',
  logo_url: 'https://signed/a.png',
}

const LINK_WITHOUT_LOGO: FriendLinkItem = {
  title: 'Kivo Wiki',
  url: 'https://b.example.com',
  intro: null,
  logo_url: null,
}

async function render(list: FriendLinkItem[]): Promise<string> {
  const app = createSSRApp(
    defineComponent({
      setup() {
        return () => h(HomeFriendLinkListComp, { links: list })
      },
    }),
  )
  return await renderToString(app)
}

describe('HomeFriendLinkListComp', () => {
  it('渲染卡片：标题、简介、新标签页打开且带 noopener noreferrer', async () => {
    const html = await render([LINK_WITH_LOGO, LINK_WITHOUT_LOGO])

    expect(html).toContain('href="https://a.example.com"')
    expect(html).toContain('href="https://b.example.com"')
    expect(html).toContain('蔚蓝同好会')
    expect(html).toContain('Kivo Wiki')
    expect(html).toContain('同好交流站点')
    // 外链一律新标签页打开，且带 noopener noreferrer（防 tabnabbing）
    expect(html.match(/target="_blank"/g)).toHaveLength(2)
    expect(html.match(/rel="noopener noreferrer"/g)).toHaveLength(2)
  })

  it('logo_url 为空时不渲染卡片内的 img，避免裂图', async () => {
    const html = await render([LINK_WITH_LOGO, LINK_WITHOUT_LOGO])

    // 只有带 logo 的那条渲染 <img class="logo">
    expect(html.match(/class="logo"/g)).toHaveLength(1)
    expect(html).toContain('src="https://signed/a.png"')
    // 无 logo 的卡片仍然渲染标题与链接
    expect(html).toContain('Kivo Wiki')
  })

  it('links 为空时不渲染任何卡片', async () => {
    const html = await render([])

    expect(html).not.toContain('link_card')
    expect(html).not.toContain('href="https://')
  })
})
