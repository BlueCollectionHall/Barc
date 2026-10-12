import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  // 全量源码行覆盖率，未被测试加载的组件也计入统计。
  test: { coverage: { provider: 'v8', include: ['src/**/*.{ts,vue}'], exclude: ['src/**/__tests__/**', 'src/**/*.d.ts', 'src/test-utils/**'], reporter: ['text', 'html', 'json-summary'], thresholds: { lines: 90 } } },
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
