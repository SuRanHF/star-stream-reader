<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { schedulerApi, ALL_SCHEDULER_TASKS, type SchedulerSummary, type ScheduledTaskLog } from '@/api/schedulerApi';

// ── Summary ──
const summary = ref<SchedulerSummary | null>(null);
const summaryLoading = ref(false);
const summaryError = ref('');

// ── Actions ──
const globalTickLoading = ref(false);
const selectedTask = ref('');
const runTaskLoading = ref(false);

// ── Logs ──
const logs = ref<ScheduledTaskLog[]>([]);
const logsLoading = ref(false);
const logsError = ref('');
const page = ref(1);
const pageSize = ref(20);
const hasMoreLogs = ref(true);

async function loadSummary() {
  summaryLoading.value = true;
  summaryError.value = '';
  try {
    summary.value = await schedulerApi.getSummary();
  } catch (e) {
    summaryError.value = e instanceof Error ? e.message : '加载调度器摘要失败';
  } finally {
    summaryLoading.value = false;
  }
}

async function handleGlobalTick() {
  globalTickLoading.value = true;
  try {
    const result = await schedulerApi.triggerGlobalTick();
    const lines = [
      `总任务: ${result.totalTasks}`,
      `成功: ${result.successCount}`,
      `失败: ${result.failedCount}`,
      `跳过: ${result.skippedCount}`,
    ];
    if (result.failedCount > 0) {
      const failed = result.taskResults.filter((r) => r.status === 'failed');
      lines.push(...failed.map((r) => `  ${r.taskName}: ${r.message}`));
    }
    ElMessageBox.alert(lines.join('\n'), '全局 Tick 执行结果', {
      confirmButtonText: '确定',
      type: result.failedCount > 0 ? 'warning' : 'success',
    });
    await loadSummary();
    page.value = 1;
    await loadLogs();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '触发全局 tick 失败');
  } finally {
    globalTickLoading.value = false;
  }
}

async function handleRunTask() {
  if (!selectedTask.value) {
    ElMessage.warning('请先选择要运行的任务');
    return;
  }
  runTaskLoading.value = true;
  try {
    const result = await schedulerApi.runTask(selectedTask.value);
    const lines = [
      `任务: ${result.taskName}`,
      `状态: ${result.status}`,
      `影响行数: ${result.affectedCount ?? '-'}`,
      `耗时: ${result.durationMs}ms`,
      `消息: ${result.message ?? '-'}`,
    ];
    if (result.errorMessage) {
      lines.push(`错误: ${result.errorMessage}`);
    }
    ElMessageBox.alert(lines.join('\n'), '任务执行结果', {
      confirmButtonText: '确定',
      type: result.status === 'success' ? 'success' : result.status === 'skipped' ? 'info' : 'warning',
    });
    await loadSummary();
    page.value = 1;
    await loadLogs();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '运行任务失败');
  } finally {
    runTaskLoading.value = false;
  }
}

async function loadLogs() {
  logsLoading.value = true;
  logsError.value = '';
  try {
    const data = await schedulerApi.getLogs(page.value, pageSize.value);
    logs.value = data;
    hasMoreLogs.value = data.length >= pageSize.value;
  } catch (e) {
    logsError.value = e instanceof Error ? e.message : '加载日志失败';
  } finally {
    logsLoading.value = false;
  }
}

function handlePageChange(newPage: number) {
  page.value = newPage;
  loadLogs();
}

function handlePrevPage() {
  if (page.value > 1) {
    page.value--;
    loadLogs();
  }
}

function handleNextPage() {
  if (hasMoreLogs.value) {
    page.value++;
    loadLogs();
  }
}

function refreshAll() {
  loadSummary();
  loadLogs();
}

// ── Computed ──
const statusTagType = computed(() => {
  if (!summary.value) return 'info';
  return summary.value.schedulerEnabled ? 'success' : 'danger';
});

const statusLabel = computed(() => {
  if (!summary.value) return '未知';
  return summary.value.schedulerEnabled ? '运行中' : '已禁用';
});

const tickStatusTagType = computed(() => {
  if (!summary.value?.lastGlobalTickStatus) return 'info';
  const s = summary.value.lastGlobalTickStatus;
  if (s === 'success') return 'success';
  if (s === 'failed') return 'danger';
  if (s === 'skipped') return 'info';
  return 'warning';
});

function logStatusTagType(status: string) {
  if (status === 'success') return 'success';
  if (status === 'failed') return 'danger';
  if (status === 'skipped') return 'info';
  return 'warning';
}

function logStatusLabel(status: string) {
  if (status === 'success') return '成功';
  if (status === 'failed') return '失败';
  if (status === 'skipped') return '跳过';
  return status;
}

function formatTaskName(name: string): string {
  const map: Record<string, string> = {
    expirePkChallenges: '过期 PK 挑战',
    expireBroadcastEvents: '过期放送事件',
    applyWorldlineDecay: '世界线衰减',
    expireFriendRequests: '过期好友请求',
    cleanupOldTaskLogs: '清理旧日志',
    openWorldBossIfNeeded: '开放世界 Boss',
    expireWorldBosses: '过期世界 Boss',
    settleKilledWorldBosses: '结算已击杀 Boss',
    expireDailyQuests: '刷新每日任务',
    expireWeeklyQuests: '刷新每周任务',
    generateAiBroadcast: 'AI 生成放送',
    settleFactionDaily: '阵营日结算',
  };
  return map[name] ?? name;
}

// ── Init ──
onMounted(() => {
  refreshAll();
});
</script>

<template>
  <section>
    <h2 class="page-title">调度器管理</h2>

    <!-- ── Summary Cards ── -->
    <el-row :gutter="16" class="mb-4">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="never" v-loading="summaryLoading">
          <template #header>
            <span>调度器状态</span>
          </template>
          <div v-if="summaryError" class="text-red-500 text-sm">{{ summaryError }}</div>
          <template v-else-if="summary">
            <el-tag :type="statusTagType" size="large">{{ statusLabel }}</el-tag>
          </template>
          <el-skeleton v-else :rows="1" animated />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="never" v-loading="summaryLoading">
          <template #header>
            <span>最后 Tick</span>
          </template>
          <div v-if="summaryError" class="text-red-500 text-sm">--</div>
          <template v-else-if="summary">
            <div v-if="summary.lastGlobalTickAt" class="text-sm">
              <div>{{ summary.lastGlobalTickAt }}</div>
              <el-tag :type="tickStatusTagType" size="small" class="mt-1">
                {{ logStatusLabel(summary.lastGlobalTickStatus ?? '') }}
              </el-tag>
            </div>
            <span v-else class="text-gray-400 text-sm">暂无记录</span>
          </template>
          <el-skeleton v-else :rows="2" animated />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="4">
        <el-card shadow="never" v-loading="summaryLoading">
          <template #header>
            <span>24h 成功</span>
          </template>
          <template v-if="summary">
            <el-statistic :value="summary.successTaskCount24h" />
          </template>
          <el-skeleton v-else :rows="1" animated />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="4">
        <el-card shadow="never" v-loading="summaryLoading">
          <template #header>
            <span>24h 失败</span>
          </template>
          <template v-if="summary">
            <el-statistic :value="summary.failedTaskCount24h">
              <template #suffix>
                <el-tag v-if="summary.failedTaskCount24h > 0" type="danger" size="small">异常</el-tag>
              </template>
            </el-statistic>
          </template>
          <el-skeleton v-else :rows="1" animated />
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="4">
        <el-card shadow="never" v-loading="summaryLoading">
          <template #header>
            <span>24h 跳过</span>
          </template>
          <template v-if="summary">
            <el-statistic :value="summary.skippedTaskCount24h" />
          </template>
          <el-skeleton v-else :rows="1" animated />
        </el-card>
      </el-col>
    </el-row>

    <!-- ── Actions ── -->
    <el-card shadow="never" class="mb-4">
      <template #header>
        <span>调度操作</span>
      </template>
      <div class="flex flex-wrap gap-3 items-center">
        <el-button
          type="primary"
          :loading="globalTickLoading"
          :disabled="globalTickLoading"
          @click="handleGlobalTick"
        >
          {{ globalTickLoading ? '执行中...' : '触发全局 Tick' }}
        </el-button>

        <el-select
          v-model="selectedTask"
          placeholder="选择任务"
          clearable
          :disabled="runTaskLoading"
          style="width: 260px"
        >
          <el-option
            v-for="task in ALL_SCHEDULER_TASKS"
            :key="task"
            :label="formatTaskName(task)"
            :value="task"
          />
        </el-select>

        <el-button
          type="success"
          :loading="runTaskLoading"
          :disabled="!selectedTask || runTaskLoading"
          @click="handleRunTask"
        >
          {{ runTaskLoading ? '执行中...' : '运行选中任务' }}
        </el-button>

        <el-button @click="refreshAll" :disabled="summaryLoading || logsLoading">
          刷新全部
        </el-button>
      </div>
    </el-card>

    <!-- ── Logs Table ── -->
    <el-card shadow="never">
      <template #header>
        <div class="flex justify-between items-center">
          <span>调度任务日志</span>
          <el-pagination
            background
            layout="prev, pager, next"
            :page-size="pageSize"
            :current-page="page"
            :total="hasMoreLogs ? page * pageSize + 1 : page * pageSize"
            @current-change="handlePageChange"
            @prev-click="handlePrevPage"
            @next-click="handleNextPage"
          />
        </div>
      </template>

      <div v-if="logsError" class="text-red-500 mb-3">{{ logsError }}</div>

      <el-table
        :data="logs"
        v-loading="logsLoading"
        stripe
        border
        style="width: 100%"
        empty-text="暂无调度日志"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="任务名称" min-width="170">
          <template #default="{ row }">
            {{ formatTaskName(row.taskName) }}
            <span class="text-gray-400 text-xs ml-1">({{ row.taskName }})</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="logStatusTagType(row.status)" size="small">
              {{ logStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="affectedCount" label="影响行数" width="90" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="message" label="消息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorMessage" class="text-red-500">{{ row.errorMessage }}</span>
            <span v-else class="text-gray-400">--</span>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="finishedAt" label="结束时间" width="170" />
      </el-table>
    </el-card>
  </section>
</template>
