import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchCommentCountByWork } from '@/utils/commentApi.ts'
import {
  getCachedWorkCommentCount,
  loadWorkCommentCount,
  loadWorkCommentCounts,
  resetWorkCommentCountCacheForTests,
} from '@/utils/workCommentCountCache.ts'

vi.mock('@/utils/commentApi.ts', () => ({
  fetchCommentCountByWork: vi.fn(),
}))

describe('workCommentCountCache', () => {
  beforeEach(() => {
    resetWorkCommentCountCacheForTests()
    vi.mocked(fetchCommentCountByWork).mockReset()
  })

  it('loads a single work comment count and caches the resolved count', async () => {
    vi.mocked(fetchCommentCountByWork).mockResolvedValueOnce(7)

    await expect(loadWorkCommentCount('work-1')).resolves.toBe(7)
    expect(getCachedWorkCommentCount('work-1')).toBe(7)
    await expect(loadWorkCommentCount('work-1')).resolves.toBe(7)

    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(1)
    expect(fetchCommentCountByWork).toHaveBeenCalledWith('work-1')
  })

  it('dedupes concurrent loads for the same work id', async () => {
    let resolveCount: (count: number) => void = () => {}
    vi.mocked(fetchCommentCountByWork).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveCount = resolve
      }),
    )

    const firstLoad = loadWorkCommentCount('work-1')
    const secondLoad = loadWorkCommentCount('work-1')
    resolveCount(3)

    await expect(Promise.all([firstLoad, secondLoad])).resolves.toEqual([3, 3])
    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(1)
  })

  it('batch loads unique work ids and returns counts keyed by work id', async () => {
    vi.mocked(fetchCommentCountByWork)
      .mockResolvedValueOnce(2)
      .mockResolvedValueOnce(5)

    await expect(loadWorkCommentCounts(['work-1', 'work-2', 'work-1'])).resolves.toEqual({
      'work-1': 2,
      'work-2': 5,
    })

    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(2)
    expect(fetchCommentCountByWork).toHaveBeenNthCalledWith(1, 'work-1')
    expect(fetchCommentCountByWork).toHaveBeenNthCalledWith(2, 'work-2')
  })

  it('uses cached values during batch loads', async () => {
    vi.mocked(fetchCommentCountByWork)
      .mockResolvedValueOnce(4)
      .mockResolvedValueOnce(9)

    await expect(loadWorkCommentCount('work-1')).resolves.toBe(4)
    await expect(loadWorkCommentCounts(['work-1', 'work-2'])).resolves.toEqual({
      'work-1': 4,
      'work-2': 9,
    })

    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(2)
    expect(fetchCommentCountByWork).toHaveBeenNthCalledWith(2, 'work-2')
  })

  it('falls back to 0 and caches 0 when loading fails', async () => {
    vi.mocked(fetchCommentCountByWork).mockRejectedValueOnce(new Error('network failed'))

    await expect(loadWorkCommentCount('work-1')).resolves.toBe(0)
    expect(getCachedWorkCommentCount('work-1')).toBe(0)
    await expect(loadWorkCommentCount('work-1')).resolves.toBe(0)

    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(1)
  })

  it('resets cached counts and pending loads for tests', async () => {
    vi.mocked(fetchCommentCountByWork)
      .mockResolvedValueOnce(1)
      .mockResolvedValueOnce(6)

    await expect(loadWorkCommentCount('work-1')).resolves.toBe(1)
    resetWorkCommentCountCacheForTests()

    expect(getCachedWorkCommentCount('work-1')).toBeUndefined()
    await expect(loadWorkCommentCount('work-1')).resolves.toBe(6)
    expect(fetchCommentCountByWork).toHaveBeenCalledTimes(2)
  })
})
