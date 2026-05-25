import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { User } from '@/types/api';

export const TOKEN_KEY = 'lingverse_token';
export const USER_KEY = 'lingverse_user';

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('');
  const user = ref<User | null>(null);
  const isAuthenticated = computed(() => Boolean(token.value));

  function setToken(nextToken: string, nextUser?: User | null) {
    token.value = nextToken;
    localStorage.setItem(TOKEN_KEY, nextToken);
    if (nextUser) {
      user.value = nextUser;
      localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
    }
  }

  function clearAuth() {
    token.value = '';
    user.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  function loadFromStorage() {
    token.value = localStorage.getItem(TOKEN_KEY) || '';
    const rawUser = localStorage.getItem(USER_KEY);
    if (rawUser) {
      try {
        user.value = JSON.parse(rawUser) as User;
      } catch {
        localStorage.removeItem(USER_KEY);
      }
    }
  }

  function logout() {
    clearAuth();
  }

  return {
    token,
    user,
    isAuthenticated,
    setToken,
    clearAuth,
    loadFromStorage,
    logout,
  };
});
