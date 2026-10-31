import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import { queryClient } from './query/queryClient';
import App from './App.vue';
import router from './router';
import './styles/main.css';
import './styles/lingverse-fidelity.css';

// 默认使用浅色；用户在「更多 → 日间/夜间」里选过的主题优先。
const savedTheme = localStorage.getItem('lingverse.player.theme');
document.documentElement.dataset.theme = savedTheme === 'dark' ? 'dark' : 'light';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
app.use(VueQueryPlugin, { queryClient });
app.use(router);
app.mount('#app');
