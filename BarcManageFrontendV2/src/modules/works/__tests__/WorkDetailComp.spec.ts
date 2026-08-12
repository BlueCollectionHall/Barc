import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import WorkDetailComp from '../components/WorkDetailComp.vue'
import { getWorkEditDetail, type WorkEditDetail } from '../api/workManage'

const apiMocks = vi.hoisted(() => ({
  getWorkEditDetail: vi.fn(),
}))

vi.mock('../api/workManage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/workManage')>()

  return {
    ...actual,
    getWorkEditDetail: apiMocks.getWorkEditDetail,
  }
})

const ElementStub = defineComponent({
  name: 'ElementStub',
  setup(_props, { slots }) {
    return () => h('span', slots.default?.())
  },
})

function createDetail(contentUpdatedAt: string | null): WorkEditDetail {
  return {
    work: {
      id: 'work-1',
      title: '测试作品',
      description: '作品简介',
      content: '<p>作品内容</p>',
      banner_image: '',
      cover_image: 'cover-key',
      view_count: 12,
      like_count: 3,
      author: 'author-1',
      author_nickname: '原作者',
      uploader: 'uploader-1',
      is_claim: true,
      status: 'PUBLIC',
      student: 'student-1',
      created_at: '2026-06-01 10:00:00',
      content_updated_at: contentUpdatedAt,
      updated_at: '2026-06-03 12:00:00',
    },
    cover_image_url: 'https://example.test/cover.jpg',
    content_image_urls: [],
    uploader_nickname: '收录者',
    author_display: '展示作者',
    school_name: '学园',
    club_name: '部团',
    student_name: '学生',
  }
}

async function mountDetail(detail: WorkEditDetail) {
  vi.mocked(getWorkEditDetail).mockResolvedValueOnce(detail)

  const wrapper = mount(WorkDetailComp, {
    props: { workId: detail.work.id },
    global: {
      stubs: {
        WorkStatusBadge: ElementStub,
        'el-icon': ElementStub,
        'el-button': ElementStub,
        'el-tag': ElementStub,
        'el-image': ElementStub,
      },
    },
  })

  await flushPromises()
  return wrapper
}

describe('WorkDetailComp', () => {
  beforeEach(() => {
    apiMocks.getWorkEditDetail.mockReset()
  })

  it('uses content_updated_at when present', async () => {
    const wrapper = await mountDetail(createDetail('2026-06-02 11:00:00'))

    // 管理端需要区分作品内容更新时间，避免把后台行记录变更误读为内容变更。
    expect(wrapper.text()).toContain('内容更新：2026-06-02 11:00:00')
  })

  it('falls back to created_at when content_updated_at is null', async () => {
    const wrapper = await mountDetail(createDetail(null))

    expect(wrapper.text()).toContain('内容更新：2026-06-01 10:00:00')
  })

  it('still shows raw updated_at separately for admin observability', async () => {
    const wrapper = await mountDetail(createDetail('2026-06-02 11:00:00'))

    expect(wrapper.text()).toContain('上传：2026-06-01 10:00:00')
    expect(wrapper.text()).toContain('记录更新：2026-06-03 12:00:00')
  })
})
