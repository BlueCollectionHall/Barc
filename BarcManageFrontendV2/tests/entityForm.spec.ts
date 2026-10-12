import { describe, expect, it } from 'vitest'
import { deriveEntityId, entityImagePayload, imageFormValue, imageUploadUrl } from '../src/modules/school-club-student/api/entityForm'

describe('基础资料表单契约', () => {
  it('三种实体使用与后端一致的 ID 规则', () => {
    expect(deriveEntityId('North Abydos')).toBe('north_abydos')
  })
  it('保留原始 key，展示签名链接不作为存储值', () => {
    expect(imageFormValue({ logo: 'https://cos.example/a?sign=x', logo_key: 'schools/a/logo/x.png' }, 'logo')).toBe('schools/a/logo/x.png')
    expect(imageFormValue({ logo: 'https://cos.example/a?sign=x' }, 'logo')).toBe('')
    expect(imageFormValue({ logo: 'legacy/key.png' }, 'logo')).toBe('legacy/key.png')
  })
  it('新学生提交三张图片 key，学校编辑不回传图片 URL', () => {
    expect(entityImagePayload('student', false, { avatar_square: 'a', avatar_rectangle: 'b', body_image: 'c' })).toEqual({ avatar_square_key: 'a', avatar_rectangle_key: 'b', body_image_key: 'c' })
    expect(entityImagePayload('student', true, {})).toEqual({})
    expect(entityImagePayload('school', true, { logo: 'x', bg: '' })).toEqual({ logo_key: 'x', beautify_logo_key: null, bg_key: null })
  })
  it('默认相对地址和显式 API 地址均可用于上传', () => {
    expect(imageUploadUrl('', 'schools/a/logo', 'https://manage.example')).toBe('https://manage.example/api/file/upload?path=schools%2Fa%2Flogo')
    expect(imageUploadUrl('https://api.example/', 'clubs/a/bg', 'https://manage.example')).toBe('https://api.example/api/file/upload?path=clubs%2Fa%2Fbg')
  })
})
