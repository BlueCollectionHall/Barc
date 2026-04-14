import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import ElementPlus from "element-plus"
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)
app.use(ElementPlus)

import * as icons from '@ant-design/icons-vue'

// 使用 `keyof typeof icons` 确保类型安全
for (const iconName in icons) {
  app.component(iconName, icons[iconName as keyof typeof icons])
}

app.mount('#app')
