// @vitest-environment jsdom
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'

const get = vi.hoisted(() => vi.fn())
vi.mock('../src/utils/https', () => ({ baseHttp: { get } }))
enableAutoUnmount(afterEach)
beforeEach(() => { vi.resetModules(); get.mockReset(); vi.useFakeTimers(); vi.setSystemTime(new Date(2026, 9, 12, 12)); })
afterEach(() => vi.useRealTimers())
const ok = (data: unknown) => ({ data: { code: 0, data } })
it('背景和节日读取缓存，未知模块为空，首页使用带场景的签名 URL', async () => {
  get.mockImplementation(async (url: string) => ok(url.includes('background') ? { home: [{ url: 'https://example.test/signed', time_period: 'day', festival: null }] } : []))
  const { default: HomeBg } = await import('../src/components/Home/HomeBgComp.vue'); const bg = mount(HomeBg); await flushPromises(); expect(bg.attributes('style')).toContain('https://example.test/signed')
  const api = await import('../src/utils/backgroundApi'); await expect(api.fetchModuleBackgrounds('absent')).resolves.toEqual([]); await api.fetchFestivals(); expect(get).toHaveBeenCalledTimes(2)
})
it.each([[12, 'bg_day_purana'], [18, 'bg_evening_seia'], [22, 'bg_night_hina']])('没有启用图片时首页按 %s 点选择默认图', async (hour, image) => {
  vi.setSystemTime(new Date(2026, 9, 12, hour)); get.mockResolvedValue(ok([])); const { default: HomeBg } = await import('../src/components/Home/HomeBgComp.vue')
  const wrapper = mount(HomeBg); await flushPromises(); expect(wrapper.attributes('style')).toContain(image)
})
it('动态节日没有配置图片时使用节日默认图，网络或非法响应不会破坏背景', async () => {
  get.mockImplementation(async (url: string) => ok(url.includes('festival') ? [{ id: 'festival', start_index: 321, end_index: 351 }] : {}))
  const { default: HomeBg } = await import('../src/components/Home/HomeBgComp.vue'); const wrapper = mount(HomeBg); await flushPromises(); expect(wrapper.attributes('style')).toContain('newyear_')
  vi.resetModules(); const api = await import('../src/utils/backgroundApi'); get.mockRejectedValue(new Error('离线')); await expect(api.fetchBackgrounds()).resolves.toEqual({}); await expect(api.fetchFestivals()).resolves.toEqual([])
  get.mockResolvedValue({ data: { code: 1, data: null } }); await expect(api.fetchBackgrounds()).resolves.toEqual({}); await expect(api.fetchFestivals()).resolves.toEqual([])
})
it('其他模块按模块列表显示后台配置图，空列表保留原有兜底', async () => {
  const item = [{ url: 'https://example.test/module-bg', time_period: null, festival: null }]
  get.mockResolvedValue(ok({ sign: item, join: item, feedback: item, messageboard: item }))
  const components = await Promise.all([import('../src/components/Sign/SignBgComp.vue'), import('../src/components/Join/JoinBgComp.vue'), import('../src/components/Feedback/FeedbackBgComp.vue'), import('../src/components/MessageBoard/MbBgComp.vue')])
  for (const { default: component } of components) { const wrapper = mount(component); await flushPromises(); expect(wrapper.html()).toContain('https://example.test/module-bg') }
  vi.resetModules(); get.mockResolvedValue(ok({})); const { default: JoinBg } = await import('../src/components/Join/JoinBgComp.vue'); const empty = mount(JoinBg); await flushPromises(); expect(empty.get('img').attributes('src')).toContain('BG_View_Kivotos2')
})
