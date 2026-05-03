import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const frameworkPackages = ['vue', 'vue-router', 'pinia']
const elementPlusPackages = ['element-plus', '@element-plus']
const elementPlusVendorPackages = [
  '@ctrl',
  '@floating-ui',
  '@popperjs/core',
  '@sxzz/popperjs-es',
  '@vueuse/core',
  '@vueuse/shared',
  'async-validator',
  'dayjs',
  'lodash-es',
  'lodash-unified',
  'memoize-one',
  'normalize-wheel-es',
]
const noticeEditorPackages = ['@vueup/vue-quill', 'quill', 'parchment', 'quill-delta', 'eventemitter3']

function isNodeModulePackage(moduleId: string, packageName: string): boolean {
  return moduleId.includes(`/node_modules/${packageName}/`)
}

function matchesNodeModulePackage(moduleId: string, packageNames: string[]): boolean {
  return packageNames.some((packageName) => isNodeModulePackage(moduleId, packageName))
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replaceAll('\\', '/')

          if (!normalizedId.includes('/node_modules/')) {
            return undefined
          }

          if (normalizedId.includes('/node_modules/@vue/')) {
            return 'vue-core'
          }

          if (matchesNodeModulePackage(normalizedId, frameworkPackages)) {
            return 'vue-core'
          }

          if (matchesNodeModulePackage(normalizedId, elementPlusPackages)) {
            return 'element-plus'
          }

          if (matchesNodeModulePackage(normalizedId, elementPlusVendorPackages)) {
            return 'element-plus-vendors'
          }

          if (matchesNodeModulePackage(normalizedId, noticeEditorPackages)) {
            return 'notice-editor'
          }

          return undefined
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-utils/setup.ts'],
    css: false,
  },
})
