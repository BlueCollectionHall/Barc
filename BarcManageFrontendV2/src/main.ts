import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from '@/app/App.vue'
import router from '@/app/router'
import '@/shared/styles/tokens.css'
import '@/shared/styles/base.css'
import '@/shared/styles/element-plus.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
