<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';

const router = useRouter();
const authStore = useAuthStore();
const token = ref('');
const adminName = ref('管理员');

async function submit() {
  authStore.setToken(token.value.trim() || 'dev-admin-placeholder', adminName.value.trim() || '管理员');
  await router.push('/dashboard');
}
</script>

<template>
  <main class="admin-login-page">
    <el-card class="admin-login-card">
      <template #header>
        <div class="login-title">灵界后台</div>
      </template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="管理员名称">
          <el-input v-model="adminName" placeholder="管理员" />
        </el-form-item>
        <el-form-item label="Token / 管理密钥占位">
          <el-input v-model="token" type="password" show-password placeholder="本轮仅保留入口" />
        </el-form-item>
        <el-button type="primary" class="w-full" @click="submit">进入后台</el-button>
      </el-form>
    </el-card>
  </main>
</template>
