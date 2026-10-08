import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { ElMessage, ElMessageBox } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'

import FestivalsListView from '@/modules/system/views/FestivalsListView.vue'
import {
  dayOptionsFor,
  detectDateMode,
  monthRangeOf,
} from '@/modules/system/api/festivals.service'
import type { FestivalRecord } from '@/modules/system/api/festivals.service'

const {
  fetchFestivals,
  createFestival,
  updateFestival,
  setFestivalEnabled,
  deleteFestival,
  showError,
  showSuccess,
} = vi.hoisted(() => ({
  fetchFestivals: vi.fn(),
  createFestival: vi.fn(),
  updateFestival: vi.fn(),
  setFestivalEnabled: vi.fn(),
  deleteFestival: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

// 只替换网络层，保留 detectDateMode / monthRangeOf / dayOptionsFor 等纯函数真实实现，
// 以便测试真正覆盖「月份式 -> 月首月末」的区间展开逻辑。
vi.mock('@/modules/system/api/festivals.service', async () => {
  const actual = await vi.importActual<typeof import('@/modules/system/api/festivals.service')>(
    '@/modules/system/api/festivals.service',
  )
  return {
    ...actual,
    fetchFestivals,
    createFestival,
    updateFestival,
    setFestivalEnabled,
    deleteFestival,
  }
})

vi.mock('@/shared/utils/message', () => ({
  showError,
  showSuccess,
  showInfo: vi.fn(),
  showWarning: vi.fn(),
}))

/**
 * 注意两点：
 * 1、不要用工厂整体 mock element-plus —— 那会破坏 el-dialog 等组件的内部依赖，
 *    导致弹窗内容无法渲染；这里改为对 ElMessage / ElMessageBox 的方法做 spy；
 * 2、全局 setup 的 ElDialog stub 不渲染 footer 插槽，因此本文件自定义一个会渲染 footer 的 stub，
 *    否则「保存 / 取消」按钮不存在，无法测试提交链路。
 */
const DialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: '',
    },
  },
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('section', { 'data-testid': 'dialog-stub' }, [
            h('header', props.title),
            slots.default?.(),
            h('footer', slots.footer?.()),
          ])
        : null
  },
})

const mountOptions = {
  global: {
    stubs: {
      'el-dialog': DialogStub,
    },
  },
}

/**
 * 测试中会在两个范围内查找控件：外层 wrapper 与弹窗 stub 容器。
 * 这里统一用「带 query 能力的 wrapper」类型，避免 DOMWrapper / VueWrapper 的泛型不兼容。
 */
type Queryable = {
  exists(): boolean
  findAll(selector: string): Array<{ text(): string; trigger(event: string): Promise<void> }>
  find(selector: string): {
    exists(): boolean
    text(): string
    setValue(value: string): Promise<void>
  }
  findComponent(selector: string): VueWrapper
}

/** 各月最后一天（2 月按闰年上限 29，与页面 MONTH_MAX_DAY 的策略保持一致） */
const FEB_LAST_DAY = 29
const APRIL_LAST_DAY = 30

/**
 * 从外层 wrapper 出发查找组件实例。
 *
 * 为什么需要它：全局 setup 会把 el-form-item / el-dialog 等替换成简易 stub，
 * 导致在「弹窗容器」这个子 wrapper 上调用 findComponent 匹配不到 el-input / el-radio-group；
 * 而外层 wrapper 的 findAllComponents 是深度遍历的，可以稳定拿到这些组件实例。
 */
function componentAt(root: ReturnType<typeof mount>, selector: string): VueWrapper {
  return root.findComponent(selector) as unknown as VueWrapper
}

/** 写入输入框：优先走组件实例，取不到时退化为 DOM setValue */
async function setInputValue(
  wrapper: ReturnType<typeof mount>,
  selector: string,
  value: string,
): Promise<void> {
  const component = wrapper.findComponent(selector)
  if (component.exists()) {
    await (component as unknown as VueWrapper).setValue(value)
    return
  }
  await wrapper.find(selector).setValue(value)
}

// Element Plus 的消息 / 确认框方法带有较复杂的重载签名，这里的 spy 只用于断言调用，
// 因此显式放宽类型，避免与其重载签名冲突。
/* eslint-disable @typescript-eslint/no-explicit-any */
let confirmSpy: any
let errorSpy: any
/* eslint-enable @typescript-eslint/no-explicit-any */

/** 构造节日记录，统一索引与后端一致（月份 * 32 + 日） */
function createFestivalRecord(
  id: string,
  name: string,
  sm: number,
  sd: number,
  em: number,
  ed: number,
  enabled = true,
  bound = 0,
): FestivalRecord {
  return {
    id,
    name,
    start_month: sm,
    start_day: sd,
    end_month: em,
    end_day: ed,
    start_index: sm * 32 + sd,
    end_index: em * 32 + ed,
    enabled,
    created_by: null,
    created_at: null,
    updated_at: null,
    bound_backgrounds: bound,
  }
}

/** 在给定范围内按文案取按钮，避免命中表格行内的同名按钮 */
function findButton(root: Queryable, label: string, index = 0) {
  const matches = root.findAll('button').filter((candidate) => candidate.text() === label)
  const button = matches[index]
  if (!button) {
    throw new Error(`Unable to find button: ${label}#${index}`)
  }
  return button
}

function findSelect(root: Queryable, testId: string): VueWrapper {
  return root.findComponent(`[data-test="${testId}"]`) as unknown as VueWrapper
}

/**
 * 读取某个下拉框当前绑定的值（即 el-select 的 modelValue）。
 *
 * 为什么不用 wrapper.props('modelValue')：findSelect 返回的是无泛型的 VueWrapper，
 * 其 props 的键类型被推断为 never，无法直接用字符串索引；
 * 这里通过一次显式断言换取可读的取值方式。
 */
function selectModelValue(root: Queryable, testId: string): unknown {
  const select = findSelect(root, testId)
  return (select.vm as unknown as { modelValue?: unknown }).modelValue
}

/** 取当前弹窗容器（DialogStub 渲染出的 section） */
function dialog(wrapper: ReturnType<typeof mount>): Queryable {
  return wrapper.find('[data-testid="dialog-stub"]') as unknown as Queryable
}

describe('festival date helpers', () => {
  it('detects month mode when the range covers a whole month', () => {
    // 2 月末日按闰年上限 29 判定，与后端放宽策略一致
    expect(detectDateMode({ start_day: 1, end_day: FEB_LAST_DAY, end_month: 2 })).toBe('month')
    expect(detectDateMode({ start_day: 1, end_day: APRIL_LAST_DAY, end_month: 4 })).toBe('month')
    expect(detectDateMode({ start_day: 1, end_day: 31, end_month: 1 })).toBe('month')
  })

  it('detects date mode when the range is not a whole month', () => {
    expect(detectDateMode({ start_day: 2, end_day: 28, end_month: 2 })).toBe('date')
    expect(detectDateMode({ start_day: 1, end_day: 27, end_month: 2 })).toBe('date')
  })

  it('expands a month range into the first and last day of the month', () => {
    expect(monthRangeOf(12, 2)).toEqual({
      start_month: 12,
      start_day: 1,
      end_month: 2,
      end_day: FEB_LAST_DAY,
    })
  })

  it('limits day options by the max day of the month', () => {
    expect(dayOptionsFor(2)).toHaveLength(FEB_LAST_DAY)
    // 4 月只有 30 天，不应出现 31 日选项
    expect(dayOptionsFor(4).some((opt) => opt.value === 31)).toBe(false)
    // 未知月份退化为 31 天，避免下拉为空
    expect(dayOptionsFor(99)).toHaveLength(31)
  })
})

describe('FestivalsListView', () => {
  beforeEach(() => {
    confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    errorSpy = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as never)
    fetchFestivals.mockResolvedValue([
      createFestivalRecord('fest-ny', '新年', 12, 1, 2, FEB_LAST_DAY, true, 3),
      createFestivalRecord('fest-summer', '暑期', 7, 5, 8, 20, false, 0),
    ])
    createFestival.mockResolvedValue(createFestivalRecord('fest-new', '新节日', 3, 1, 3, 31))
    updateFestival.mockResolvedValue(
      createFestivalRecord('fest-ny', '新年', 12, 1, 2, FEB_LAST_DAY),
    )
    setFestivalEnabled.mockResolvedValue(
      createFestivalRecord('fest-ny', '新年', 12, 1, 2, FEB_LAST_DAY, false),
    )
    deleteFestival.mockResolvedValue({ id: 'fest-ny', unboundBackgrounds: 3 })
  })

  afterEach(() => {
    confirmSpy.mockRestore()
    errorSpy.mockRestore()
  })

  it('loads festivals and renders month/date modes with the bound background count', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)

    await flushPromises()

    expect(fetchFestivals).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('新年')
    expect(wrapper.text()).toContain('暑期')
    // 12-01 -> 02-29 覆盖整月 -> 月份式，展示到「月」粒度并标注次年
    expect(wrapper.text()).toContain('12月 ~ 次年02月')
    // 07-05 -> 08-20 非整月 -> 日期式，展示到「日」粒度
    expect(wrapper.text()).toContain('07月05日 ~ 08月20日')
    expect(wrapper.text()).toContain('3 张')
    expect(showError).not.toHaveBeenCalled()
  })

  it('shows an error when the list request fails', async () => {
    fetchFestivals.mockRejectedValue(new Error('boom'))
    const wrapper = mount(FestivalsListView, mountOptions)

    await flushPromises()

    expect(showError).toHaveBeenCalled()
  })

  it('creates a month-mode festival, expanding it to the first/last day of the months', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '新增节日').trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    expect(box.exists()).toBe(true)
    await setInputValue(wrapper, '[data-test="form-name"]', '寒假')

    await findSelect(box, 'form-start-month').vm.$emit('update:modelValue', 1)
    await findSelect(box, 'form-end-month').vm.$emit('update:modelValue', 2)
    await flushPromises()

    // 月份式预览：起始月首日 -> 结束月末日
    expect(box.find('[data-test="form-preview"]').text()).toContain('01月01日 ~ 02月29日')

    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(createFestival).toHaveBeenCalledWith({
      name: '寒假',
      start_month: 1,
      start_day: 1,
      end_month: 2,
      end_day: FEB_LAST_DAY,
    })
    expect(showSuccess).toHaveBeenCalledWith('节日已新增')
    expect(fetchFestivals).toHaveBeenCalledTimes(2)
  })

  it('creates a date-mode festival with the exact month and day picked', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '新增节日').trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    await setInputValue(wrapper, '[data-test="form-name"]', '校庆')
    // 切到日期式
    await componentAt(wrapper, '[data-test="form-mode"]').vm.$emit('update:modelValue', 'date')
    await flushPromises()

    await findSelect(box, 'form-start-month').vm.$emit('update:modelValue', 5)
    await findSelect(box, 'form-start-day').vm.$emit('update:modelValue', 10)
    await findSelect(box, 'form-end-month').vm.$emit('update:modelValue', 5)
    await findSelect(box, 'form-end-day').vm.$emit('update:modelValue', 20)
    await flushPromises()

    expect(box.find('[data-test="form-preview"]').text()).toContain('05月10日 ~ 05月20日')

    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(createFestival).toHaveBeenCalledWith({
      name: '校庆',
      start_month: 5,
      start_day: 10,
      end_month: 5,
      end_day: 20,
    })
  })

  it('rejects an empty name before calling the API', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '新增节日').trigger('click')
    await flushPromises()
    await findButton(dialog(wrapper), '保存').trigger('click')
    await flushPromises()

    expect(errorSpy).toHaveBeenCalledWith('请填写节日名称')
    expect(createFestival).not.toHaveBeenCalled()
  })

  it('shows the backend overlap message when the range conflicts with another festival', async () => {
    createFestival.mockRejectedValue(new Error('节日日期与「新年」重叠重合，请调整日期区间'))
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '新增节日').trigger('click')
    await flushPromises()
    const box = dialog(wrapper)
    await setInputValue(wrapper, '[data-test="form-name"]', '元旦')
    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(showError).toHaveBeenCalledWith('节日日期与「新年」重叠重合，请调整日期区间')
  })

  it('prefills the dialog when editing and submits the updated range', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '修改').trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    // 月份式节日回显：传入 12 月 -> 2 月，预览展开为月首 -> 月末并标注次年
    expect(wrapper.find('[data-test="form-name"]').exists()).toBe(true)
    expect(box.find('[data-test="form-preview"]').text()).toContain('12月01日 ~ 次年02月29日')

    await findButton(box, '保存').trigger('click')
    await flushPromises()

    expect(updateFestival).toHaveBeenCalledWith('fest-ny', {
      name: '新年',
      start_month: 12,
      start_day: 1,
      end_month: 2,
      end_day: FEB_LAST_DAY,
    })
    expect(showSuccess).toHaveBeenCalledWith('节日已更新')
  })

  /**
   * 回归测试：管理端「修改节日」把月份式切成日期式时，起止月日必须原样回显。
   *
   * 背景：日期式下起始 / 结束日期的下拉框曾整片看不到文字（只剩箭头）。
   * 数据层本身是正常的，问题出在布局 —— el-form-item 内容区是 flex 容器，
   * 日期行未撑满时会塌陷到内容固有宽度，把 el-select 的绝对定位选中文本压成 0 宽。
   * 因此这里同时锁定「值绑定」与「结构契约」两部分。
   */
  it('keeps the start/end values when a month-mode festival is switched to date mode', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    // 修改「新年」（12-01 → 次年 02-29，整月区间 -> 默认按月份式回显）
    await findButton(wrapper, '修改').trigger('click')
    await flushPromises()

    const box = dialog(wrapper)
    expect(selectModelValue(box, 'form-start-month')).toBe(12)

    // 切到日期式：起止月日原样保留，四个下拉框都要有值
    await componentAt(wrapper, '[data-test="form-mode"]').vm.$emit('update:modelValue', 'date')
    await flushPromises()

    expect(selectModelValue(box, 'form-start-month')).toBe(12)
    expect(selectModelValue(box, 'form-start-day')).toBe(1)
    expect(selectModelValue(box, 'form-end-month')).toBe(2)
    expect(selectModelValue(box, 'form-end-day')).toBe(FEB_LAST_DAY)
    expect(box.find('[data-test="form-preview"]').text()).toContain('12月01日 ~ 次年02月29日')

    // 结构契约：日期式必须由 .date-row 承载「月份 + 日」两个下拉。
    // 注意边界：jsdom 没有布局能力（vitest 也关闭了 CSS 处理），
    // 因此本用例只能守住「取值绑定 + DOM 结构」，样式可见性自身仍需人工 / 浏览器验证；
    // 撑满规则见 FestivalsListView.vue 的 <style scoped>，重构时勿删。
    expect(box.findAll('.date-row')).toHaveLength(2)
    expect(box.findAll('.date-row .el-select')).toHaveLength(4)
  })

  it('closes a festival while keeping the background bindings', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    const firstSwitch = wrapper.findAllComponents({ name: 'ElSwitch' })[0]
    await firstSwitch!.vm.$emit('update:modelValue', false)
    await firstSwitch!.vm.$emit('change', false)
    await flushPromises()

    expect(setFestivalEnabled).toHaveBeenCalledWith('fest-ny', false)
    expect(showSuccess).toHaveBeenCalledWith('节日已关闭（保留背景图绑定）')
    // 关闭不应触发解绑（后端只在删除时解绑）
    expect(deleteFestival).not.toHaveBeenCalled()
  })

  it('warns about bound backgrounds before deleting and reports the unbound count', async () => {
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(confirmSpy).toHaveBeenCalledWith(
      '删除后，绑定该节日的 3 张背景图将自动划归到「无节日」通用图，是否继续？',
      '删除节日「新年」',
      expect.any(Object),
    )
    expect(deleteFestival).toHaveBeenCalledWith('fest-ny')
    expect(showSuccess).toHaveBeenCalledWith('节日已删除，3 张背景图已划归「无节日」')
    expect(fetchFestivals).toHaveBeenCalledTimes(2)
  })

  it('does not delete when the confirmation is cancelled', async () => {
    confirmSpy.mockRejectedValue('cancel')
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(deleteFestival).not.toHaveBeenCalled()
    expect(showError).not.toHaveBeenCalled()
  })

  it('reports an error when the delete request fails', async () => {
    deleteFestival.mockRejectedValue(new Error('删除失败'))
    const wrapper = mount(FestivalsListView, mountOptions)
    await flushPromises()

    await findButton(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(showError).toHaveBeenCalled()
  })
})