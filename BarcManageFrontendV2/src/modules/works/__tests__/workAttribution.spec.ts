import { describe, expect, it } from 'vitest'

import { getWorkCreatorDisplay, getWorkCreatorSourceLabel } from '../utils/workAttribution'

describe('work attribution display', () => {
  it('uses the platform author nickname for a claimed work', () => {
    const work = {
      is_claim: true,
      author: 'author-uuid',
      author_display: '平台作者',
      author_nickname: null,
    }

    expect(getWorkCreatorDisplay(work)).toBe('平台作者')
    expect(getWorkCreatorSourceLabel(work)).toBe('平台作者')
  })

  it('uses the external original author for a collected work without changing platform ownership', () => {
    const work = {
      is_claim: false,
      author: 'collection-assistant-uuid',
      author_display: '蔚蓝收录助手',
      author_nickname: 'guochouchou',
    }

    expect(getWorkCreatorDisplay(work)).toBe('guochouchou')
    expect(getWorkCreatorSourceLabel(work)).toBe('站外原作者')
  })
})
