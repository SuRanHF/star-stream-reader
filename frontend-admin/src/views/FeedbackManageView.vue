<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { adminApi, type ApiRecord } from '@/api/adminApi';

const feedbacks = ref<ApiRecord[]>([]);
const loading = ref(false);
const statusFilter = ref('');

async function loadFeedbacks() {
  loading.value = true;
  try {
    const result = await adminApi.getFeedback({ status: statusFilter.value || undefined });
    feedbacks.value = result.feedback || [];
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载反馈失败');
  } finally {
    loading.value = false;
  }
}

async function updateStatus(id: number, status: string) {
  try {
    await adminApi.updateFeedback(id, { status });
    ElMessage.success(`已标记为 ${status}`);
    loadFeedbacks();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败');
  }
}

onMounted(() => loadFeedbacks());
</script>

<template>
  <section>
    <h2 class="page-title">反馈管理</h2>

    <div class="mb-4 flex gap-2">
      <el-select v-model="statusFilter" placeholder="按状态筛选" clearable @change="loadFeedbacks" style="width:150px">
        <el-option label="全部" value="" />
        <el-option label="待处理" value="pending" />
        <el-option label="处理中" value="processing" />
        <el-option label="已解决" value="resolved" />
      </el-select>
      <el-button type="primary" @click="loadFeedbacks">刷新</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="feedbacks" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nickname" label="提交者" width="120" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="updateStatus(row.id, 'processing')">处理中</el-button>
            <el-button size="small" type="success" @click="updateStatus(row.id, 'resolved')">解决</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
