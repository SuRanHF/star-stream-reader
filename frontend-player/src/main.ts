import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import App from './App.vue';
import router from './router';
import './styles/main.css';
import './styles/lingverse-fidelity.css';

// 默认使用暗色；保留一个显式的 light 分支，方便后续设置页接入主题切换。
const savedTheme = localStorage.getItem('lingverse.player.theme');
document.documentElement.dataset.theme = savedTheme === 'light' ? 'light' : 'dark';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(VueQueryPlugin);
app.use(router);
app.mount('#app');
