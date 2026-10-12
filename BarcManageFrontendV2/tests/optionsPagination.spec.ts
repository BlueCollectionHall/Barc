import { describe, expect, it, vi } from 'vitest'
import { loadAllOptions } from '@/modules/school-club-student/api/options'

/** 验证超过一页的选项不会因后端容量限制而消失。 */
describe('基础资料选项分页', () => {
  it('继续读取后续页并保留全部选项', async () => {
    const first = Array.from({ length: 100 }, (_, index) => ({ id: String(index) }))
    const load = vi.fn().mockResolvedValueOnce({ list: first, total: 101 }).mockResolvedValueOnce({ list: [{ id: 'last' }], total: 101 })
    const result = await loadAllOptions(load)
    expect(result.list).toHaveLength(101)
    expect(load).toHaveBeenNthCalledWith(2, 2, 100)
  })
  it('重复页必须报错，不能无限加载或悄悄丢失选项', async () => {
    await expect(loadAllOptions(vi.fn().mockResolvedValue({ list: [{ id: 'same' }], total: 2 }))).rejects.toThrow('重新加载')
  })
  it('空字典正常返回空选项', async () => {
    expect(await loadAllOptions(vi.fn().mockResolvedValue({ list: [], total: 0 }))).toEqual({ list: [], total: 0 })
  })
})
