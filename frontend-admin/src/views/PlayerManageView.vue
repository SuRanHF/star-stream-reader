<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminApi, type ApiRecord } from '@/api/adminApi';

const players = ref<ApiRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const search = ref('');
const currentPage = ref(1);
const pageSize = 20;

const drawerVisible = ref(false);
const currentPlayer = ref<ApiRecord>({});
const playerLogs = ref<string[]>([]);

// Grant
const grantOptions = ref<ApiRecord>({});
const grantType = ref('item');
const grantKey = ref('');
const grantQty = ref(1);

// Stat edit
const editLevel = ref(1);
const editHp = ref(100);
const editMaxHp = ref(100);
const editStamina = ref(50);
const editMaxStamina = ref(50);
const editAttack = ref(10);
const editDefense = ref(5);
const editSpeed = ref(10);
const editLuck = ref(1);
const editCritRate = ref(0.05);
const editExp = ref(0);
const editFreePoints = ref(0);
const editInsight = ref(0);
const editChannelHeat = ref(0);
const editWorldLineShift = ref(0);

function sVal(stats: ApiRecord, key: string, def: number): number {
  return Number(stats[key] ?? def);
}

// Resource edit
const editCoins = ref(0);
const editFragments = ref(0);

async function loadPlayers() {
  loading.value = true;
  try {
    const result = await adminApi.listPlayers({
      search: search.value || undefined,
      limit: pageSize,
      offset: (currentPage.value - 1) * pageSize,
    });
    players.value = (result as ApiRecord).players || [];
    total.value = Number((result as ApiRecord).total) || 0;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败');
  } finally {
    loading.value = false;
  }
}

async function viewPlayer(id: number) {
  try {
    const result = await adminApi.getPlayer(id);
    currentPlayer.value = ((result as ApiRecord).player || result || {}) as ApiRecord;
    const st = (currentPlayer.value.stats || {}) as ApiRecord;
    editLevel.value = sVal(st, 'level', 1);
    editHp.value = sVal(st, 'hp', 100);
    editMaxHp.value = sVal(st, 'maxHp', 100);
    editStamina.value = sVal(st, 'stamina', 50);
    editMaxStamina.value = sVal(st, 'maxStamina', 50);
    editAttack.value = sVal(st, 'attack', 10);
    editDefense.value = sVal(st, 'defense', 5);
    editSpeed.value = sVal(st, 'speed', 10);
    editLuck.value = sVal(st, 'luck', 1);
    editCritRate.value = sVal(st, 'critRate', 0.05);
    editExp.value = sVal(st, 'exp', 0);
    editFreePoints.value = sVal(st, 'freePoints', 0);
    editInsight.value = sVal(st, 'insight', 0);
    editChannelHeat.value = sVal(st, 'channelHeat', 0);
    editWorldLineShift.value = sVal(st, 'worldLineShift', 0);
    editCoins.value = Number(currentPlayer.value.coins || 0);
    editFragments.value = Number(currentPlayer.value.story_fragments || 0);
    drawerVisible.value = true;

    const logsResult = await adminApi.getPlayerLogs(id, 30);
    playerLogs.value = ((logsResult as ApiRecord).logs || []) as string[];

    const opts = await adminApi.getGrantOptions();
    grantOptions.value = (opts || {}) as ApiRecord;
    grantKey.value = '';
    grantQty.value = 1;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败');
  }
}

function grantOptionList(type: string): ApiRecord[] {
  const key = type === 'item' ? 'items' : type === 'equipment' ? 'equipments' : type === 'skill' ? 'skills' : 'titles';
  return (grantOptions.value[key] || []) as ApiRecord[];
}

async function doGrant() {
  const id = Number(currentPlayer.value.id);
  if (!id || !grantKey.value) return;
  try {
    const res = await adminApi.grant(id, { type: grantType.value, key: grantKey.value, quantity: grantQty.value });
    ElMessage.success(String((res as ApiRecord).message || '发放成功'));
    viewPlayer(id);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发放失败');
  }
}

async function doQuickAction(action: string) {
  const id = Number(currentPlayer.value.id);
  if (!id) return;
  try {
    const res = await adminApi.quickAction(id, action);
    ElMessage.success(String((res as ApiRecord).message || '操作完成'));
    viewPlayer(id);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  }
}

async function updateStats() {
  const id = Number(currentPlayer.value.id);
  if (!id) return;
  try {
    await adminApi.updatePlayer(id, {
      level: editLevel.value,
      hp: editHp.value,
      maxHp: editMaxHp.value,
      stamina: editStamina.value,
      maxStamina: editMaxStamina.value,
      attack: editAttack.value,
      defense: editDefense.value,
      speed: editSpeed.value,
      luck: editLuck.value,
      critRate: editCritRate.value,
      exp: editExp.value,
      freePoints: editFreePoints.value,
      insight: editInsight.value,
      channelHeat: editChannelHeat.value,
      worldLineShift: editWorldLineShift.value,
      coins: editCoins.value,
      story_fragments: editFragments.value,
    });
    ElMessage.success('属性已更新');
    viewPlayer(id);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败');
  }
}

async function revivePlayer(id: number) {
  try {
    await ElMessageBox.confirm('确认强制复活该玩家？', '强制复活', { type: 'warning' });
    await adminApi.forceRevive(id);
    ElMessage.success('已强制复活');
    loadPlayers();
  } catch { /* cancelled */ }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  loadPlayers();
}

function pVal(key: string): unknown {
  return currentPlayer.value[key] ?? (currentPlayer.value.stats as ApiRecord)?.[key];
}

onMounted(() => loadPlayers());
</script>

<template>
  <section>
    <h2 class="page-title">玩家管理</h2>

    <el-card shadow="never" class="mb-4">
      <div class="flex gap-2">
        <el-input v-model="search" placeholder="搜索玩家名称或ID" style="max-width:300px" clearable @clear="loadPlayers" @keyup.enter="loadPlayers" />
        <el-button type="primary" @click="loadPlayers">搜索</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="players" v-loading="loading" stripe border style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="player_name" label="名称" min-width="120" />
        <el-table-column label="HP" width="90">
          <template #default="{ row }">{{ row.hp }} / {{ row.maxHp }}</template>
        </el-table-column>
        <el-table-column label="位阶" width="90">
          <template #default="{ row }">{{ row.avatarRank || '-' }}</template>
        </el-table-column>
        <el-table-column prop="coins" label="金币" width="90" />
        <el-table-column prop="current_location" label="当前位置" min-width="110" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewPlayer(row.id)">详情</el-button>
            <el-button size="small" type="warning" @click="revivePlayer(row.id)">复活</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-center">
        <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- Player Detail Drawer -->
    <el-drawer v-model="drawerVisible" title="玩家详情" size="550px">
      <template v-if="currentPlayer.id">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ currentPlayer.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ currentPlayer.player_name }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ pVal('level') }}</el-descriptions-item>
          <el-descriptions-item label="HP">{{ pVal('hp') }} / {{ pVal('maxHp') }}</el-descriptions-item>
          <el-descriptions-item label="行动力">{{ pVal('stamina') }} / {{ pVal('maxStamina') }}</el-descriptions-item>
          <el-descriptions-item label="攻击">{{ pVal('attack') }}</el-descriptions-item>
          <el-descriptions-item label="防御">{{ pVal('defense') }}</el-descriptions-item>
          <el-descriptions-item label="速度">{{ pVal('speed') }}</el-descriptions-item>
          <el-descriptions-item label="金币">{{ currentPlayer.coins }}</el-descriptions-item>
          <el-descriptions-item label="故事碎片">{{ currentPlayer.story_fragments }}</el-descriptions-item>
          <el-descriptions-item label="背后星">{{ (currentPlayer.stats as ApiRecord)?.constellation || '无' }}</el-descriptions-item>
          <el-descriptions-item label="当前位置">{{ currentPlayer.current_location }}</el-descriptions-item>
          <el-descriptions-item label="位阶">{{ currentPlayer.avatarRank || '-' }}</el-descriptions-item>
          <el-descriptions-item label="死亡">{{ pVal('isDead') ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="休息中">{{ pVal('isResting') ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 快捷操作 -->
        <h3 class="mt-4 mb-2 text-sm font-semibold">快捷操作</h3>
        <div class="flex flex-wrap gap-1">
          <el-button size="small" @click="doQuickAction('fill_all')">满血满体</el-button>
          <el-button size="small" @click="doQuickAction('fill_hp')">满血</el-button>
          <el-button size="small" @click="doQuickAction('fill_stamina')">满体力</el-button>
          <el-button size="small" @click="doQuickAction('zero_stamina')">清空体力</el-button>
          <el-button size="small" @click="doQuickAction('start_rest')">开始休息</el-button>
          <el-button size="small" @click="doQuickAction('stop_rest')">停止休息</el-button>
          <el-button size="small" type="danger" @click="doQuickAction('kill_player')">杀死</el-button>
          <el-button size="small" type="danger" plain @click="doQuickAction('clear_death')">清除死亡</el-button>
          <el-button size="small" type="warning" @click="doQuickAction('fill_rank_requirements')">满足升阶条件</el-button>
          <el-button size="small" type="success" @click="doQuickAction('force_rank_up')">强制升阶</el-button>
        </div>

        <!-- 属性修改 -->
        <h3 class="mt-4 mb-2 text-sm font-semibold">属性修改</h3>
        <div class="grid gap-2" style="grid-template-columns: repeat(4, 1fr);">
          <div><span class="text-xs">等级</span><el-input-number v-model="editLevel" :min="1" size="small" style="width:100%" /></div>
          <div><span class="text-xs">HP</span><el-input-number v-model="editHp" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">最大HP</span><el-input-number v-model="editMaxHp" :min="1" size="small" style="width:100%" /></div>
          <div><span class="text-xs">行动力</span><el-input-number v-model="editStamina" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">最大行动力</span><el-input-number v-model="editMaxStamina" :min="1" size="small" style="width:100%" /></div>
          <div><span class="text-xs">攻击</span><el-input-number v-model="editAttack" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">防御</span><el-input-number v-model="editDefense" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">速度</span><el-input-number v-model="editSpeed" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">幸运</span><el-input-number v-model="editLuck" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">暴击率</span><el-input-number v-model="editCritRate" :min="0" :max="1" :step="0.01" :precision="2" size="small" style="width:100%" /></div>
          <div><span class="text-xs">经验</span><el-input-number v-model="editExp" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">自由点数</span><el-input-number v-model="editFreePoints" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">洞察</span><el-input-number v-model="editInsight" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">频道热度</span><el-input-number v-model="editChannelHeat" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">世界线偏移</span><el-input-number v-model="editWorldLineShift" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">金币</span><el-input-number v-model="editCoins" :min="0" size="small" style="width:100%" /></div>
          <div><span class="text-xs">故事碎片</span><el-input-number v-model="editFragments" :min="0" size="small" style="width:100%" /></div>
        </div>
        <div class="mt-2">
          <el-button size="small" type="primary" @click="updateStats">保存属性</el-button>
        </div>

        <!-- 发放物品 -->
        <h3 class="mt-4 mb-2 text-sm font-semibold">发放物品</h3>
        <div class="flex gap-2 items-center">
          <el-select v-model="grantType" size="small" style="width:90px">
            <el-option label="道具" value="item" />
            <el-option label="装备" value="equipment" />
            <el-option label="技能" value="skill" />
            <el-option label="称号" value="title" />
          </el-select>
          <el-select v-model="grantKey" size="small" placeholder="选择" style="width:200px" filterable>
            <el-option v-for="opt in grantOptionList(grantType)" :key="String(opt.key)" :label="String(opt.name || opt.key)" :value="String(opt.key)" />
          </el-select>
          <el-input-number v-if="grantType === 'item'" v-model="grantQty" :min="1" size="small" style="width:70px" />
          <el-button size="small" type="primary" :disabled="!grantKey" @click="doGrant">发放</el-button>
        </div>

        <!-- 日志 -->
        <h3 class="mt-4 mb-2 text-sm font-semibold">最近日志</h3>
        <div v-if="playerLogs.length" class="max-h-60 overflow-y-auto rounded border p-2 text-xs">
          <p v-for="(log, i) in playerLogs" :key="i" class="border-b py-1 text-gray-500">{{ log }}</p>
        </div>
        <p v-else class="text-xs text-gray-400">暂无日志</p>
      </template>
      <el-empty v-else description="未加载玩家数据" />
    </el-drawer>
  </section>
</template>
