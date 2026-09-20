import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import OfficialAccountsView from '@/modules/users/views/OfficialAccountsView.vue'
import type { OfficialAccountItem } from '@/shared/types/officialAccount'

const {
  fetchOfficialAccounts,
  bindOfficialAccount,
  searchOfficialAccountCandidates,
  showError,
  showSuccess,
  confirm,
} = vi.hoisted(() => ({
  fetchOfficialAccounts: vi.fn(),
  bindOfficialAccount: vi.fn(),
  searchOfficialAccountCandidates: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/modules/users/api/officialAccount.service', () => ({
  fetchOfficialAccounts,
  bindOfficialAccount,
  searchOfficialAccountCandidates,
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

function buildItem(overrides: Partial<OfficialAccountItem> = {}): OfficialAccountItem {
  return {
    business_key: 'COLLECTION_ASSISTANT',
    display_name: '未认领收录作品临时归属',
    description: '搬运收录与撤销认领时，未认领作品临时归属该账号。',
    bound: false,
    dangling: false,
    user_uuid: null,
    nickname: null,
    username: null,
    avatar: null,
    remark: null,
    updated_by_nickname: null,
    updated_at: '',
    ...overrides,
  }
}

const boundItem = buildItem({
  bound: true,
  user_uuid: 'official-uuid-1',
  nickname: '蔚蓝收录认领助手',
  username: 'workslibrary',
  updated_at: '2026-09-19 23:00:00',
  updated_by_nickname: '馆长',
})

async function mountView(items: OfficialAccountItem[]): Promise<VueWrapper> {
  fetchOfficialAccounts.mockResolvedValue(items)
  const wrapper = mount(OfficialAccountsView)
  await flushPromises()
  return wrapper
}

function buttonWithText(wrapper: VueWrapper, text: string) {
  const button = wrapper.findAll('button').find((item) => item.text().includes(text))
  if (!button) {
    throw new Error(`未找到按钮：${text}`)
  }
  return button
}

/** 直接给 el-select 写入选中值，等价于用户在下拉里选中某个账号 */
async function pickAccount(wrapper: VueWrapper, uuid: string): Promise<void> {
  const select = wrapper.findComponent({ name: 'ElSelect' })
  select.vm.$emit('update:modelValue', uuid)
  await flushPromises()
}

async function submitDialog(wrapper: VueWrapper): Promise<void> {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('OfficialAccountsView', () => {
  beforeEach(() => {
    confirm.mockReset()
    confirm.mockResolvedValue('confirm')
  })

  it('未配置业务必须展示阻断提示，而不是被隐藏', async () => {
    const wrapper = await mountView([buildItem()])

    expect(wrapper.text()).toContain('未认领收录作品临时归属')
    expect(wrapper.text()).toContain('未配置 —— 该业务写入已被阻断')
    expect(wrapper.text()).toContain('未配置业务：1 项')
    expect(wrapper.text()).toContain('绑定')
  })

  it('已绑定业务展示账号昵称、用户名与操作人', async () => {
    const wrapper = await mountView([boundItem])

    expect(wrapper.text()).toContain('蔚蓝收录认领助手')
    expect(wrapper.text()).toContain('@workslibrary')
    expect(wrapper.text()).toContain('2026-09-19 23:00:00')
    expect(wrapper.text()).toContain('馆长')
    expect(wrapper.text()).toContain('更换')
    expect(wrapper.text()).toContain('未配置业务：0 项')
  })

  it('说明列直接渲染后端下发的短文案，且不再渲染 Title 下的冗余解释卡片', async () => {
    const wrapper = await mountView([boundItem])

    // 业务说明只在表格「说明」列呈现
    expect(wrapper.text()).toContain(boundItem.description)
    // 回归保护：Title 条下方的「解释卡片」已删除，避免重复信息挤占表格
    expect(wrapper.find('.intro').exists()).toBe(false)
    expect(wrapper.findAll('.page-card')).toHaveLength(1)
  })

  it('绑定账号已被删除时必须提示重新绑定并展示旧 UUID', async () => {
    const wrapper = await mountView([
      buildItem({ dangling: true, user_uuid: 'deleted-uuid' }),
    ])

    expect(wrapper.text()).toContain('绑定账号已不存在')
    expect(wrapper.text()).toContain('deleted-uuid')
    expect(wrapper.text()).toContain('未配置业务：1 项')
  })

  it('首次绑定：提交 business_key 与所选账号，且不弹更换确认', async () => {
    bindOfficialAccount.mockResolvedValue({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-2',
      replaced_uuid: null,
      migrated_works: 0,
    })
    const wrapper = await mountView([buildItem()])

    await buttonWithText(wrapper, '绑定').trigger('click')
    await flushPromises()
    await pickAccount(wrapper, 'official-uuid-2')
    await submitDialog(wrapper)

    expect(confirm).not.toHaveBeenCalled()
    expect(bindOfficialAccount).toHaveBeenCalledWith({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-2',
      remark: null,
    })
    expect(showSuccess).toHaveBeenCalledWith('官方账号已更新')
    expect(fetchOfficialAccounts).toHaveBeenCalledTimes(2)
  })

  it('更换绑定：必须先二次确认，并回报被重新归属的作品数', async () => {
    bindOfficialAccount.mockResolvedValue({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-2',
      replaced_uuid: 'official-uuid-1',
      migrated_works: 3,
    })
    const wrapper = await mountView([boundItem])

    await buttonWithText(wrapper, '更换').trigger('click')
    await flushPromises()
    await pickAccount(wrapper, 'official-uuid-2')
    await submitDialog(wrapper)

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(String(confirm.mock.calls[0]?.[0])).toContain('未认领作品的归属账号会一并改到新账号')
    expect(bindOfficialAccount).toHaveBeenCalledWith({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-2',
      remark: null,
    })
    expect(showSuccess).toHaveBeenCalledWith('官方账号已更新，并重新归属了 3 篇未认领作品')
  })

  it('更换绑定：取消确认时不得调用接口', async () => {
    confirm.mockRejectedValue('cancel')
    const wrapper = await mountView([boundItem])

    await buttonWithText(wrapper, '更换').trigger('click')
    await flushPromises()
    await pickAccount(wrapper, 'official-uuid-2')
    await submitDialog(wrapper)

    expect(confirm).toHaveBeenCalledTimes(1)
    expect(bindOfficialAccount).not.toHaveBeenCalled()
  })

  it('更换绑定：目标账号与当前一致时跳过确认（幂等提交）', async () => {
    bindOfficialAccount.mockResolvedValue({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-1',
      replaced_uuid: null,
      migrated_works: 0,
    })
    const wrapper = await mountView([boundItem])

    await buttonWithText(wrapper, '更换').trigger('click')
    await flushPromises()
    await pickAccount(wrapper, 'official-uuid-1')
    await submitDialog(wrapper)

    expect(confirm).not.toHaveBeenCalled()
    expect(bindOfficialAccount).toHaveBeenCalledTimes(1)
  })

  it('加载失败必须提示错误', async () => {
    fetchOfficialAccounts.mockRejectedValue(new Error('网络异常'))
    mount(OfficialAccountsView)
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('网络异常')
  })

  it('绑定失败必须提示错误且不刷新列表', async () => {
    bindOfficialAccount.mockRejectedValue(new Error('该账号已被其它官方业务绑定，请更换其它账号'))
    const wrapper = await mountView([buildItem()])

    await buttonWithText(wrapper, '绑定').trigger('click')
    await flushPromises()
    await pickAccount(wrapper, 'official-uuid-2')
    await submitDialog(wrapper)

    expect(showError).toHaveBeenCalledWith('该账号已被其它官方业务绑定，请更换其它账号')
    // 仅首次加载
    expect(fetchOfficialAccounts).toHaveBeenCalledTimes(1)
  })

  it('远程搜索必须把关键词透传给接口，且候选账号可直接选中提交', async () => {
    searchOfficialAccountCandidates.mockResolvedValue([
      { uuid: 'official-uuid-3', nickname: '候选账号', username: 'candidate', avatar: null },
    ])
    bindOfficialAccount.mockResolvedValue({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-3',
      replaced_uuid: null,
      migrated_works: 0,
    })
    const wrapper = await mountView([buildItem()])

    await buttonWithText(wrapper, '绑定').trigger('click')
    await flushPromises()

    const select = wrapper.findComponent({ name: 'ElSelect' })
    await select.props('remoteMethod')('candidate')
    await flushPromises()

    expect(searchOfficialAccountCandidates).toHaveBeenCalledWith('candidate')

    // 候选项要能真正被选中并提交（下拉内容被 teleport，故按行为而非 DOM 文案断言）
    await pickAccount(wrapper, 'official-uuid-3')
    await submitDialog(wrapper)

    expect(bindOfficialAccount).toHaveBeenCalledWith({
      business_key: 'COLLECTION_ASSISTANT',
      user_uuid: 'official-uuid-3',
      remark: null,
    })
  })
})
