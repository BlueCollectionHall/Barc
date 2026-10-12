/** 基础资料 ID 与后端统一由英文名派生，新实体不另设可冲突的自定义 ID。 */
export function deriveEntityId(enName: string): string {
  return enName.toLowerCase().replace(/ /g, '_')
}

/** 展示 URL 仅用于预览，保存只使用服务端返回的原始 key。 */
export function imageFormValue(data: Record<string, unknown>, field: string): string {
  const key = data[`${field}_key`]
  if (typeof key === 'string') return key
  const legacy = data[field]
  return typeof legacy === 'string' && !/^https?:\/\//i.test(legacy) ? legacy : ''
}

/** 同源相对路径和独立 API 域名都能得到一致的上传地址。 */
export function imageUploadUrl(apiBase: string, prefix: string, origin: string): string {
  return new URL(`${apiBase.replace(/\/$/, '')}/api/file/upload?path=${encodeURIComponent(prefix)}`, origin).toString()
}

/** 新建学生必须携带图片 key；编辑学生继续使用独立图片保存接口。 */
export function entityImagePayload(type: 'school' | 'club' | 'student', isEdit: boolean, form: Record<string, unknown>): Record<string, string | null> {
  const fields = type === 'school' ? ['logo', 'beautify_logo', 'bg'] : type === 'club' ? ['logo', 'bg'] : isEdit ? [] : ['avatar_square', 'avatar_rectangle', 'body_image']
  return Object.fromEntries(fields.map(field => [`${field}_key`, typeof form[field] === 'string' && form[field] ? form[field] : null]))
}
