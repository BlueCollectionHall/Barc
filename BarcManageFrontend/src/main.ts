import { createApp } from 'vue';
import App from './App.vue';
// 路由
import router from './Routers';
// 状态管理
import { createPinia } from 'pinia';
// ElementUI Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App);
// 创建 Pinia 实例
const pinia = createPinia()

// 使用 Pinia
app.use(pinia)
app.use(router);
app.use(ElementPlus)
app.mount('#app');
