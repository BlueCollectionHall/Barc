import createDOMPurify from 'dompurify'

// 双端与服务端采用同一类排版白名单；每个模块使用独立净化器，避免污染第三方钩子。
const purifier = typeof window === 'undefined' ? null : createDOMPurify(window)
const tags = ['a', 'b', 'blockquote', 'br', 'caption', 'cite', 'code', 'col', 'colgroup', 'dd', 'div', 'dl', 'dt', 'em', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'i', 'img', 'li', 'ol', 'p', 'pre', 'q', 'small', 'span', 'strike', 'strong', 'sub', 'sup', 'table', 'tbody', 'td', 'tfoot', 'th', 'thead', 'tr', 'u', 'ul', 's', 'del', 'hr']
const attributes = ['href', 'src', 'alt', 'title', 'width', 'height', 'colspan', 'rowspan', 'class', 'style', 'data-list', 'rel']
const safeStyle = /^(?:(?:color|background-color)\s*:\s*(?:#[0-9a-f]{3,8}|[a-z]{1,20}|rgba?\([0-9.,% ]{1,40}\))|text-align\s*:\s*(?:left|right|center|justify)|font-weight\s*:\s*(?:normal|bold|[1-9]00)|font-style\s*:\s*(?:normal|italic)|text-decoration\s*:\s*(?:none|underline|line-through))$/i
const safeClass = /^ql-(?:align-(?:center|right|justify)|indent-[1-8]|size-(?:small|large|huge)|font-(?:serif|monospace)|direction-rtl|syntax)$/

purifier?.addHook('uponSanitizeAttribute', (_node, data) => {
  if (data.attrName === 'style') {
    data.attrValue = data.attrValue.split(';').map(value => value.trim()).filter(value => safeStyle.test(value)).join('; ')
    if (!data.attrValue) data.keepAttr = false
  } else if (data.attrName === 'class') {
    data.attrValue = data.attrValue.split(/\s+/).filter(value => safeClass.test(value)).join(' ')
    if (!data.attrValue) data.keepAttr = false
  } else if (data.attrName === 'data-list' && !/^(bullet|ordered|checked|unchecked)$/.test(data.attrValue)) {
    data.keepAttr = false
  } else if (data.attrName === 'src' && !/^https?:\/\//i.test(data.attrValue.trim())) {
    data.keepAttr = false
  } else if (data.attrName === 'href' && !/^(https?:\/\/|mailto:|tel:|#)/i.test(data.attrValue.trim())) {
    data.keepAttr = false
  }
})

/** 服务端已净化的富文本在进入 v-html 前再次校验；无 DOM 环境返回空内容，不回退原始 HTML。 */
export function sanitizeHtmlContent(content: string | null | undefined): string {
  if (!content || typeof window === 'undefined' || !purifier?.isSupported) return ''
  return purifier.sanitize(content, { ALLOWED_TAGS: tags, ALLOWED_ATTR: attributes, ALLOW_DATA_ATTR: false, RETURN_TRUSTED_TYPE: false })
}
