import type { School } from './school'
import type { Club } from './club'
import type { Student } from './student'

/** 读取保留具体实体类型；表单写入允许可选文本为空，禁止混入非文本值。 */
export type EntityRecord = School & Partial<Club> & Partial<Student>
export interface EntityDraft {
  id: string
  en_name: string
  cn_name: string
  [field: string]: string | null | undefined
}

/** 学生所属关系由独立参数交给后端验证，正文不重复传递关系字段。 */
export function studentContent(data: EntityDraft): EntityDraft {
  const result = { ...data }
  for (const key of ['school', 'club', 'school_id', 'club_id']) delete result[key]
  return result
}
