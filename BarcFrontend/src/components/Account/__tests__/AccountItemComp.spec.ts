import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRenderer, defineComponent, h, nextTick, reactive } from 'vue'
import type { App, Ref } from 'vue'

import * as AccountItemModule from '../AccountItemComp.vue'

interface TestElement {
  type: string
  props: Record<string, unknown>
  children: Array<TestElement | TestText | string>
  parent: TestElement | null
}

interface TestText {
  type: 'text' | 'comment'
  text: string
  parent: TestElement | null
}

interface AccountItem {
  label: string
  value: string
  icon: string
  color: string
}

interface AccountItemController {
  selectedItem: Ref<string>
  changeSelect: (target: string) => void
}

type AccountItemExports = typeof AccountItemModule & {
  accountItemList?: AccountItem[]
  useAccountItemController?: (options: {
    route: { query: Record<string, unknown> }
    router: { replace: (target: unknown) => void }
    fetchWorks: (username: string) => void
    reportError: (message: string) => void
  }) => AccountItemController
}

const accountItemExports = AccountItemModule as AccountItemExports

const renderer = createRenderer<TestElement, TestElement>({
  patchProp(el, key, _prevValue, nextValue) {
    el.props[key] = nextValue
  },
  insert(child, parent) {
    child.parent = parent
    parent.children.push(child)
  },
  remove(child) {
    const parent = child.parent
    if (!parent) return
    parent.children = parent.children.filter(item => item !== child)
    child.parent = null
  },
  createElement(type) {
    return { type, props: {}, children: [], parent: null }
  },
  createText(text) {
    return { type: 'text', text, parent: null }
  },
  createComment(text) {
    return { type: 'comment', text, parent: null }
  },
  setText(node, text) {
    node.text = text
  },
  setElementText(node, text) {
    node.children = [text]
  },
  parentNode(node) {
    return node.parent
  },
  nextSibling() {
    return null
  },
})

const createRoot = (): TestElement => ({
  type: 'root',
  props: {},
  children: [],
  parent: null,
})

const mountController = async (query: Record<string, unknown>) => {
  const route = reactive({ query })
  const router = { replace: vi.fn() }
  const fetchWorks = vi.fn()
  const reportError = vi.fn()
  let controller: AccountItemController | null = null

  const Harness = defineComponent({
    setup() {
      if (!accountItemExports.useAccountItemController) {
        throw new Error('useAccountItemController export is required')
      }
      controller = accountItemExports.useAccountItemController({ route, router, fetchWorks, reportError })
      return () => h('div')
    },
  })

  const app: App = renderer.createApp(Harness)
  const root = createRoot()
  app.mount(root)
  await nextTick()

  if (!controller) throw new Error('controller was not created')

  return { app, route, router, fetchWorks, reportError, controller }
}

describe('AccountItemComp account item behavior', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exposes the 喜欢 tab immediately after 收录集', () => {
    expect(accountItemExports.accountItemList?.map(item => item.label)).toEqual(['作品集', '收录集', '喜欢'])
  })

  it('clears stale page_num before replacing the route query when switching tabs', async () => {
    const { app, controller, router } = await mountController({ username: 'alice', type: 'works', page_num: '5' })
    router.replace.mockClear()

    controller.changeSelect('likes')

    expect(router.replace).toHaveBeenCalledWith({
      query: {
        username: 'alice',
        type: 'likes',
      },
    })

    app.unmount()
  })

  it('uses route.query.type for the initially selected tab', async () => {
    const { app, controller } = await mountController({ username: 'alice', type: 'likes' })

    expect(controller.selectedItem.value).toBe('likes')

    app.unmount()
  })

  it('updates the selected tab when route.query.type changes externally', async () => {
    const { app, route, controller } = await mountController({ username: 'alice', type: 'works' })

    route.query = { username: 'alice', type: 'likes' }
    await nextTick()

    expect(controller.selectedItem.value).toBe('likes')

    app.unmount()
  })

  it('does not fetch on mount when username is missing and reports an error', async () => {
    const { app, fetchWorks, reportError } = await mountController({ type: 'likes' })

    expect(fetchWorks).not.toHaveBeenCalled()
    expect(reportError).toHaveBeenCalledWith('页面缺少重要数据！')

    app.unmount()
  })
})
