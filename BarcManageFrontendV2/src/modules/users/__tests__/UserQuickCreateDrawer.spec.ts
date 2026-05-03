import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import UserQuickCreateDrawer from '@/modules/users/components/UserQuickCreateDrawer.vue'

const { uploadNewUser, showError, showSuccess, messageBoxConfirm } = vi.hoisted(() => ({
  uploadNewUser: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
  messageBoxConfirm: vi.fn(),
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

vi.mock('@/modules/users/api/users.service', () => ({
  uploadNewUser,
}))

vi.mock('@/shared/utils/message', () => ({
  showError,
  showInfo: vi.fn(),
  showSuccess,
  showWarning: vi.fn(),
}))

function loadingStates(wrapper: ReturnType<typeof mount>): boolean[] {
  return (wrapper.emitted('update:loading') ?? []).map(([value]) => value as boolean)
}

function findInputByIndex(wrapper: ReturnType<typeof mount>, index: number) {
  const input = wrapper.findAllComponents({ name: 'ElInput' })[index]

  if (!input) {
    throw new Error(`Unable to find input at index ${index}`)
  }

  return input
}

function findButtonByText(wrapper: ReturnType<typeof mount>, label: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === label)

  if (!button) {
    throw new Error(`Unable to find button: ${label}`)
  }

  return button
}

function findComponentByName(wrapper: ReturnType<typeof mount>, name: string, index = 0) {
  const component = wrapper.findAllComponents({ name })[index]

  if (!component) {
    throw new Error(`Unable to find component ${name} at index ${index}`)
  }

  return component
}

function createDeferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void

  const promise = new Promise<T>((nextResolve) => {
    resolve = nextResolve
  })

  return { promise, resolve }
}

describe('UserQuickCreateDrawer', () => {
  beforeEach(() => {
    uploadNewUser.mockResolvedValue('创建成功')
    messageBoxConfirm.mockResolvedValue('confirm')
  })

  it('uses form validation to block invalid email submissions before calling the service', async () => {
    const wrapper = mount(UserQuickCreateDrawer, {
      props: {
        modelValue: true,
      },
    })

    await findInputByIndex(wrapper, 0).find('input').setValue('new-user')
    await findInputByIndex(wrapper, 1).find('input').setValue('secret-pass')
    await findInputByIndex(wrapper, 2).find('input').setValue('not-an-email')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(uploadNewUser).not.toHaveBeenCalled()
    expect(showError).toHaveBeenCalledWith('请输入有效的邮箱地址。')
  })

  it('emits submitted flow events after a successful quick create', async () => {
    const wrapper = mount(UserQuickCreateDrawer, {
      props: {
        modelValue: true,
      },
    })

    await findInputByIndex(wrapper, 0).find('input').setValue('  new-user  ')
    await findInputByIndex(wrapper, 1).find('input').setValue('secret-pass')
    await findInputByIndex(wrapper, 2).find('input').setValue('  new-user@example.com  ')
    await findInputByIndex(wrapper, 3).find('input').setValue('  123456789  ')
    findComponentByName(wrapper, 'ElInputNumber').vm.$emit('update:modelValue', 3)
    findComponentByName(wrapper, 'ElSwitch').vm.$emit('update:modelValue', false)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(uploadNewUser).toHaveBeenCalledWith({
      username: 'new-user',
      password: 'secret-pass',
      email: 'new-user@example.com',
      telephone: '123456789',
      safe_level: 3,
      email_verified: false,
    })
    expect(showSuccess).toHaveBeenCalledWith('创建成功')
    expect(wrapper.emitted('submitted')).toEqual([[
      {
        username: 'new-user',
        email_verified: false,
        safe_level: 3,
        telephone_present: true,
      },
    ]])
    expect(wrapper.emitted('submitted')?.[0]?.[0]).not.toHaveProperty('password')
    expect(wrapper.emitted('update:modelValue')).toContainEqual([false])
    expect(loadingStates(wrapper)).toEqual([true, false])
  })

  it('prevents duplicate submits while the create request is still pending', async () => {
    const deferred = createDeferred<string>()
    uploadNewUser.mockReturnValueOnce(deferred.promise)

    const wrapper = mount(UserQuickCreateDrawer, {
      props: {
        modelValue: true,
      },
    })

    await findInputByIndex(wrapper, 0).find('input').setValue('new-user')
    await findInputByIndex(wrapper, 1).find('input').setValue('secret-pass')
    await findInputByIndex(wrapper, 2).find('input').setValue('new-user@example.com')

    await wrapper.find('form').trigger('submit')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(uploadNewUser).toHaveBeenCalledTimes(1)
    expect(loadingStates(wrapper)).toEqual([true])

    deferred.resolve('创建成功')
    await flushPromises()

    expect(loadingStates(wrapper)).toEqual([true, false])
  })

  it('asks for confirmation before discarding dirty drawer content', async () => {
    messageBoxConfirm.mockRejectedValueOnce('cancel')

    const wrapper = mount(UserQuickCreateDrawer, {
      props: {
        modelValue: true,
      },
    })

    await findInputByIndex(wrapper, 0).find('input').setValue('half-filled-user')
    await findButtonByText(wrapper, '取消').trigger('click')
    await flushPromises()

    expect(messageBoxConfirm).toHaveBeenCalledWith(
      expect.stringContaining('未保存的内容'),
      '放弃新增用户',
      expect.any(Object),
    )
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
