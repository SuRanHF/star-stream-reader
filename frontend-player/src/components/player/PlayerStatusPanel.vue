<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { Player, PlayerStats } from '@/types/player';
import type { ApiRecord } from '@/types/api';
import { equipmentApi } from '@/api/equipmentApi';
import { rankingApi } from '@/api/rankingApi';
import { useUiStore } from '@/stores/uiStore';
import AllocDialog from '@/components/player/AllocDialog.vue';

const ui = useUiStore();

const props = defineProps<{
  player: Player | null;
}>();

function stats(): PlayerStats {
  return props.player?.stats || props.player?.statsJson || {};
}

function num(value: unknown): number {
  return typeof value === 'number' ? value : Number(value || 0);
}

function compact(value: unknown) {
  const next = num(value);
  if (next >= 10000) return `${(next / 10000).toFixed(next >= 100000 ? 0 : 1)}万`;
  return String(next);
}

function pct(value: unknown, max: unknown) {
  const nextMax = num(max);
  if (!nextMax) return 0;
  return Math.max(0, Math.min(100, Math.round((num(value) / nextMax) * 1000) / 10));
}

interface EquipmentRow {
  slot: string;
  name: string;
  detail: string;
}

const SLOT_LABELS: Record<string, string> = {
  mainHand: '主手',
  offHand: '副手',
  head: '头部',
  body: '外装',
  legs: '腿甲',
  feet: '足部',
  accessory: '遗物',
  accessory1: '遗物',
  accessory2: '遗物',
  bag: '背包',
  // 兼容旧数据库 slot 命名
  weapon: '武器',
  armor: '防具',
  relic: '遗物',
};

function slotLabel(slot: string): string {
  return SLOT_LABELS[slot] || slot || '装备';
}

const CONSTELLATION_MAP: Record<string, string> = {
  demon_judge_of_fire: '惡魔般的火之審判者',
  master_of_steel: '鋼鐵之主',
  prisoner_of_golden_headband: '金箍棒囚徒',
  abyssal_black_flame_dragon: '深淵黑色焰龍',
  queen_of_darkest_spring: '最黑暗春天的女王',
  father_of_rich_night: '富裕夜晚之父',
  scribe_of_heaven: '天堂的抄寫員',
  morning_star: '晨星',
};


function constellationDisplay(): string {
  const s = stats();
  const key = String(s.constellation || '');
  if (!key) return '未选择';
  return CONSTELLATION_MAP[key] || '未选择';
}

function rarityLabel(rarity: string): string {
  const map: Record<string, string> = {
    legendary: '传说',
    epic: '史诗',
    rare: '稀有',
    uncommon: '精良',
    common: '普通',
  };
  return map[rarity] || rarity || '';
}

function roundDurability(durability: unknown): number {
  return Math.round(num(durability));
}

function buildDetail(item: ApiRecord): string {
  const parts: string[] = [];
  const bonus = item.bonus || item.statBonus;
  if (bonus) {
    const n = num(bonus);
    if (n > 0) parts.push(`+${n}`);
    else if (n < 0) parts.push(`${n}`);
  }
  if (item.durability !== undefined && item.durability !== null) {
    const d = roundDurability(item.durability);
    parts.push(`耐久${d}%`);
  }
  if (item.bonusDesc || item.effect) {
    parts.push(String(item.bonusDesc || item.effect));
  }
  return parts.join(' ') || '';
}

const equippedItems = ref<EquipmentRow[]>([]);

async function loadEquippedEquipment() {
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId) {
    equippedItems.value = [];
    return;
  }
  try {
    const equipped = await equipmentApi.getEquipped(playerId);
    const entries = Object.values(equipped).filter(
      (item): item is ApiRecord => item != null && typeof item === 'object' && (item as ApiRecord).equipped === true
    );
    if (entries.length > 0) {
      equippedItems.value = entries.map((item: ApiRecord) => {
        const slot = String(item.slot || '');
        const rarity = rarityLabel(String(item.rarity || ''));
        const nameRaw = String(item.equipmentName || item.name || item.equipmentKey || '未知装备');
        const name = rarity ? `${rarity}·${nameRaw}` : nameRaw;
        return { slot: slotLabel(slot), name, detail: buildDetail(item) };
      });
    } else {
      equippedItems.value = [];
    }
  } catch {
    equippedItems.value = [];
  }
}

onMounted(loadEquippedEquipment);
watch(() => props.player, loadEquippedEquipment);

const tooltipKey = ref<string | null>(null);
const tooltipX = ref(0);
const tooltipY = ref(0);

function b(key: string): number {
  const s = stats();
  return s[key] instanceof Number || typeof s[key] === 'number' ? Number(s[key]) : 0;
}

const tooltipText = computed(() => {
  const s = stats();
  const lv = num(s.level) || 1;
  const constName = String(s.bonusConstellationName || '');

  function constPart(value: number): string {
    if (!value) return '';
    return constName ? `背后星·${constName}(+${value})` : `背后星(+${value})`;
  }
  function constPartPct(value: number): string {
    if (!value) return '';
    return constName ? `背后星·${constName}(+${(value * 100).toFixed(0)}%)` : `背后星(+${(value * 100).toFixed(0)}%)`;
  }
  function equipPart(value: number, detail: string): string {
    if (detail) return `装备·${detail}`;
    return value ? `装备(+${value})` : '';
  }
  function equipPartPct(value: number, detail: string): string {
    if (detail) return `装备·${detail}`;
    return value ? `装备(+${(value * 100).toFixed(0)}%)` : '';
  }
  function skillPart(value: number, detail: string): string {
    if (detail) return `技能·${detail}`;
    return value ? `技能(+${value})` : '';
  }
  function skillPartPct(value: number, detail: string): string {
    if (detail) return `技能·${detail}`;
    return value ? `技能(+${(value * 100).toFixed(0)}%)` : '';
  }
  function factionPart(value: number, detail: string): string {
    if (detail) return `阵营·${detail}`;
    return value ? `阵营(+${value})` : '';
  }

  const eqAtkDetail = String(s.bonusAtkEquipmentDetail || '');
  const skAtkDetail = String(s.bonusAtkSkillDetail || '');
  const faAtkDetail = String(s.bonusAtkFactionDetail || '');
  const partsAtk = [
    `基础(${b('bonusAtkBase')})`,
    `属性点(${b('bonusAtkAlloc')})`,
    constPart(b('bonusAtkConstellation')),
    equipPart(b('bonusAtkEquipment'), eqAtkDetail),
    skillPart(b('bonusAtkSkill'), skAtkDetail),
    factionPart(b('bonusAtkFaction'), faAtkDetail),
  ].filter(Boolean).join(' + ');
  const totalAtk = num(s.attack);

  const eqDefDetail = String(s.bonusDefEquipmentDetail || '');
  const skDefDetail = String(s.bonusDefSkillDetail || '');
  const partsDef = [
    `基础(${b('bonusDefBase')})`,
    `属性点(${b('bonusDefAlloc')})`,
    constPart(b('bonusDefConstellation')),
    equipPart(b('bonusDefEquipment'), eqDefDetail),
    skillPart(b('bonusDefSkill'), skDefDetail),
  ].filter(Boolean).join(' + ');
  const totalDef = num(s.defense);

  const eqSpdDetail = String(s.bonusSpdEquipmentDetail || '');
  const skSpdDetail = String(s.bonusSpdSkillDetail || '');
  const partsSpd = [
    `基础(${b('bonusSpdBase')})`,
    `属性点(${b('bonusSpdAlloc')})`,
    constPart(b('bonusSpdConstellation')),
    equipPart(b('bonusSpdEquipment'), eqSpdDetail),
    skillPart(b('bonusSpdSkill'), skSpdDetail),
  ].filter(Boolean).join(' + ');
  const totalSpd = num(s.speed);

  const eqCritDetail = String(s.bonusCritEquipmentDetail || '');
  const skCritDetail = String(s.bonusCritSkillDetail || '');
  const partsCrit = [
    `属性点(${(b('bonusCritAlloc') * 2).toFixed(0)}%)`,
    constPartPct(b('bonusCritConstellation')),
    equipPartPct(b('bonusCritEquipment'), eqCritDetail),
    skillPartPct(b('bonusCritSkill'), skCritDetail),
  ].filter(Boolean).join(' + ');
  const totalCrit = num(s.critRate);

  const eqHpDetail = String(s.bonusMaxHpEquipmentDetail || '');
  const skHpDetail = String(s.bonusMaxHpSkillDetail || '');
  const partsMaxHp = [
    `基础(${b('bonusMaxHpBase')})`,
    constPart(b('bonusMaxHpConstellation')),
    equipPart(b('bonusMaxHpEquipment'), eqHpDetail),
    skillPart(b('bonusMaxHpSkill'), skHpDetail),
  ].filter(Boolean).join(' + ');
  const totalMaxHp = num(s.maxHp);

  const lines: Record<string, string> = {
    hp: `最大生命 = ${partsMaxHp} = ${totalMaxHp}\n`
      + `当前: ${compact(s.hp)} / ${compact(s.maxHp)}\n`
      + `归零时化身将陷入死亡状态。`,
    stamina: `行动力上限: ${compact(s.maxStamina)}\n`
      + `当前: ${compact(s.stamina)} / ${compact(s.maxStamina)}\n`
      + `探索场景、执行行动的核心资源。休息中自动恢复。`,
    attack: `攻击力 = ${partsAtk} = ${totalAtk}\n`
      + `1属性点 = 1攻击力`,
    defense: `防御力 = ${partsDef} = ${totalDef}\n`
      + `1属性点 = 1防御力`,
    power: `综合战力 = 攻击×2 + 防御×1.5 + 反应×1.2 + 最大生命×0.3\n`
      + `= ${totalAtk}×2 + ${totalDef}×1.5 + ${totalSpd}×1.2 + ${totalMaxHp}×0.3 = ${computedPower()}`,
    speed: `反应 = ${partsSpd} = ${totalSpd}\n`
      + `1属性点 = 1反应速度\n决定战斗中的行动优先级。`,
    luck: `概率修正: ${compact(s.luck)}\n`
      + `影响探索掉落率、暴击率、隐藏事件发现概率。`,
    channelHeat: `频道热度: ${compact(s.channelHeat)}\n`
      + `反映在星流频道中的影响力。影响特殊事件与隐藏选项。`,
    worldLineShift: `世界线偏移: ${compact(s.worldLineShift)}\n`
      + `偏移越大越容易触发特殊结局。`,
    critRate: `暴击率 = ${partsCrit || '基础(0%)'} = ${(totalCrit * 100).toFixed(1)}%\n`
      + `1属性点 = 2%暴击率`,
    rank: `位阶决定化身的能力上限和可解锁内容。\n`
      + `通过经验积累和位阶校准提升。`,
    exp: `经验: ${compact(s.exp)} / ${compact(s.maxExp)}\n`
      + `通过探索、任务、战斗获得。\n`
      + `满足升阶条件后可进行位阶校准。`,
    level: `等级 = floor(√(经验/100)) + 1\n`
      + `= floor(√(${num(s.exp)}/100)) + 1 = ${lv}\n`
      + `每升一级: 自由属性点+3`,
  };

  const key = tooltipKey.value;
  return key ? lines[key] || '' : '';
});

function showTooltip(key: string, e: MouseEvent) {
  if (tooltipKey.value === key) {
    tooltipKey.value = null;
    return;
  }
  tooltipKey.value = key;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  tooltipX.value = rect.right + 8;
  tooltipY.value = rect.top;
}

function hideTooltip() {
  tooltipKey.value = null;
}

const showAllocDialog = ref(false);
const rankingUp = ref(false);
const emit = defineEmits<{ playerUpdated: [data: unknown] }>();

async function handleRankUp() {
  const playerId = props.player?.id ?? props.player?.playerId;
  if (!playerId || rankingUp.value) return;
  rankingUp.value = true;
  try {
    const info: any = await rankingApi.getAvatarRankInfo(playerId);
    const reqs: Array<{ type: string; label: string; current: number; required: number; completed: boolean }> =
      info?.requirements || [];
    const canRankUp: boolean = info?.canRankUp ?? false;
    const nextName: string = info?.nextDisplayName || info?.nextRankName || '下一级';

    const details = reqs.map(r => ({
      label: r.label,
      current: r.current,
      required: r.required,
      completed: r.completed,
    }));

    rankingUp.value = false; // 释放按钮，弹窗期间不禁用

    if (canRankUp) {
      const confirmed = await ui.showConfirm(
        '位阶校准',
        `即将晋升至「${nextName}」，确认执行位阶校准？`,
        details,
      );
      if (!confirmed) return;
      rankingUp.value = true; // 确认后，执行升阶时显示加载状态
    } else {
      await ui.showAlert('位阶校准', '升阶条件尚未全部满足：', details);
      return;
    }

    await rankingApi.rankUp(playerId);
    emit('playerUpdated', null);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '升阶失败';
    ui.showAlert('位阶校准', msg);
  } finally {
    rankingUp.value = false;
  }
}

function clampX(x: number) {
  return Math.min(x, window.innerWidth - 270);
}

function clampY(y: number) {
  return Math.min(y, window.innerHeight - 160);
}

function computedPower(): number {
  const s = stats();
  const atk = num(s.attack);
  const def = num(s.defense);
  const spd = num(s.speed);
  const mxHp = num(s.maxHp);
  if (!atk && !def && !spd && !mxHp) return 0;
  return Math.round(atk * 2 + def * 1.5 + spd * 1.2 + mxHp * 0.3);
}

const attrs = computed(() => {
  const s = stats();
  return [
    { label: '生命', key: 'hp', color: 'red', display: `${compact(s.hp)} / ${compact(s.maxHp)}` },
    { label: '行动力', key: 'stamina', color: 'blue', display: `${compact(s.stamina)} / ${compact(s.maxStamina)}` },
    { label: '攻击', key: 'attack', color: 'blue', display: `${compact(s.attack)}` },
    { label: '防御', key: 'defense', color: 'gold', display: `${compact(s.defense)}` },
    { label: '综合战力', key: 'power', color: 'white', display: `${compact(computedPower())}` },
    { label: '反应', key: 'speed', color: 'green', display: `${compact(s.speed)}` },
    { label: '暴击率', key: 'critRate', color: 'red', display: `${(num(s.critRate) * 100).toFixed(1)}%` },
    { label: '概率修正', key: 'luck', color: 'green', display: `${compact(s.luck)}` },
    { label: '频道热度', key: 'channelHeat', color: 'red', display: `${compact(s.channelHeat)}` },
  ];
});
</script>

<template>
  <div class="ling-side-content">
    <section class="ling-side-section">
      <h3>化身信息</h3>
      <dl class="ling-kv">
        <div>
          <dt>化身</dt>
          <dd>
            <b>{{ player?.playerName || player?.player_name || player?.name || '未命名读者' }}</b>
            <em>#{{ player?.user_id || player?.userId || player?.id || '-' }}</em>
          </dd>
        </div>
        <div>
          <dt>等级</dt>
          <dd>
            <b class="gold">{{ stats().level || 1 }}</b>
            <button class="ling-info-btn" @click.stop="showTooltip('level', $event)" title="等级说明">ⓘ</button>
          </dd>
        </div>
        <div>
          <dt>位阶</dt>
          <dd>
            <b class="green">{{ player?.avatarRank || player?.storyGrade || '普通化身' }}</b>
            <button class="ling-info-btn" @click.stop="showTooltip('rank', $event)" title="位阶说明">ⓘ</button>
          </dd>
        </div>
        <div>
          <dt>背后星</dt>
          <dd>
            <b class="gold">{{ constellationDisplay() }}</b>
            <small class="ling-worldline">偏移 {{ compact(stats().worldLineShift) }}</small>
          </dd>
        </div>
        <div>
          <dt>经验</dt>
          <dd>
            <b>{{ compact(stats().exp) }}</b><span>/{{ compact(stats().maxExp) }}</span>
          </dd>
        </div>
      </dl>
      <div class="ling-progress">
        <span :style="{ width: `${pct(stats().exp, stats().maxExp)}%` }"></span>
        <b>{{ pct(stats().exp, stats().maxExp) }}%</b>
        <button class="ling-info-btn" style="margin-left:4px;" @click.stop="showTooltip('exp', $event)">ⓘ</button>
      </div>
      <button class="ling-break-btn" :disabled="rankingUp" @click="handleRankUp">{{ rankingUp ? '校准中...' : '位阶校准 ⓘ' }}</button>
      <!-- 属性分配 -->
      <div class="ling-alloc-section">
        <div class="ling-alloc-header">
          <span>自由属性点: <b class="gold">{{ stats().freePoints ?? 0 }}</b></span>
          <span class="ling-alloc-summary">已分配: 攻{{ stats().allocatedAtk ?? 0 }} 防{{ stats().allocatedDef ?? 0 }} 速{{ stats().allocatedSpd ?? 0 }} 暴{{ stats().allocatedCrit ?? 0 }}</span>
        </div>
        <button class="ling-alloc-btn" @click.stop="showAllocDialog = true">属性加点</button>
      </div>
    </section>

    <section class="ling-side-section">
      <h3>属性</h3>
      <dl class="ling-kv ling-attrs">
        <div v-for="item in attrs" :key="item.key">
          <dt>{{ item.label }}</dt>
          <dd :class="item.color">
            {{ item.display }}
            <button class="ling-info-btn" @click.stop="showTooltip(item.key, $event)">ⓘ</button>
          </dd>
        </div>
      </dl>
    </section>

    <section class="ling-side-section">
      <div class="ling-equip-header">
        <h3>星痕装备</h3>
        <div class="ling-equip-actions">
          <button>整备</button>
          <button>修 3</button>
        </div>
      </div>
      <div class="ling-equipment-list">
        <template v-if="equippedItems.length > 0">
          <div v-for="(item, idx) in equippedItems" :key="idx">
            <span>{{ item.slot }}</span>
            <b>{{ item.name }}</b>
            <small>{{ item.detail }}</small>
          </div>
        </template>
        <div v-else class="ling-equipment-empty">
          <span>暂无已装备的装备</span>
        </div>
      </div>
    </section>

    <section class="ling-side-section">
      <h3>星币</h3>
      <dl class="ling-kv">
        <div>
          <dt>持有</dt>
          <dd class="gold">{{ compact(player?.coins) }}</dd>
        </div>
        <div>
          <dt>碎片</dt>
          <dd class="gold">{{ compact(player?.storyFragments || player?.story_fragments) }}</dd>
        </div>
      </dl>
      <button class="ling-market-btn">图 鬼怪商店</button>
    </section>

    <section class="ling-side-section">
      <h3>其他</h3>
      <dl class="ling-kv">
        <div><dt>标识</dt><dd class="gold">无名读者</dd></div>
        <div><dt>星痕</dt><dd>第四面墙</dd></div>
        <div><dt>位置</dt><dd>{{ player?.currentLocationName || player?.currentLocation || player?.currentLocationKey || '未知区域' }}</dd></div>
      </dl>
    </section>

    <AllocDialog
      :visible="showAllocDialog"
      :player-id="Number(player?.id || player?.playerId || 0)"
      :stats="stats()"
      @close="showAllocDialog = false"
      @updated="(data: unknown) => { showAllocDialog = false; emit('playerUpdated', data) }"
    />
    <Teleport to="body">
      <div v-if="tooltipKey" class="ling-tooltip-backdrop" @click="hideTooltip"></div>
      <div
        v-if="tooltipKey"
        class="ling-stats-tooltip"
        :style="{ left: `${clampX(tooltipX)}px`, top: `${clampY(tooltipY)}px` }"
      >
        <div class="ling-stats-tooltip-title">
          <span>{{ attrs.find(a => a.key === tooltipKey)?.label || (tooltipKey === 'rank' ? '位阶' : tooltipKey === 'exp' ? '经验' : '') }}</span>
          <button class="ling-stats-tooltip-close" @click.stop="hideTooltip">✕</button>
        </div>
        <div class="ling-stats-tooltip-text" style="white-space: pre-line;">{{ tooltipText }}</div>
      </div>
    </Teleport>
  </div>
</template>
