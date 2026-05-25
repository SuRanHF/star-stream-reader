<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi, type ApiRecord } from '@/api/adminApi';

const actions = ref<ApiRecord[]>([]);
const loading = ref(false);

async function loadActions() {
  loading.value = true;
  try {
    const result = await adminApi.getActions(50);
    actions.value = result.actions || [];
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载操作日志失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => loadActions());
</script>

<template>
  <section>
    <h2 class="page-title">操作日志</h2>

    <div class="mb-4">
      <el-button type="primary" @click="loadActions">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="actions" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="adminName" label="操作者" width="120" />
        <el-table-column prop="action" label="操作" min-width="200" show-overflow-tooltip />
        <el-table-column prop="targetType" label="目标类型" width="100" />
        <el-table-column prop="targetId" label="目标ID" width="80" />
        <el-table-column prop="createdAt" label="时间" width="160" />
      </el-table>
    </el-card>
  </section>
</template>
