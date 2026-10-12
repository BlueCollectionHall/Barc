// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest'
import { sanitizeHtmlContent } from '../src/utils/richText'

afterEach(() => vi.unstubAllGlobals())

// 解析最终输出再断言攻击载荷被移除，避免只对实现中的正则重复作相同判断。
it('移除执行标签、事件、混淆协议与非 HTTP 图片，保留正文', () => {
  const html = sanitizeHtmlContent('<p onclick="alert(1)">正文<script>alert(1)</script><svg><g onload="alert(1)"></g></svg><math><mtext>x</mtext></math><iframe src="https://example.test"></iframe><a href="java&#x0a;script:alert(1)">危险链接</a><img src="data:image/svg+xml;base64,AAAA" onerror="alert(1)"><img src="https://image.example.test/a.png" onerror="alert(1)"></p>')
  const dom = new DOMParser().parseFromString(html, 'text/html')
  expect(dom.querySelector('script,svg,math,iframe,[onclick],[onerror],[onload]')).toBeNull()
  expect(dom.querySelector('a')?.hasAttribute('href')).toBe(false)
  expect(dom.querySelectorAll('img')[0]?.hasAttribute('src')).toBe(false)
  expect(dom.querySelectorAll('img')[1]?.getAttribute('src')).toBe('https://image.example.test/a.png')
  expect(dom.body.textContent).toContain('正文')
})

it('保留编辑器安全排版、列表、链接和表格，剔除外部样式与任意类名', () => {
  const html = sanitizeHtmlContent('<p class="ql-align-center ql-indent-2 arbitrary" style="color: #ff0000; background-image: url(https://tracker.test); text-align: center; position: fixed"><strong>正文</strong><a href="https://example.test">来源</a></p><ol><li data-list="ordered">项目</li><li data-list="bad">非法</li></ol><table><tbody><tr><td colspan="2">单元格</td></tr></tbody></table>')
  const dom = new DOMParser().parseFromString(html, 'text/html')
  expect(dom.querySelector('p')?.getAttribute('class')).toBe('ql-align-center ql-indent-2')
  expect(dom.querySelector('p')?.getAttribute('style')).toBe('color: #ff0000; text-align: center')
  expect(dom.querySelector('a')?.getAttribute('href')).toBe('https://example.test')
  expect(dom.querySelector('strong')?.textContent).toBe('正文')
  expect(dom.querySelectorAll('li')[0]?.getAttribute('data-list')).toBe('ordered')
  expect(dom.querySelectorAll('li')[1]?.hasAttribute('data-list')).toBe(false)
  expect(dom.querySelector('td')?.getAttribute('colspan')).toBe('2')
})

it('嵌套命名空间和伪造属性不能逃出 HTML 白名单', () => {
  const html = sanitizeHtmlContent('<math><mtext><table><mglyph><style><!--</style><img title="--><img src=x onerror=alert(1)>"></table></mtext></math><div class="evil" style="expression(alert(1))"><a href="data:text/html,evil">链接</a></div>')
  const dom = new DOMParser().parseFromString(html, 'text/html')
  expect(dom.querySelector('math,svg,style,[onerror],[href],[class],[style]')).toBeNull()
  expect(dom.body.textContent).toContain('链接')
})

it('空内容和无 DOM 环境不会回退未经净化的 HTML', () => {
  expect(sanitizeHtmlContent(null)).toBe(''); expect(sanitizeHtmlContent(undefined)).toBe(''); expect(sanitizeHtmlContent('')).toBe('')
  vi.stubGlobal('window', undefined)
  expect(sanitizeHtmlContent('<img onerror="alert(1)">')).toBe('')
})
