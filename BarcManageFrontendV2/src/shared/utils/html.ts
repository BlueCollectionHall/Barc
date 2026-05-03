const BLOCKED_TAGS = ['script', 'iframe', 'object', 'embed', 'style', 'link', 'meta'] as const
const URL_ATTRIBUTES = ['href', 'src', 'xlink:href'] as const

function sanitizeNodeAttributes(element: Element): void {
  for (const attribute of [...element.getAttributeNames()]) {
    const normalizedName = attribute.toLowerCase()
    const value = element.getAttribute(attribute)?.trim() ?? ''

    if (normalizedName.startsWith('on')) {
      element.removeAttribute(attribute)
      continue
    }

    if (URL_ATTRIBUTES.includes(normalizedName as (typeof URL_ATTRIBUTES)[number]) && /^javascript:/i.test(value)) {
      element.removeAttribute(attribute)
      continue
    }

    if (normalizedName === 'style' && /expression\s*\(|javascript:/i.test(value)) {
      element.removeAttribute(attribute)
    }
  }
}

export function sanitizeHtmlContent(content: string | null | undefined): string {
  if (!content) {
    return ''
  }

  if (typeof window === 'undefined' || typeof DOMParser === 'undefined') {
    return content
  }

  const documentFragment = new DOMParser().parseFromString(content, 'text/html')
  BLOCKED_TAGS.forEach((tag) => {
    documentFragment.querySelectorAll(tag).forEach((node) => {
      node.remove()
    })
  })

  documentFragment.querySelectorAll('*').forEach((element) => {
    sanitizeNodeAttributes(element)
  })

  return documentFragment.body.innerHTML
}
