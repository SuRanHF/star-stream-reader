<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete } from '@element-plus/icons-vue';
import { adminApi, type ApiRecord } from '@/api/adminApi';

const broadcasts = ref<ApiRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const search = ref('');
const page = ref(1);
const pageSize = 20;

// 对话框
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formData = ref<ApiRecord>({});
const editingId = ref<number | null>(null);

async function loadBroadcasts() {
  loading.value = true;
  try {
    const data = await adminApi.crudList('broadcast-events', {
      search: search.value || undefined,
      limit: pageSize,
      offset: (page.value - 1) * pageSize,
    });
    broadcasts.value = data.rows;
    total.value = data.total;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载广播失败');
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  page.value = 1;
  loadBroadcasts();
}

function onPageChange(val: number) {
  page.value = val;
  loadBroadcasts();
}

function openCreate() {
  dialogTitle.value = '创建广播';
  formData.value = { type: 'worldline', status: 'active', targetValue: 100, currentValue: 0 };
  editingId.value = null;
  dialogVisible.value = true;
}

function openEdit(row: ApiRecord) {
  dialogTitle.value = '编辑广播';
  formData.value = { ...row };
  editingId.value = row.id as number;
  dialogVisible.value = true;
}

async function handleDelete(row: ApiRecord) {
  try {
    await ElMessageBox.confirm('确定删除此广播吗？', '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
    await adminApi.crudDelete('broadcast-events', row.id as number);
    ElMessage.success('已删除');
    loadBroadcasts();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '删除失败');
  }
}

async function handleSubmit() {
  try {
    if (editingId.value) {
      await adminApi.crudUpdate('broadcast-events', editingId.value, formData.value);
      ElMessage.success('已更新');
    } else {
      await adminApi.crudCreate('broadcast-events', formData.value);
      ElMessage.success('已创建');
    }
    dialogVisible.value = false;
    loadBroadcasts();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  }
}

function calcProgress(row: ApiRecord): number {
  const cur = Number(row.currentValue || 0);
  const tar = Math.max(Number(row.targetValue || 1), 1);
  return Math.round((cur / tar) * 100);
}

const formFields = ['title', 'description', 'type', 'status', 'targetValue', 'currentValue', 'eventKey', 'startAt', 'endAt'];

onMounted(() => loadBroadcasts());
</script>

<template>
  <section>
    <h2 class="page-title">星流放送</h2>

    <div class="mb-4 flex items-center gap-4 flex-wrap">
      <el-input v-model="search" placeholder="搜索标题..." style="width: 240px" clearable
        @clear="onSearch" @keyup.enter="onSearch">
        <template #append><el-button @click="onSearch">搜索</el-button></template>
      </el-input>
      <el-button type="primary" :icon="Plus" @click="openCreate">创建广播</el-button>
      <span class="text-sm text-gray-500">共 {{ total }} 条</span>
    </div>

    <el-card shadow="never">
      <el-table :data="broadcasts" v-loading="loading" stripe border style="width:100%" empty-text="暂无广播">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="eventKey" label="编号" width="120" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="calcProgress(row)" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : row.status === 'finished' ? 'info' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :icon="Delete" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total"
          layout="total, prev, pager, next" @current-change="onPageChange" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item v-for="f in formFields" :key="f" :label="f">
          <el-input v-model="formData[f]" :placeholder="f" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
