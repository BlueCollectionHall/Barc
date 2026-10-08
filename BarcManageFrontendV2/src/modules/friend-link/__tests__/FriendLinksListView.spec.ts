import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import FriendLinksListView from '@/modules/friend-link/views/FriendLinksListView.vue'
import type { FriendLinkRecord } from '@/modules/friend-link/api/friendLink.service'

/**
 * 友情链接管理页测试
 *
 * 覆盖：
 * 1、列表渲染（logo 有无两种形态、标题、链接、简介）；
 * 2、上移 / 下移的调用参数与首末位禁用，成功后就地替换列表；
 * 3、删除二次确认（取消不调接口）；
 * 4、新增表单：必填校验（标题 / 链接、链接前缀）、提交体组装；
 * 5、logo 上传走「先传后落」：上传成功写入 logo_key，pending 阶段由本地预览显示。
 */

const {
  fetchFriendLinks,
  uploadFriendLinkLogo,
  createFriendLink,
  updateFriendLink,
  moveFriendLink,
  deleteFriendLink,
  showError,
  showSuccess,
  confirm,
} = vi.hoisted(() => ({
  fetchFriendLinks: vi.fn(),
  uploadFriendLinkLogo: vi.fn(),
  createFriendLink: vi.fn(),
  updateFriendLink: vi.fn(),
  moveFriendLink: vi.fn(),
  deleteFriendLink: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/modules/friend-link/api/friendLink.service', () => ({
  fetchFriendLinks,
  uploadFriendLinkLogo,
  createFriendLink,
  updateFriendLink,
  moveFriendLink,
  deleteFriendLink,
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showSuccess,
  showInfo: vi.fn(),
  showWarning: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')

  return {
    ...actual,
    ElMessageBox: {
      confirm,
    },
    ElMessage: {
      error: vi.fn(),
      success: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    },
  }
})

function createRecord(
  id: string,
  title: string,
  sortOrder: number,
  overrides: Partial<FriendLinkRecord> = {},
): FriendLinkRecord {
  return {
    id,
    title,
    url: `https://${id}.example.com`,
    logo_key: `friendlink/logo/${id}.png`,
    intro: `${title} 的简介`,
    sort_order: sortOrder,
    enabled: true,
    created_by: 'admin-uuid',
    created_at: null,
    updated_at: null,
    logo_url: `https://signed/${id}.png`,
    ...overrides,
  }
}

/** 取某个 data-test 元素（找不到直接抛错，避免断言静默通过） */
function el(wrapper: ReturnType<typeof mount>, testId: string) {
  const found = wrapper.find(`[data-test="${testId}"]`)
  if (!found.exists()) {
    throw new Error(`未找到元素: ${testId}`)
  }
  return found
}

describe('FriendLinksListView', () => {
  beforeEach(() => {
    fetchFriendLinks.mockResolvedValue([
      createRecord('link-1', '蔚蓝同好会', 1),
      createRecord('link-2', 'Kivo Wiki', 2, { logo_key: null, logo_url: null }),
    ])
    uploadFriendLinkLogo.mockResolvedValue('friendlink/logo/new_1A2B3C4D_1758364800.png')
    createFriendLink.mockResolvedValue(createRecord('link-3', '新友链', 3))
    updateFriendLink.mockResolvedValue(createRecord('link-1', '蔚蓝同好会（改）', 1))
    moveFriendLink.mockResolvedValue([
      createRecord('link-2', 'Kivo Wiki', 1, { logo_key: null, logo_url: null }),
      createRecord('link-1', '蔚蓝同好会', 2),
    ])
    deleteFriendLink.mockResolvedValue(null)
    confirm.mockResolvedValue(undefined)
  })

  it('渲染友链列表：有 logo 显示图片，无 logo 显示占位', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    expect(fetchFriendLinks).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('蔚蓝同好会')
    expect(wrapper.text()).toContain('Kivo Wiki')
    expect(wrapper.text()).toContain('Kivo Wiki 的简介')
    // logo 有值时渲染 el-image（内部为 img）；无值时走占位，不渲染裂图
    const logoImages = wrapper.findAll('td .el-image')
    expect(logoImages).toHaveLength(1)
    expect(wrapper.findAll('.logo-placeholder')).toHaveLength(1)
    // 标题列逐个断言，避免与简介文案混在一起
    const titles = wrapper.findAll('td .title').map((node) => node.text())
    expect(titles).toEqual(['蔚蓝同好会', 'Kivo Wiki'])
    expect(showError).not.toHaveBeenCalled()
  })

  it('链接以新标签页打开并带 rel=noopener noreferrer', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    const link = wrapper.find('a.link')
    expect(link.attributes('href')).toBe('https://link-1.example.com')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
  })

  it('列表接口失败时提示错误且页面不崩', async () => {
    fetchFriendLinks.mockRejectedValue(new Error('boom'))
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('boom')
  })

  it('首行「上移」与末行「下移」被禁用', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    const upButtons = wrapper.findAll('[data-test="move-up"]')
    const downButtons = wrapper.findAll('[data-test="move-down"]')
    expect(upButtons).toHaveLength(2)
    expect(upButtons[0]!.attributes('disabled')).toBeDefined()
    expect(upButtons[1]!.attributes('disabled')).toBeUndefined()
    expect(downButtons[0]!.attributes('disabled')).toBeUndefined()
    expect(downButtons[1]!.attributes('disabled')).toBeDefined()
  })

  it('下移调用 moveFriendLink(id, "down") 并用返回的列表就地替换', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await wrapper.findAll('[data-test="move-down"]')[0]!.trigger('click')
    await flushPromises()

    expect(moveFriendLink).toHaveBeenCalledWith('link-1', 'down')
    expect(showSuccess).toHaveBeenCalled()
    // 返回顺序被交换：现在 Kivo Wiki 在前
    const titles = wrapper.findAll('td .title').map((node) => node.text())
    expect(titles[0]).toBe('Kivo Wiki')
  })

  it('移动失败时提示错误（例如已在最前 / 最后）', async () => {
    moveFriendLink.mockRejectedValue(new Error('该友链已经在最前'))
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await wrapper.findAll('[data-test="move-up"]')[1]!.trigger('click')
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('该友链已经在最前')
  })

  it('删除先二次确认；确认后调接口并重新拉列表', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await wrapper.findAll('[data-test="delete-friend-link"]')[0]!.trigger('click')
    await flushPromises()

    expect(confirm).toHaveBeenCalled()
    expect(deleteFriendLink).toHaveBeenCalledWith('link-1')
    expect(fetchFriendLinks).toHaveBeenCalledTimes(2)
  })

  it('删除时取消则不调接口', async () => {
    confirm.mockRejectedValue('cancel')
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await wrapper.findAll('[data-test="delete-friend-link"]')[0]!.trigger('click')
    await flushPromises()

    expect(deleteFriendLink).not.toHaveBeenCalled()
    expect(showError).not.toHaveBeenCalled()
  })

  it('新增：标题 / 链接为空时不提交', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await el(wrapper, 'create-friend-link').trigger('click')
    await flushPromises()

    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(createFriendLink).not.toHaveBeenCalled()
  })

  it('新增：链接缺少 http(s) 前缀时不提交', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await el(wrapper, 'create-friend-link').trigger('click')
    await flushPromises()
    await el(wrapper, 'form-title').setValue('某站')
    await el(wrapper, 'form-url').setValue('www.example.com')
    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(createFriendLink).not.toHaveBeenCalled()
  })

  it('新增：填写标题与链接后提交，payload 正确（logo / 简介为空时为 null）', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await el(wrapper, 'create-friend-link').trigger('click')
    await flushPromises()
    await el(wrapper, 'form-title').setValue('  某站  ')
    await el(wrapper, 'form-url').setValue('  https://example.com  ')
    await el(wrapper, 'form-intro').setValue('   ')
    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(createFriendLink).toHaveBeenCalledWith({
      title: '某站',
      url: 'https://example.com',
      logo_key: null,
      intro: null,
    })
    // 提交成功后重新拉列表
    expect(fetchFriendLinks).toHaveBeenCalledTimes(2)
  })

  it('编辑：回填原值，提交时调用 updateFriendLink', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await wrapper.findAll('[data-test="edit-friend-link"]')[0]!.trigger('click')
    await flushPromises()
    await el(wrapper, 'form-title').setValue('蔚蓝同好会（改）')
    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(updateFriendLink).toHaveBeenCalledWith('link-1', {
      title: '蔚蓝同好会（改）',
      url: 'https://link-1.example.com',
      logo_key: 'friendlink/logo/link-1.png',
      intro: '蔚蓝同好会 的简介',
    })
  })

  it('logo 上传：走独立上传接口，成功后写入 logo_key（先传后落）', async () => {
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await el(wrapper, 'create-friend-link').trigger('click')
    await flushPromises()

    // findComponent 传 CSS 选择器时返回的 WrapperLike 在类型上没有具体组件的 props 签名，
    // 这里统一断言成宽松类型以便读取 http-request（el-upload 的自定义上传入口）
    const upload = wrapper.findComponent('[data-test="upload-logo-field"]') as unknown as {
      props: (key: string) => unknown
    }
    const httpRequest = upload.props('httpRequest') as (options: {
      file: File
      onSuccess?: (response: unknown) => void
      onError?: (error: unknown) => void
    }) => Promise<void>

    await httpRequest({ file: new File(['x'], 'cover.png', { type: 'image/png' }) })
    await flushPromises()

    expect(uploadFriendLinkLogo).toHaveBeenCalledTimes(1)
    expect(showSuccess).toHaveBeenCalled()

    // 上传成功后提交：logo_key 使用上传接口返回的 objectKey
    await el(wrapper, 'form-title').setValue('带 logo 的友链')
    await el(wrapper, 'form-url').setValue('https://logo.example.com')
    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(createFriendLink).toHaveBeenCalledWith({
      title: '带 logo 的友链',
      url: 'https://logo.example.com',
      logo_key: 'friendlink/logo/new_1A2B3C4D_1758364800.png',
      intro: null,
    })
  })

  it('logo 上传失败时提示错误且不写 logo_key', async () => {
    uploadFriendLinkLogo.mockRejectedValue(new Error('图片大小不能超过 1MB'))
    const wrapper = mount(FriendLinksListView)
    await flushPromises()

    await el(wrapper, 'create-friend-link').trigger('click')
    await flushPromises()

    const upload = wrapper.findComponent('[data-test="upload-logo-field"]') as unknown as {
      props: (key: string) => unknown
    }
    const httpRequest = upload.props('httpRequest') as (options: { file: File }) => Promise<void>
    await httpRequest({ file: new File(['x'], 'cover.png', { type: 'image/png' }) })
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('图片大小不能超过 1MB')

    await el(wrapper, 'form-title').setValue('某站')
    await el(wrapper, 'form-url').setValue('https://example.com')
    await el(wrapper, 'submit-friend-link').trigger('click')
    await flushPromises()

    expect(createFriendLink).toHaveBeenCalledWith({
      title: '某站',
      url: 'https://example.com',
      logo_key: null,
      intro: null,
    })
  })
})
