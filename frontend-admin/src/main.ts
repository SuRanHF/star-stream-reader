import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import './style.css';

// 管理后台默认使用暗色；后续设置页可通过 localStorage 写入 light 切换浅色。
const savedTheme = localStorage.getItem('lingverse.admin.theme');
document.documentElement.dataset.theme = savedTheme === 'light' ? 'light' : 'dark';

const app = createApp(App);
app.use(createPinia());
app.use(ElementPlus);
app.use(router);
app.mount('#app');
