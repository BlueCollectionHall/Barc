import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, type PropType } from 'vue'

import NoticeEditorView from '@/modules/notices/views/NoticeEditorView.vue'

type QuillToolbarValue = Array<boolean | number | string> | boolean | number | string
type QuillToolbarItem = Record<string, QuillToolbarValue> | string
type QuillToolbarGroup = QuillToolbarItem[]

interface QuillOptionsLike {
  modules?: {
    toolbar?: QuillToolbarGroup[]
  }
  placeholder?: string
}

function describeToolbarItem(item: QuillToolbarItem): string {
  if (typeof item === 'string') {
    return item
  }

  const entry = Object.entries(item)[0]
  if (!entry) {
    return 'unknown'
  }

  const [name, value] = entry

  if (Array.isArray(value)) {
    return `${name}:${value.length > 0 ? value.join('|') : 'default'}`
  }

  return `${name}:${String(value)}`
}

function flattenToolbarOptions(options?: QuillOptionsLike): string[] {
  return (options?.modules?.toolbar ?? []).flatMap((group) => group.map(describeToolbarItem))
}

const {
  routeState,
  replace,
  back,
  messageBoxConfirm,
  createNotice,
  fetchNotice,
  updateNotice,
  showError,
  showSuccess,
} = vi.hoisted(() => ({
  routeState: {
    params: {} as Record<string, unknown>,
    query: {} as Record<string, unknown>,
  },
  replace: vi.fn(),
  back: vi.fn(),
  messageBoxConfirm: vi.fn(),
  createNotice: vi.fn(),
  fetchNotice: vi.fn(),
  updateNotice: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    replace,
    back,
  }),
  onBeforeRouteLeave: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessageBox: {
      ...actual.ElMessageBox,
      confirm: messageBoxConfirm,
    },
  }
})

vi.mock('@vueup/vue-quill', () => ({
  QuillEditor: defineComponent({
    name: 'QuillEditor',
    props: {
      content: {
        type: String,
        default: '',
      },
      options: {
        type: Object as PropType<QuillOptionsLike | undefined>,
        default: undefined,
      },
    },
    emits: ['update:content'],
    setup(props, { emit }) {
      return () =>
        h('div', { 'data-testid': 'quill-editor-shell' }, [
          h(
            'div',
            { 'data-testid': 'quill-toolbar' },
            flattenToolbarOptions(props.options).map((control) =>
              h('span', { key: control, 'data-testid': 'quill-toolbar-control' }, control),
            ),
          ),
          h('div', { 'data-testid': 'quill-placeholder' }, props.options?.placeholder ?? ''),
          h('textarea', {
            'data-testid': 'quill-editor',
            value: props.content,
            onInput: (event) => {
              emit('update:content', (event.target as HTMLTextAreaElement).value)
            },
          }),
        ])
    },
  }),
}))

vi.mock('@/modules/notices/api/notices.service', () => ({
  createNotice,
  fetchNotice,
  updateNotice,
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showInfo: vi.fn(),
  showSuccess,
  showWarning: vi.fn(),
}))

function findButtonByText(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === label)

  if (!button) {
    throw new Error(`Unable to find button: ${label}`)
  }

  return button
}

function createDeferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void

  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })

  return { promise, resolve }
}

function readToolbarControls(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('[data-testid="quill-toolbar-control"]').map((candidate) => candidate.text())
}

async function settleNoticeEditorView(): Promise<void> {
  await vi.dynamicImportSettled()
  await flushPromises()
}

describe('NoticeEditorView', () => {
  beforeEach(() => {
    routeState.params = {}
    routeState.query = {}
    replace.mockReset()
    replace.mockResolvedValue(undefined)
    back.mockReset()
    messageBoxConfirm.mockReset()
    messageBoxConfirm.mockResolvedValue('confirm')
    createNotice.mockReset()
    createNotice.mockResolvedValue('公告发布成功')
    fetchNotice.mockReset()
    fetchNotice.mockResolvedValue({
      id: 'notice-1',
      title: 'Original Title',
      content: '<p>Original</p>',
      author: 'manager-1',
      created_at: '2026-04-18T10:00:00Z',
      updated_at: '2026-04-18T10:30:00Z',
    })
    updateNotice.mockReset()
    updateNotice.mockResolvedValue('公告更新成功')
    showError.mockReset()
    showSuccess.mockReset()
  })

  it('shows the richer safe-format authoring surface and content-status cue', async () => {
    const wrapper = mount(NoticeEditorView)

    await settleNoticeEditorView()

    expect(readToolbarControls(wrapper)).toEqual(
      expect.arrayContaining([
        'header:1|2|3|false',
        'bold',
        'italic',
        'underline',
        'strike',
        'blockquote',
        'code-block',
        'list:ordered',
        'list:bullet',
        'indent:-1',
        'indent:+1',
        'align:default',
        'link',
        'clean',
      ]),
    )
    expect(wrapper.get('[data-testid="quill-placeholder"]').text()).toContain('公告对象')

    const guidance = wrapper.get('[data-testid="editor-guidance"]').text()
    expect(guidance).toContain('提交前会自动清洗富文本')
    expect(guidance).toContain('脚本、危险链接和嵌入不会保留')
    expect(guidance).toContain('公告对象、执行时间、影响范围')

    expect(wrapper.get('[data-testid="editor-content-status"]').text()).toContain('正文待补充')

    await wrapper.find('[data-testid="quill-editor"]').setValue('<p>晚间值班调整</p>')
    await flushPromises()

    expect(wrapper.get('[data-testid="editor-content-status"]').text()).toContain('正文已就绪')
    expect(wrapper.get('[data-testid="editor-content-status"]').text()).toContain('后端安全规则清洗')
  })

  it('sanitizes create payload content and returns to the list with page context', async () => {
    routeState.query = { page: '3' }

    const wrapper = mount(NoticeEditorView)

    await settleNoticeEditorView()

    await wrapper.find('input').setValue('  Phase 1 Update  ')
    await wrapper.find('[data-testid="quill-editor"]').setValue('<p>Hello</p><script>alert(1)</script>')
    await findButtonByText(wrapper, '确认发布').trigger('click')
    await flushPromises()

    expect(createNotice).toHaveBeenCalledWith({
      title: 'Phase 1 Update',
      content: '<p>Hello</p>',
    })
    expect(showSuccess).toHaveBeenCalledWith('公告发布成功')
    expect(replace).toHaveBeenCalledWith({
      name: 'notices-list',
      query: {
        page: '3',
        noticeAction: 'created',
      },
    })
  })

  it('treats markup-only editor output as empty content before submit', async () => {
    const wrapper = mount(NoticeEditorView)

    await settleNoticeEditorView()

    await wrapper.find('input').setValue('Valid title')
    await wrapper.find('[data-testid="quill-editor"]').setValue('<p><br></p>')
    await flushPromises()

    expect(wrapper.get('[data-testid="editor-content-status"]').text()).toContain('正文待补充')

    await findButtonByText(wrapper, '确认发布').trigger('click')
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('公告内容不能为空。')
    expect(createNotice).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
  })

  it('keeps the editor locked until edit data finishes loading', async () => {
    routeState.params = {
      noticeId: 'notice-1',
    }

    const deferred = createDeferred<{
      id: string
      title: string
      content: string
      author: string
      created_at: string
      updated_at: string
    }>()
    fetchNotice.mockReturnValueOnce(deferred.promise)

    const wrapper = mount(NoticeEditorView)

    await settleNoticeEditorView()

    expect(wrapper.find('[data-testid="editor-loading"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="quill-editor"]').exists()).toBe(false)

    deferred.resolve({
      id: 'notice-1',
      title: 'Loaded title',
      content: '<p>Loaded</p>',
      author: 'manager-1',
      created_at: '2026-04-18T10:00:00Z',
      updated_at: '2026-04-18T10:30:00Z',
    })
    await settleNoticeEditorView()

    expect(wrapper.find('[data-testid="editor-loading"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="quill-editor"]').exists()).toBe(true)
  })

  it('asks for confirmation before canceling a dirty draft', async () => {
    routeState.query = { page: '2' }
    messageBoxConfirm.mockRejectedValueOnce('cancel')

    const wrapper = mount(NoticeEditorView)

    await settleNoticeEditorView()
    await wrapper.find('input').setValue('Unsaved title')
    await findButtonByText(wrapper, '取消返回').trigger('click')
    await flushPromises()

    expect(messageBoxConfirm).toHaveBeenCalledWith(
      expect.stringContaining('未保存的修改'),
      '放弃公告编辑',
      expect.any(Object),
    )
    expect(replace).not.toHaveBeenCalled()
  })

  it('redirects back to the notices list when an edit load fails', async () => {
    routeState.params = {
      noticeId: 'missing-notice',
    }
    routeState.query = { page: '4' }
    fetchNotice.mockRejectedValueOnce(new Error('公告不存在'))

    mount(NoticeEditorView)
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('公告不存在')
    expect(replace).toHaveBeenCalledWith({
      name: 'notices-list',
      query: { page: '4' },
    })
  })
})
