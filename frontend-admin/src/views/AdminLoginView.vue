<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { http } from '@/api/http';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();
const usernameOrEmail = ref('');
const password = ref('');
const pending = ref(false);
const errorMessage = ref('');

type LoginResult = {
  username: string;
  role: string;
  token: string;
};

async function submit() {
  if (pending.value) return;
  errorMessage.value = '';
  pending.value = true;
  try {
    const result = await http.post('/auth/login', {
      usernameOrEmail: usernameOrEmail.value.trim(),
      password: password.value,
    }) as unknown as LoginResult;
    if (result.role !== 'admin') {
      errorMessage.value = '此账号没有管理员权限';
      return;
    }
    authStore.setToken(result.token, result.username);
    await router.push('/dashboard');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败';
  } finally {
    pending.value = false;
  }
}
</script>

<template>
  <main class="admin-login-page">
    <el-card class="admin-login-card">
      <template #header>
        <div class="login-title">灵界后台</div>
      </template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名或邮箱">
          <el-input v-model="usernameOrEmail" autocomplete="username" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" placeholder="请输入密码" />
        </el-form-item>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" class="mb-4" />
        <el-button type="primary" class="w-full" native-type="submit" :loading="pending" :disabled="!usernameOrEmail.trim() || !password">登录后台</el-button>
      </el-form>
    </el-card>
  </main>
</template>
