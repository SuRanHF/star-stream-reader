<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { adminApi, type ApiRecord } from '@/api/adminApi';

// ==================== Create World Boss ====================
const createFormRef = ref<FormInstance>();
const createLoading = ref(false);

interface CreateBossForm {
  bossNo: string;
  name: string;
  description: string;
  maxHp: number;
  attack: number;
  defense: number;
  speed: number;
  rewardsJson: string;
  scheduleCron: string;
}

const createForm = reactive<CreateBossForm>({
  bossNo: '',
  name: '',
  description: '',
  maxHp: 10000,
  attack: 200,
  defense: 100,
  speed: 50,
  rewardsJson: '',
  scheduleCron: '',
});

const createRules: FormRules = {
  bossNo: [
    { required: true, message: '请输入 Boss 编号', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入 Boss 名称', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请输入 Boss 描述', trigger: 'blur' },
  ],
  maxHp: [
    { required: true, message: '请输入最大 HP', trigger: 'blur' },
  ],
  attack: [
    { required: true, message: '请输入攻击力', trigger: 'blur' },
  ],
  defense: [
    { required: true, message: '请输入防御力', trigger: 'blur' },
  ],
  speed: [
    { required: true, message: '请输入速度', trigger: 'blur' },
  ],
};

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  createLoading.value = true;
  try {
    const body: Parameters<typeof adminApi.createWorldBoss>[0] = {
      bossNo: createForm.bossNo,
      name: createForm.name,
      description: createForm.description,
      maxHp: createForm.maxHp,
      attack: createForm.attack,
      defense: createForm.defense,
      speed: createForm.speed,
    };
    const trimmedRewards = createForm.rewardsJson.trim();
    if (trimmedRewards) {
      body.rewardsJson = trimmedRewards;
    }
    const trimmedCron = createForm.scheduleCron.trim();
    if (trimmedCron) {
      body.scheduleCron = trimmedCron;
    }
    const result = await adminApi.createWorldBoss(body);
    ElMessage.success(String((result as ApiRecord).message || '世界 Boss 创建成功'));
    createFormRef.value?.resetFields();
    createForm.rewardsJson = '';
    createForm.scheduleCron = '';
    createForm.maxHp = 10000;
    createForm.attack = 200;
    createForm.defense = 100;
    createForm.speed = 50;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败');
  } finally {
    createLoading.value = false;
  }
}

// ==================== Open World Boss ====================
const openBossNo = ref('');
const openLoading = ref(false);

async function handleOpen() {
  const trimmed = openBossNo.value.trim();
  if (!trimmed) {
    ElMessage.warning('请输入 Boss 编号');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认手动开启世界 Boss「${trimmed}」？`,
      '手动开启',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  openLoading.value = true;
  try {
    const result = await adminApi.openWorldBoss(trimmed);
    ElMessage.success(String((result as ApiRecord).message || '世界 Boss 已开启'));
    openBossNo.value = '';
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '开启失败');
  } finally {
    openLoading.value = false;
  }
}

// ==================== Settle World Boss ====================
const settleBossNo = ref('');
const settleLoading = ref(false);

async function handleSettle() {
  const trimmed = settleBossNo.value.trim();
  if (!trimmed) {
    ElMessage.warning('请输入 Boss 编号');
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确认手动结算世界 Boss「${trimmed}」？结算后将发放奖励并关闭本次活动。`,
      '手动结算',
      { type: 'warning', confirmButtonText: '确认结算' },
    );
  } catch {
    return;
  }

  settleLoading.value = true;
  try {
    const result = await adminApi.settleWorldBoss(trimmed);
    ElMessage.success(String((result as ApiRecord).message || '世界 Boss 已结算'));
    settleBossNo.value = '';
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '结算失败');
  } finally {
    settleLoading.value = false;
  }
}
</script>

<template>
  <section>
    <h2 class="page-title">世界 Boss 管理</h2>

    <div class="grid gap-4" style="grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));">
      <!-- Create World Boss -->
      <el-card shadow="never" v-loading="createLoading">
        <template #header>
          <span class="font-semibold">创建世界 Boss</span>
        </template>

        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="createRules"
          label-position="top"
          size="default"
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="Boss 编号" prop="bossNo">
                <el-input v-model="createForm.bossNo" placeholder="例如: dragon_king" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="名称" prop="name">
                <el-input v-model="createForm.name" placeholder="例如: 龙王" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="描述" prop="description">
            <el-input
              v-model="createForm.description"
              type="textarea"
              :rows="3"
              placeholder="Boss 的背景描述..."
            />
          </el-form-item>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="最大 HP" prop="maxHp">
                <el-input-number
                  v-model="createForm.maxHp"
                  :min="1"
                  :step="1000"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="攻击力" prop="attack">
                <el-input-number
                  v-model="createForm.attack"
                  :min="0"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="防御力" prop="defense">
                <el-input-number
                  v-model="createForm.defense"
                  :min="0"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="速度" prop="speed">
                <el-input-number
                  v-model="createForm.speed"
                  :min="0"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="奖励 JSON（可选）">
            <el-input
              v-model="createForm.rewardsJson"
              type="textarea"
              :rows="2"
              placeholder='例如: [{"type":"coins","amount":5000},{"type":"item","key":"dragon_scale"}]'
            />
          </el-form-item>

          <el-form-item label="定时 Cron（可选）">
            <el-input
              v-model="createForm.scheduleCron"
              placeholder="例如: 0 0 12 * * 6（每周六中午12点）"
            />
            <div class="text-xs text-gray-400 mt-1">
              留空则不自动调度，需手动开启。格式：分 时 日 月 周
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="createLoading" @click="handleCreate">
              创建 Boss
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- Actions Column -->
      <div class="flex flex-col gap-4">
        <!-- Open Boss -->
        <el-card shadow="never" v-loading="openLoading">
          <template #header>
            <span class="font-semibold">手动开启 Boss</span>
          </template>
          <p class="text-sm text-gray-500 mb-3">
            输入已创建的 Boss 编号，手动开启一场世界 Boss 活动。
          </p>
          <div class="flex gap-2">
            <el-input
              v-model="openBossNo"
              placeholder="输入 Boss 编号"
              style="flex: 1"
              @keyup.enter="handleOpen"
            />
            <el-button
              type="primary"
              :loading="openLoading"
              :disabled="!openBossNo.trim()"
              @click="handleOpen"
            >
              开启
            </el-button>
          </div>
        </el-card>

        <!-- Settle Boss -->
        <el-card shadow="never" v-loading="settleLoading">
          <template #header>
            <span class="font-semibold">手动结算 Boss</span>
          </template>
          <p class="text-sm text-gray-500 mb-3">
            输入正在进行的 Boss 编号，手动结算并发放奖励。
          </p>
          <div class="flex gap-2">
            <el-input
              v-model="settleBossNo"
              placeholder="输入 Boss 编号"
              style="flex: 1"
              @keyup.enter="handleSettle"
            />
            <el-button
              type="danger"
              :loading="settleLoading"
              :disabled="!settleBossNo.trim()"
              @click="handleSettle"
            >
              结算
            </el-button>
          </div>
        </el-card>

        <!-- Info Card -->
        <el-card shadow="never">
          <template #header>
            <span class="font-semibold">操作说明</span>
          </template>
          <ul class="text-sm text-gray-500 space-y-1 list-disc list-inside">
            <li>创建 Boss 后，Boss 数据将持久化存储。</li>
            <li>设置了定时 Cron 的 Boss 将按计划自动开启。</li>
            <li>未设置 Cron 的 Boss 需手动点击"开启"。</li>
            <li>Boss 结束后需手动结算以发放奖励。</li>
          </ul>
        </el-card>
      </div>
    </div>
  </section>
</template>
