<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { adminApi, type ApiRecord } from '@/api/adminApi';

interface EntityOption {
  key: string;
  label: string;
}

const entities = ref<EntityOption[]>([]);
const selectedEntity = ref('');
const rows = ref<ApiRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const search = ref('');
const page = ref(1);
const pageSize = 20;

// 对话框状态
const dialogVisible = ref(false);
const dialogTitle = ref('');
const formData = ref<ApiRecord>({});
const editingId = ref<number | null>(null);

const entityLabel = computed(() => {
  const found = entities.value.find(e => e.key === selectedEntity.value);
  return found?.label || selectedEntity.value;
});

async function loadEntities() {
  try {
    entities.value = await adminApi.crudEntities();
  } catch (e) {
    ElMessage.error('加载实体列表失败');
  }
}

async function loadRows() {
  if (!selectedEntity.value) return;
  loading.value = true;
  try {
    const data = await adminApi.crudList(selectedEntity.value, {
      search: search.value || undefined,
      limit: pageSize,
      offset: (page.value - 1) * pageSize,
    });
    rows.value = data.rows;
    total.value = data.total;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

function onEntityChange() {
  page.value = 1;
  loadRows();
}

function onSearch() {
  page.value = 1;
  loadRows();
}

function onPageChange(val: number) {
  page.value = val;
  loadRows();
}

function openCreate() {
  dialogTitle.value = `创建 ${entityLabel.value}`;
  formData.value = {};
  editingId.value = null;
  dialogVisible.value = true;
}

function openEdit(row: ApiRecord) {
  dialogTitle.value = `编辑 ${entityLabel.value}`;
  formData.value = { ...row };
  editingId.value = row.id as number;
  dialogVisible.value = true;
}

async function handleDelete(row: ApiRecord) {
  try {
    await ElMessageBox.confirm(
      `确定删除这条${entityLabel.value}记录吗？此操作不可撤销。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    );
    await adminApi.crudDelete(selectedEntity.value, row.id as number);
    ElMessage.success('已删除');
    loadRows();
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败');
    }
  }
}

async function handleSubmit() {
  try {
    if (editingId.value) {
      await adminApi.crudUpdate(selectedEntity.value, editingId.value, formData.value);
      ElMessage.success('已更新');
    } else {
      await adminApi.crudCreate(selectedEntity.value, formData.value);
      ElMessage.success('已创建');
    }
    dialogVisible.value = false;
    loadRows();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  }
}

function getVisibleFields(): string[] {
  if (rows.value.length === 0) return [];
  const row = rows.value[0];
  return Object.keys(row).filter(k =>
    k !== 'id' && !k.endsWith('_json') && !k.endsWith('Json') &&
    !k.endsWith('_at') && !k.endsWith('At')
  );
}

function getFormFields(): string[] {
  const sample = rows.value[0] || {};
  return Object.keys(sample).filter(k =>
    k !== 'id' && !k.endsWith('_at') && !k.endsWith('At')
  );
}

function formatValue(val: unknown): string {
  if (val === null || val === undefined) return '-';
  if (typeof val === 'boolean') return val ? '是' : '否';
  if (typeof val === 'object') return JSON.stringify(val).slice(0, 80);
  return String(val);
}

onMounted(() => loadEntities());
</script>

<template>
  <section>
    <h2 class="page-title">数据管理</h2>

    <div class="mb-4 flex items-center gap-4 flex-wrap">
      <el-select
        v-model="selectedEntity"
        placeholder="选择实体类型"
        @change="onEntityChange"
        style="width: 200px"
      >
        <el-option
          v-for="e in entities"
          :key="e.key"
          :label="e.label"
          :value="e.key"
        />
      </el-select>

      <el-input
        v-model="search"
        placeholder="搜索名称或Key..."
        style="width: 240px"
        clearable
        @clear="onSearch"
        @keyup.enter="onSearch"
      >
        <template #append>
          <el-button @click="onSearch">搜索</el-button>
        </template>
      </el-input>

      <el-button
        v-if="selectedEntity"
        type="primary"
        :icon="Plus"
        @click="openCreate"
      >
        新增
      </el-button>

      <span v-if="selectedEntity" class="text-sm text-gray-500">
        共 {{ total }} 条
      </span>
    </div>

    <el-card v-if="!selectedEntity" shadow="never">
      <el-empty description="请选择一个实体类型开始管理">
        <template #image>
          <span style="font-size: 48px">📋</span>
        </template>
      </el-empty>
    </el-card>

    <el-card v-else shadow="never">
      <el-table :data="rows" v-loading="loading" stripe border style="width:100%" empty-text="暂无数据">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column
          v-for="field in getVisibleFields()"
          :key="field"
          :prop="field"
          :label="field"
          :min-width="field === 'name' || field === 'description' ? 160 : 100"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatValue(row[field]) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item
          v-for="field in getFormFields()"
          :key="field"
          :label="field"
        >
          <el-input
            v-if="typeof formData[field] !== 'boolean'"
            v-model="formData[field]"
            :placeholder="field"
          />
          <el-switch v-else v-model="formData[field]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
