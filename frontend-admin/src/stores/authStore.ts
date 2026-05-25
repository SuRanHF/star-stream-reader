import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export const ADMIN_TOKEN_KEY = 'lingverse_admin_token';

export const useAuthStore = defineStore('adminAuth', () => {
  const token = ref(localStorage.getItem(ADMIN_TOKEN_KEY) || '');
  const adminName = ref(localStorage.getItem('lingverse_admin_name') || '管理员');
  const isAuthenticated = computed(() => Boolean(token.value));

  function setToken(nextToken: string, name = '管理员') {
    token.value = nextToken;
    adminName.value = name;
    localStorage.setItem(ADMIN_TOKEN_KEY, nextToken);
    localStorage.setItem('lingverse_admin_name', name);
  }

  function clearAuth() {
    token.value = '';
    adminName.value = '管理员';
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    localStorage.removeItem('lingverse_admin_name');
  }

  return { token, adminName, isAuthenticated, setToken, clearAuth };
});
