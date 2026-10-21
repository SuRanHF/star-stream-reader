import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const seedRows = JSON.parse(readFileSync(
  resolve(repositoryRoot, 'springboot-backend/src/main/resources/db/seed_explore.json'),
  'utf8',
));

const location = seedRows.find((row) => row.type === 'location' && row.location_key === 'ruined_station');
assert.ok(location, '正式探索种子缺少废弃车站地点');
const events = seedRows.filter((row) => row.type === 'event' && row.location_key === 'ruined_station');
const mainStoryKey = 'station_first_trace';
const bossClueKey = 'station_boss_clue';
assert.equal(events.filter((event) => event.event_key === mainStoryKey).length, 1);
assert.equal(events.filter((event) => event.event_key === bossClueKey).length, 1);
assert.equal(events.filter((event) => event.event_type === 'story').length, 1,
  '模型依赖废弃车站当前只有一个主线故事事件；配置改变时应重新审查保底模型');
assert.ok(events.every((event) => !event.required_conditions || Object.keys(event.required_conditions).length === 0),
  '废弃车站出现事件门槛后，需要把门槛纳入模拟条件');

function eventWeight(event, storyPity) {
  const rate = location.event_rates?.[event.event_type];
  let weight = rate === undefined
    ? event.weight
    : Math.max(1, Math.round(event.weight * Number(rate) / 100));
  if (event.event_type === 'story') {
    const pityBonusPerMiss = Math.max(1, Math.floor(weight / 2));
    weight = Math.min(2_147_483_647, weight + storyPity * pityBonusPerMiss);
  }
  return weight;
}

function makeRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 4_294_967_296;
  };
}

const initialWeights = events.map((event) => ({
  eventKey: event.event_key,
  eventType: event.event_type,
  adjustedWeight: eventWeight(event, 0),
}));
const clueWeight = initialWeights.find((entry) => entry.eventKey === bossClueKey).adjustedWeight;
const initialWeightTotal = initialWeights.reduce((total, entry) => total + entry.adjustedWeight, 0);
const postStoryWeightTotal = initialWeightTotal
  - initialWeights.find((entry) => entry.eventType === 'story').adjustedWeight;

function simulateOne(random) {
  let availableEvents = [...events];
  let storyFound = false;
  let clueFound = false;
  let storyPity = 0;
  let explorationCount = 0;
  let staminaSpent = 0;

  while (!storyFound || !clueFound) {
    explorationCount++;
    const stories = availableEvents.filter((event) => event.event_type === 'story');
    const selectionEvents = storyPity >= 8 && stories.length > 0 ? stories : availableEvents;
    const weightedEvents = selectionEvents.map((event) => ({
      event,
      weight: eventWeight(event, storyPity),
    }));
    const totalWeight = weightedEvents.reduce((total, entry) => total + entry.weight, 0);
    assert.ok(totalWeight > 0, '正式探索事件池意外为空');

    let roll = random() * totalWeight;
    let selected = weightedEvents.at(-1).event;
    for (const entry of weightedEvents) {
      roll -= entry.weight;
      if (roll < 0) {
        selected = entry.event;
        break;
      }
    }

    staminaSpent += Number(selected.stamina_cost ?? 5);
    if (selected.event_type === 'story') {
      if (selected.event_key === mainStoryKey) storyFound = true;
      storyPity = 0;
    } else {
      if (selected.event_key === bossClueKey) clueFound = true;
      storyPity++;
    }

    if (Number(selected.repeatable) === 0) {
      availableEvents = availableEvents.filter((event) => event.event_key !== selected.event_key);
    }
  }

  return { explorationCount, staminaSpent };
}

const samples = Number(process.argv[2] ?? 1_000_000);
assert.ok(Number.isSafeInteger(samples) && samples > 0, '样本量必须是正整数');
const random = makeRandom(0x4d595df4);
const explorationHistogram = new Uint32Array(1000);
let totalExplorations = 0;
let totalStamina = 0;
const completedWithin = new Map([10, 20, 30].map((limit) => [limit, 0]));

for (let index = 0; index < samples; index++) {
  const result = simulateOne(random);
  explorationHistogram[Math.min(explorationHistogram.length - 1, result.explorationCount)]++;
  totalExplorations += result.explorationCount;
  totalStamina += result.staminaSpent;
  for (const limit of completedWithin.keys()) {
    if (result.explorationCount <= limit) completedWithin.set(limit, completedWithin.get(limit) + 1);
  }
}

function percentile(fraction) {
  const target = Math.ceil(samples * fraction);
  let cumulative = 0;
  for (let index = 0; index < explorationHistogram.length; index++) {
    cumulative += explorationHistogram[index];
    if (cumulative >= target) return index;
  }
  return explorationHistogram.length - 1;
}

console.log(JSON.stringify({
  locationKey: location.location_key,
  source: 'springboot-backend/src/main/resources/db/seed_explore.json',
  mainStoryKey,
  bossClueKey,
  assumptions: [
    '开始时主线/线索均未发现，且玩家已满足等级等其他晋升条件。',
    '仅使用当前废弃车站种子事件；忽略门槛、技能/称号故事权重加成、体力恢复等待和事件处理用时。',
    '按 ExploreServiceImpl 的地点类型倍率、storyPity 权重和 8 次未触发后的主线硬保底抽样。',
    '实际服务端在每次探索后保存选择并推进进度；事件重复规则按种子 repeatable 字段处理。',
  ],
  locationRates: location.event_rates,
  initialAdjustedWeights: initialWeights,
  initialBossClueChance: `${clueWeight}/${initialWeightTotal}`,
  bossClueChanceAfterMainStory: `${clueWeight}/${postStoryWeightTotal}`,
  storyPityGuaranteeAfterMisses: 8,
  samples,
  seed: '0x4d595df4',
  meanExplorations: totalExplorations / samples,
  meanStaminaSpent: totalStamina / samples,
  percentiles: {
    p50: percentile(0.5),
    p75: percentile(0.75),
    p90: percentile(0.9),
    p95: percentile(0.95),
    p99: percentile(0.99),
  },
  completionWithinExplorations: Object.fromEntries(
    [...completedWithin].map(([limit, count]) => [limit, count / samples]),
  ),
}, null, 2));
