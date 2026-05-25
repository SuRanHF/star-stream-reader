<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { adminApi } from '@/api/adminApi';

const playerCount = ref(0);
const loading = ref(true);
const error = ref('');

onMounted(async () => {
  try {
    const result = await adminApi.listPlayers({ limit: 1 });
    playerCount.value = result.total || 0;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section>
    <h2 class="page-title">Dashboard</h2>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never">
          <el-statistic title="玩家总数" :value="loading ? '加载中...' : playerCount" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never">
          <el-statistic title="管理端" value="Phase 9B" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never">
          <el-statistic title="后端" value="Spring Boot 3" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never">
          <el-statistic title="API" value="已就绪" />
        </el-card>
      </el-col>
    </el-row>
    <p v-if="error" class="mt-4 text-red-500">{{ error }}</p>
  </section>
</template>
