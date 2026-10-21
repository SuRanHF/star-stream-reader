import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const seedPath = resolve(repoRoot, 'springboot-backend/src/main/resources/db/seed_explore.json');
const monsterSeedPath = resolve(repoRoot, 'springboot-backend/src/main/resources/db/seed_monsters.sql');
const seedEntries = JSON.parse(readFileSync(seedPath, 'utf8'));
const locationsByKey = new Map(seedEntries
  .filter((entry) => entry.type === 'location')
  .map((entry) => [entry.location_key, entry]));

function parseSqlLiteral(token) {
  const value = token.trim();
  if (value.toUpperCase() === 'NULL') return null;
  if (value.startsWith("'") && value.endsWith("'")) {
    return value.slice(1, -1).replace(/''/g, "'").replace(/\\'/g, "'");
  }
  if (/^-?\d+$/.test(value)) return Number(value);
  return value;
}

function splitSqlTuple(tuple) {
  const fields = [];
  let start = 0;
  let inString = false;
  for (let index = 0; index < tuple.length; index += 1) {
    const char = tuple[index];
    if (inString && char === '\\') {
      index += 1;
      continue;
    }
    if (char === "'") {
      if (inString && tuple[index + 1] === "'") {
        index += 1;
        continue;
      }
      inString = !inString;
    } else if (char === ',' && !inString) {
      fields.push(parseSqlLiteral(tuple.slice(start, index)));
      start = index + 1;
    }
  }
  fields.push(parseSqlLiteral(tuple.slice(start)));
  return fields;
}

function stripSqlComments(sqlText) {
  const output = [...sqlText];
  let inString = false;
  let lineComment = false;
  let blockComment = false;

  for (let index = 0; index < sqlText.length; index += 1) {
    const char = sqlText[index];
    const next = sqlText[index + 1];

    if (lineComment) {
      if (char === '\n' || char === '\r') lineComment = false;
      else output[index] = ' ';
      continue;
    }
    if (blockComment) {
      if (char === '*' && next === '/') {
        output[index] = ' ';
        output[index + 1] = ' ';
        index += 1;
        blockComment = false;
      } else if (char !== '\n' && char !== '\r') {
        output[index] = ' ';
      }
      continue;
    }
    if (inString) {
      if (char === '\\') {
        index += 1;
        continue;
      }
      if (char === "'" && next === "'") {
        index += 1;
        continue;
      }
      if (char === "'") inString = false;
      continue;
    }
    if (char === "'") {
      inString = true;
    } else if (char === '-' && next === '-') {
      output[index] = ' ';
      output[index + 1] = ' ';
      index += 1;
      lineComment = true;
    } else if (char === '#') {
      output[index] = ' ';
      lineComment = true;
    } else if (char === '/' && next === '*') {
      output[index] = ' ';
      output[index + 1] = ' ';
      index += 1;
      blockComment = true;
    }
  }

  return output.join('');
}

function parseMonsterSeed(sqlText) {
  const uncommentedSql = stripSqlComments(sqlText);
  const valuesMatch = /\bVALUES\b/i.exec(uncommentedSql);
  const valuesStart = valuesMatch?.index ?? -1;
  if (valuesStart < 0) throw new Error('seed_monsters.sql has no VALUES clause');
  const tuples = [];
  let tupleStart = -1;
  let depth = 0;
  let inString = false;
  for (let index = valuesStart + valuesMatch[0].length; index < uncommentedSql.length; index += 1) {
    const char = uncommentedSql[index];
    if (inString && char === '\\') {
      index += 1;
      continue;
    }
    if (char === "'") {
      if (inString && uncommentedSql[index + 1] === "'") {
        index += 1;
        continue;
      }
      inString = !inString;
    } else if (!inString && char === '(') {
      if (depth === 0) tupleStart = index + 1;
      depth += 1;
    } else if (!inString && char === ')') {
      depth -= 1;
      if (depth === 0 && tupleStart >= 0) {
        tuples.push(splitSqlTuple(uncommentedSql.slice(tupleStart, index)));
        tupleStart = -1;
      }
    } else if (!inString && char === ';' && depth === 0) {
      break;
    }
  }

  return tuples.map((fields) => {
    if (fields.length < 12) throw new Error(`Unexpected monster seed row with ${fields.length} fields`);
    return {
      monsterKey: fields[0],
      name: fields[1],
      type: fields[2],
      rarity: fields[3],
      locationKey: fields[4],
      stats: JSON.parse(fields[6]),
      rewards: JSON.parse(fields[8]),
      drops: JSON.parse(fields[9]),
      enabled: fields[11],
    };
  });
}

const monsterSeedSql = readFileSync(monsterSeedPath, 'utf8');
const monsters = parseMonsterSeed(monsterSeedSql);
const monsterByKey = new Map(monsters.map((monster) => [monster.monsterKey, monster]));

const locationKey = process.argv[2] || 'ruined_station';
const iterations = Number.parseInt(process.argv[3] || '50000', 10);
const startingStamina = 50;
const maxExplorations = 10;
// 50 base + optional steady-breath (+5), first rank promotion (+10), and
// active faction skill (+10) gives a modeled stacked ceiling of 75.
const staminaCapacityScenarios = [50, 55, 60, 65, 70, 75];
const location = seedEntries.find((entry) =>
  entry.type === 'location' && entry.location_key === locationKey,
);
const events = seedEntries.filter((entry) =>
  entry.type === 'event' && entry.location_key === locationKey,
);

if (!location || events.length === 0) {
  throw new Error(`No seeded location/events found for location_key=${locationKey}`);
}
if (!Number.isInteger(iterations) || iterations < 1) {
  throw new Error('iterations must be a positive integer');
}

const conditionedEvents = events.filter((event) =>
  Object.keys(event.required_conditions ?? {}).length > 0,
);
const baselineEvents = events.filter((event) =>
  Object.keys(event.required_conditions ?? {}).length === 0,
);

const getExpectedMonsterDrops = (monster) => {
  const expected = {};
  for (const item of monster?.drops?.items ?? []) {
    const itemKey = `item:${item.itemKey}`;
    expected[itemKey] = (expected[itemKey] ?? 0)
      + Number(item.quantity ?? 1) * Number(item.dropRate ?? 1);
  }
  for (const equipment of monster?.drops?.equipment ?? []) {
    const equipmentKey = `equipment:${equipment.equipmentKey}`;
    expected[equipmentKey] = (expected[equipmentKey] ?? 0) + Number(equipment.dropRate ?? 1);
  }
  return expected;
};

// NPC sell prices are retained only for the already-audited station-rat cash
// sensitivity. Other monster loot is reported as expected quantity, not guessed cash.
const stationRat = monsterByKey.get('station_rat');
if (!stationRat) throw new Error('station_rat is missing from seed_monsters.sql');
const stationRatRewards = {
  monsterKey: 'station_rat',
  coins: Number(stationRat.rewards.coins ?? 0),
  baseExperience: Number(stationRat.rewards.exp ?? 0),
  storyFragments: Number(stationRat.rewards.storyFragments ?? 0),
  dropsExpected: Object.fromEntries(Object.entries(getExpectedMonsterDrops(stationRat))
    .map(([key, value]) => [key.replace(/^item:/, ''), value])),
  npcSellPrices: { stamina_bread: 5, monster_bone: 8 },
};
const battleEvents = baselineEvents.filter((event) => event.event_type === 'battle_placeholder');
const missingMonsterKeys = [...new Set(battleEvents
  .map((event) => event.risks_json?.monsterKey)
  .filter((monsterKey) => monsterKey && !monsterByKey.has(monsterKey)))];
const combatRewardDataComplete = battleEvents.every((event) =>
  Boolean(event.risks_json?.monsterKey && monsterByKey.has(event.risks_json.monsterKey)));
const stationRatCombatSupported = locationKey === 'ruined_station'
  && battleEvents.length > 0
  && battleEvents.every((event) => event.risks_json?.monsterKey === stationRatRewards.monsterKey);
const combatSensitivitySupported = battleEvents.length > 0 && combatRewardDataComplete;
const perMonsterCombatEnabled = process.argv.includes('--per-monster-combat');
const combatRunsArgument = process.argv.find((argument) => argument.startsWith('--combat-runs='));
const combatRuns = combatRunsArgument
  ? Number.parseInt(combatRunsArgument.slice('--combat-runs='.length), 10)
  : 10000;
if (perMonsterCombatEnabled && (!Number.isInteger(combatRuns) || combatRuns < 100 || combatRuns > 100000)) {
  throw new Error('--combat-runs must be an integer between 100 and 100000');
}

function loadPerMonsterCombatWinRates() {
  const simulatorPath = resolve(repoRoot, 'scripts/simulate-rank-transition-combat.mjs');
  const simulation = spawnSync(process.execPath, [simulatorPath, '--all-exploration-encounters',
    `--runs=${combatRuns}`], { encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 });
  if (simulation.error) throw simulation.error;
  if (simulation.status !== 0) {
    throw new Error(`Combat simulation failed: ${simulation.stderr || simulation.stdout}`);
  }

  const report = JSON.parse(simulation.stdout);
  if (!String(report.model).includes('全探索战斗遭遇')) {
    throw new Error('Combat simulator returned an unexpected model');
  }
  const ratesByEventKey = new Map();
  for (const result of report.results ?? []) {
    if (result.equipmentKeys?.length !== 0
      || result.rank !== result.encounterLocationRecommendedRank) continue;
    const winRate = Number(result.winRatePercent) / 100;
    const outcome = {
      winRate,
      experienceOnWin: Number(result.experienceAwardOnWin),
    };
    for (const eventKey of result.eventKeys ?? []) {
      const previousOutcome = ratesByEventKey.get(eventKey);
      if (previousOutcome !== undefined
        && (previousOutcome.winRate !== outcome.winRate
          || previousOutcome.experienceOnWin !== outcome.experienceOnWin)) {
        throw new Error(`Conflicting combat win rates for event ${eventKey}`);
      }
      ratesByEventKey.set(eventKey, outcome);
    }
  }

  const missingEvents = battleEvents
    .filter((event) => !ratesByEventKey.has(event.event_key))
    .map((event) => event.event_key);
  if (missingEvents.length > 0) {
    throw new Error(`Combat simulation has no baseline rate for events: ${missingEvents.join(', ')}`);
  }
  return { report, ratesByEventKey };
}

const perMonsterCombat = perMonsterCombatEnabled ? loadPerMonsterCombatWinRates() : null;

function createRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state = (Math.imul(1664525, state) + 1013904223) >>> 0;
    return state / 0x1_0000_0000;
  };
}

function effectiveWeight(event, storyPity) {
  const rate = location.event_rates?.[event.event_type] ?? 100;
  let weight = Math.max(1, Math.round(Number(event.weight) * Number(rate) / 100));
  if (event.event_type === 'story') {
    // Mirrors ExploreServiceImpl: at least one weight point per missed main story.
    weight += Math.max(0, storyPity) * Math.max(1, Math.floor(weight / 2));
  }
  return weight;
}

function selectEvent(pool, storyPity, random) {
  if (storyPity >= 8) {
    const guaranteedStory = pool.find((event) => event.event_type === 'story');
    if (guaranteedStory) return guaranteedStory;
  }

  const weightedPool = pool.map((event) => ({
    event,
    weight: effectiveWeight(event, storyPity),
  }));
  const totalWeight = weightedPool.reduce((sum, entry) => sum + entry.weight, 0);
  let roll = random() * totalWeight;
  for (const entry of weightedPool) {
    roll -= entry.weight;
    if (roll < 0) return entry.event;
  }
  return weightedPool.at(-1).event;
}

function simulate(
  combatWinRateAssumption = 0,
  repeatableOnly = false,
  useStaminaBread = false,
  totalStaminaBudget = startingStamina,
  simulationRuns = iterations,
  combatOutcomeByEventKey = null,
  maxStaminaCapacity = 50,
) {
  // Separate streams keep the sampled exploration events identical across the
  // 0%, 50%, and 100% encounter-outcome sensitivity rows.
  const eventRandom = createRandom(20260925);
  const combatRandom = createRandom(20260926);
  const itemRandom = createRandom(20260927);
  const staminaCapacity = Math.max(1, maxStaminaCapacity);
  const totals = {
    explorations: 0,
    staminaSpent: 0,
    coins: 0,
    baseExperience: 0,
    storyFragments: 0,
    staminaBreadExpected: 0,
    staminaBreadUsed: 0,
    battleEncounters: 0,
    battleWins: 0,
    battleFailures: 0,
    battleFailureCoinPenaltyAtBalance10OrMore: 0,
    battleCoins: 0,
    battleBaseExperience: 0,
    battleStoryFragments: 0,
    battleItemDropsExpected: {},
    battleStaminaBreadExpected: 0,
    battleMonsterBoneExpected: 0,
    reachedTenExplorations: 0,
  };
  const steadyBreathEligibilityActions = [];
  const steadyBreathEligibilityWithQuestActions = [];

  for (let run = 0; run < simulationRuns; run += 1) {
    const triggeredStories = new Set();
    const triggeredSideEvents = new Set();
    let storyPity = 0;
    let stamina = Math.min(startingStamina, staminaCapacity, totalStaminaBudget);
    let remainingRecoveryBudget = Math.max(0, totalStaminaBudget - stamina);
    let staminaBreadInventory = 0;
    let explorationCount = 0;
    let runStaminaSpent = 0;
    let runCoins = 0;
    let runBaseExperience = 0;
    let steadyBreathEligibleAt = null;
    let runCoinsWithQuest = 0;
    let runBaseExperienceWithQuest = 0;
    let steadyBreathEligibleWithQuestAt = null;

    const explorationLimit = repeatableOnly
      ? (useStaminaBread ? Math.max(1000, totalStaminaBudget * 20) : totalStaminaBudget + 1)
      : maxExplorations;
    while (explorationCount < explorationLimit) {
      const pool = baselineEvents.filter((event) => {
        if (repeatableOnly && event.repeatable === 0) return false;
        if (event.repeatable !== 0) return true;
        return event.event_type === 'story'
          ? !triggeredStories.has(event.event_key)
          : !triggeredSideEvents.has(event.event_key);
      });
      if (pool.length === 0) break;

      const event = selectEvent(pool, storyPity, eventRandom);
      const cost = Number(event.stamina_cost ?? 5);
      while (useStaminaBread && stamina < cost && staminaBreadInventory > 0) {
        staminaBreadInventory -= 1;
        stamina = Math.min(staminaCapacity, stamina + 30);
        totals.staminaBreadUsed += 1;
      }
      while (stamina < cost && remainingRecoveryBudget > 0) {
        const refill = Math.min(staminaCapacity - stamina, remainingRecoveryBudget);
        if (refill <= 0) break;
        stamina += refill;
        remainingRecoveryBudget -= refill;
      }
      if (stamina < cost) break;

      stamina -= cost;
      runStaminaSpent += cost;
      explorationCount += 1;

      // Settlement rewards belong to the seeded monster, not the placeholder event.
      // A battle outcome never changes the already-paid explore cost.
      if (event.event_type === 'battle_placeholder') {
        totals.battleEncounters += 1;
        const monster = monsterByKey.get(event.risks_json?.monsterKey);
        const combatOutcome = combatOutcomeByEventKey?.get(event.event_key);
        const combatWinRate = combatOutcome?.winRate ?? combatWinRateAssumption;
        const battleWon = monster && combatRandom() < combatWinRate;
        if (!battleWon && monster) {
          totals.battleFailures += 1;
          // Track the service's conditional failure penalty without assuming the
          // simulated player actually has ten coins available to lose.
          totals.battleFailureCoinPenaltyAtBalance10OrMore += 10;
        }
        if (battleWon) {
          const reward = monster.rewards ?? {};
          const experienceOnWin = combatOutcome?.experienceOnWin ?? Number(reward.exp ?? 0);
          totals.battleWins += 1;
          totals.battleCoins += Number(reward.coins ?? 0);
          totals.battleBaseExperience += experienceOnWin;
          totals.battleStoryFragments += Number(reward.storyFragments ?? 0);
          const itemDrops = monster.drops?.items ?? [];
          for (const item of itemDrops) {
            const quantity = Number(item.quantity ?? 1);
            const dropRate = Number(item.dropRate ?? 1);
            const itemKey = `item:${item.itemKey}`;
            totals.battleItemDropsExpected[itemKey] = (totals.battleItemDropsExpected[itemKey] ?? 0)
              + quantity * dropRate;
            if (item.itemKey === 'stamina_bread') {
              totals.battleStaminaBreadExpected += quantity * dropRate;
            }
            if (item.itemKey === 'monster_bone') {
              totals.battleMonsterBoneExpected += quantity * dropRate;
            }
            if (useStaminaBread && item.itemKey === 'stamina_bread'
              && itemRandom() < dropRate) {
              staminaBreadInventory += quantity;
            }
          }
          for (const equipment of monster.drops?.equipment ?? []) {
            const equipmentKey = `equipment:${equipment.equipmentKey}`;
            totals.battleItemDropsExpected[equipmentKey] = (totals.battleItemDropsExpected[equipmentKey] ?? 0)
              + Number(equipment.dropRate ?? 1);
          }
          runCoins += Number(reward.coins ?? 0);
          runBaseExperience += experienceOnWin;
          runCoinsWithQuest += Number(reward.coins ?? 0);
          runBaseExperienceWithQuest += experienceOnWin;
        }
      } else {
        const eventCoins = Number(event.rewards?.coins ?? 0);
        const eventExperience = Number(event.rewards?.exp ?? 0);
        totals.coins += eventCoins;
        totals.baseExperience += eventExperience;
        totals.storyFragments += Number(event.rewards?.storyFragments ?? 0);
        runCoins += eventCoins;
        runBaseExperience += eventExperience;
        runCoinsWithQuest += eventCoins;
        runBaseExperienceWithQuest += eventExperience;
        for (const item of event.rewards?.items ?? []) {
          if (item.itemKey === 'stamina_bread') {
            const quantity = Number(item.quantity ?? 1);
            const dropRate = Number(item.dropRate ?? 1);
            totals.staminaBreadExpected += quantity * dropRate;
            if (useStaminaBread && itemRandom() < dropRate) {
              staminaBreadInventory += quantity;
            }
          }
        }
      }

      if (event.event_type === 'story') {
        storyPity = 0;
        if (event.repeatable === 0) triggeredStories.add(event.event_key);
      } else {
        storyPity += 1;
        if (event.repeatable === 0) triggeredSideEvents.add(event.event_key);
      }

      // steady_breath is the first universal exploration skill: level 2 and 50 coins.
      if (steadyBreathEligibleAt === null && runCoins >= 50 && runBaseExperience >= 100) {
        steadyBreathEligibleAt = explorationCount;
      }
      if (explorationCount === 3) {
        // Best-case accessibility check: player claims daily_explore_3 immediately.
        runCoinsWithQuest += 50;
        runBaseExperienceWithQuest += 20;
      }
      if (steadyBreathEligibleWithQuestAt === null
        && runCoinsWithQuest >= 50 && runBaseExperienceWithQuest >= 100) {
        steadyBreathEligibleWithQuestAt = explorationCount;
      }
    }

    totals.explorations += explorationCount;
    totals.staminaSpent += runStaminaSpent;
    if (explorationCount >= maxExplorations) totals.reachedTenExplorations += 1;
    if (steadyBreathEligibleAt !== null) steadyBreathEligibilityActions.push(steadyBreathEligibleAt);
    if (steadyBreathEligibleWithQuestAt !== null) {
      steadyBreathEligibilityWithQuestActions.push(steadyBreathEligibleWithQuestAt);
    }
  }

  const mean = (value) => Number((value / simulationRuns).toFixed(3));
  const summarizeReadiness = (actions) => {
    const sorted = [...actions].sort((a, b) => a - b);
    const percentile = (fraction) => sorted.length === 0
    ? null
      : sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
    return {
      eligibleWithinTenExplorationsRate: mean(sorted.length),
      meanExplorationsAmongEligibleRuns: sorted.length === 0
        ? null
        : Number((sorted.reduce((sum, value) => sum + value, 0) / sorted.length).toFixed(3)),
      eligibleRunActionCountPercentiles: {
        p10: percentile(0.1),
        p50: percentile(0.5),
        p90: percentile(0.9),
      },
    };
  };
  return {
    means: {
      explorations: mean(totals.explorations),
      staminaSpent: mean(totals.staminaSpent),
      coins: mean(totals.coins + totals.battleCoins),
      baseExperience: mean(totals.baseExperience + totals.battleBaseExperience),
      storyFragments: mean(totals.storyFragments + totals.battleStoryFragments),
      staminaBreadExpected: mean(totals.staminaBreadExpected + totals.battleStaminaBreadExpected),
      staminaBreadUsed: mean(totals.staminaBreadUsed),
      reachedTenExplorationsRate: mean(totals.reachedTenExplorations),
      battleEncounters: mean(totals.battleEncounters),
      battleWins: mean(totals.battleWins),
      battleFailures: mean(totals.battleFailures),
      battleFailureCoinPenaltyAtBalance10OrMore:
        mean(totals.battleFailureCoinPenaltyAtBalance10OrMore),
      netCoinsIfBalanceAlwaysAtLeast10: mean(totals.coins + totals.battleCoins
        - totals.battleFailureCoinPenaltyAtBalance10OrMore),
      battleCoins: mean(totals.battleCoins),
      battleBaseExperience: mean(totals.battleBaseExperience),
      battleStoryFragments: mean(totals.battleStoryFragments),
      battleStaminaBreadExpected: mean(totals.battleStaminaBreadExpected),
      battleMonsterBoneExpected: mean(totals.battleMonsterBoneExpected),
      battleItemDropsExpected: Object.fromEntries(Object.entries(totals.battleItemDropsExpected)
        .map(([dropKey, quantity]) => [dropKey, mean(quantity)])),
    },
    steadyBreathReadiness: {
      skill: 'steady_breath',
      requirements: { level: 2, totalExperience: 100, coins: 50 },
      withoutClaimingDailyQuest: summarizeReadiness(steadyBreathEligibilityActions),
      assumingDailyExploreQuestClaimedAfterAction3: summarizeReadiness(steadyBreathEligibilityWithQuestActions),
    },
  };
}

const passiveRecoverySnapshots = [1, 3, 8, 24].map((hours) => {
  const elapsedSeconds = hours * 3600;
  const recovery = (resting) => {
    const multiplier = resting ? 1 : 3;
    const hpInterval = Math.max(1, Math.floor(7200 / 100)) * multiplier;
    const staminaInterval = Math.max(1, Math.floor(10800 / 50)) * multiplier;
    const experienceInterval = 30 * multiplier;
    return {
      experienceFromPassiveRecovery: Math.floor(elapsedSeconds / experienceInterval),
      hpRecoveredFromEmpty: Math.min(100, Math.floor(elapsedSeconds / hpInterval)),
      staminaRecoveredFromEmpty: Math.min(50, Math.floor(elapsedSeconds / staminaInterval)),
    };
  };
  return { hours, resting: recovery(true), normal: recovery(false) };
});

const staminaCapacityRecoverySensitivity = staminaCapacityScenarios.map((maxStaminaCapacity) => {
  const restingSecondsPerStamina = Math.max(1, Math.floor(10800 / maxStaminaCapacity));
  const normalSecondsPerStamina = restingSecondsPerStamina * 3;
  return {
    maxStaminaCapacity,
    secondsPerStamina: { resting: restingSecondsPerStamina, normal: normalSecondsPerStamina },
    recoveredFromEmptyPerDay: {
      resting: Math.min(maxStaminaCapacity, Math.floor(86400 / restingSecondsPerStamina)),
      normal: Math.min(maxStaminaCapacity, Math.floor(86400 / normalSecondsPerStamina)),
    },
    recoveredFromEmptyPerWeekAtContinuousUse: {
      resting: Math.floor(7 * 86400 / restingSecondsPerStamina),
      normal: Math.floor(7 * 86400 / normalSecondsPerStamina),
    },
  };
});

// Mirrors PkServiceImpl rewards and the weekly_pk_5 / weekly_pk_win_3
// definitions in init.sql. The rank multipliers mirror ExperienceProgression.
const weeklyPkScenario = {
  acceptedChallenges: 5,
  winCoinsPerChallenge: 50,
  winBaseExperiencePerChallenge: 30,
  participationQuest: { winsNeeded: 0, coins: 200, baseExperience: 80, storyFragments: 3 },
  threeWinsQuest: { winsNeeded: 3, coins: 120, baseExperience: 50, storyFragments: 2 },
  rankExperienceMultipliers: { F: 1, E: 1.5, D: 2.5, C: 4, B: 7, A: 12, S: 20, SS: 35, SSS: 60 },
};
const binomialCoefficient = (n, k) => {
  let result = 1;
  for (let index = 1; index <= k; index += 1) {
    result = result * (n - index + 1) / index;
  }
  return result;
};
const weeklyPkWinRateSensitivity = [0, 0.25, 0.5, 0.75, 1].map((winRate) => {
  let threeWinQuestProbability = 0;
  for (let wins = weeklyPkScenario.threeWinsQuest.winsNeeded;
    wins <= weeklyPkScenario.acceptedChallenges; wins += 1) {
    threeWinQuestProbability += binomialCoefficient(weeklyPkScenario.acceptedChallenges, wins)
      * winRate ** wins
      * (1 - winRate) ** (weeklyPkScenario.acceptedChallenges - wins);
  }
  const expectedWins = weeklyPkScenario.acceptedChallenges * winRate;
  const expectedCoins = expectedWins * weeklyPkScenario.winCoinsPerChallenge
    + weeklyPkScenario.participationQuest.coins
    + threeWinQuestProbability * weeklyPkScenario.threeWinsQuest.coins;
  const expectedExperienceByRank = Object.fromEntries(
    Object.entries(weeklyPkScenario.rankExperienceMultipliers).map(([rank, multiplier]) => {
      const perWin = Math.round(weeklyPkScenario.winBaseExperiencePerChallenge * multiplier);
      const participation = Math.round(weeklyPkScenario.participationQuest.baseExperience * multiplier);
      const threeWins = Math.round(weeklyPkScenario.threeWinsQuest.baseExperience * multiplier);
      return [rank, Number((expectedWins * perWin + participation
        + threeWinQuestProbability * threeWins).toFixed(3))];
    }),
  );
  return {
    winRate,
    expectedWins,
    threeWinQuestProbability,
    expectedCoins: Number(expectedCoins.toFixed(3)),
    expectedStoryFragments: Number((weeklyPkScenario.participationQuest.storyFragments
      + threeWinQuestProbability * weeklyPkScenario.threeWinsQuest.storyFragments).toFixed(3)),
    expectedExperienceByRank,
  };
});

const activeExperienceByRankSensitivity = combatSensitivitySupported
  ? [0, 0.5, 1].map((assumedWinRate) => {
    const baseExperience = simulate(assumedWinRate).means.baseExperience;
    return {
      assumedWinRate,
      baseExperienceAtF: baseExperience,
      estimatedExperienceAtSameSampleByRank: Object.fromEntries(
        Object.entries(weeklyPkScenario.rankExperienceMultipliers).map(([rank, multiplier]) => [
          rank,
          Number((baseExperience * multiplier).toFixed(3)),
        ]),
      ),
    };
  })
  : null;

const grossLiquidationSensitivity = stationRatCombatSupported
  ? [0, 0.5, 1].map((assumedWinRate) => {
    const { means } = simulate(assumedWinRate);
    const breadSaleCoins = means.staminaBreadExpected * stationRatRewards.npcSellPrices.stamina_bread;
    const boneSaleCoins = means.battleMonsterBoneExpected * stationRatRewards.npcSellPrices.monster_bone;
    const liquidationCoins = means.coins + breadSaleCoins + boneSaleCoins;
    return {
      assumedWinRate,
      meanStaminaSpent: means.staminaSpent,
      meanDirectCoins: means.coins,
      meanBreadExpected: means.staminaBreadExpected,
      meanBoneExpected: means.battleMonsterBoneExpected,
      estimatedBreadSaleCoins: Number(breadSaleCoins.toFixed(3)),
      estimatedBoneSaleCoins: Number(boneSaleCoins.toFixed(3)),
      estimatedCoinsAfterSellingAllModeledDrops: Number(liquidationCoins.toFixed(3)),
      grossCoinsPerStaminaAfterSellingModeledDrops: Number((liquidationCoins / means.staminaSpent).toFixed(4)),
    };
  })
  : null;

const weeklyRepeatableScenario = process.argv.includes('--weekly') && stationRatCombatSupported
  ? (() => {
    const weeklyRuns = Math.min(iterations, 5000);
    const recoveryScenarios = [
      { key: 'daily_login', externalRecoveryStamina: 350, passiveExperiencePerHour: 120 },
      { key: 'normal_high_activity', externalRecoveryStamina: (86400 / 648) * 7, passiveExperiencePerHour: 40 },
      {
        key: 'rest_high_activity_theoretical_ceiling',
        externalRecoveryStamina: (86400 / 216) * 7,
        passiveExperiencePerHour: 120,
      },
    ];
    return {
      assumedCombatWinRate: 0.5,
      iterations: weeklyRuns,
      includesInitialFullBar: true,
      scenarios: recoveryScenarios.map((scenario) => {
        const totalStaminaBudget = startingStamina + scenario.externalRecoveryStamina;
        const means = simulate(0.5, true, true, totalStaminaBudget, weeklyRuns).means;
        const passiveRecoveryBaseExperience = scenario.passiveExperiencePerHour * 24 * 7;
        const activeExperienceByRank = Object.fromEntries(
          Object.entries(weeklyPkScenario.rankExperienceMultipliers).map(([rank, multiplier]) => [
            rank,
            Number((means.baseExperience * multiplier).toFixed(3)),
          ]),
        );
        const totalExperienceByRank = Object.fromEntries(
          Object.entries(activeExperienceByRank).map(([rank, activeExperience]) => [
            rank,
            Number((activeExperience + passiveRecoveryBaseExperience).toFixed(3)),
          ]),
        );
        return {
          ...scenario,
          totalStaminaBudget: Number(totalStaminaBudget.toFixed(3)),
          assumesNoStaminaWasteFromDelayedCheckIns: true,
          ...means,
          passiveRecoveryBaseExperience,
          activeExperienceByRank,
          totalExperienceByRank,
        };
      }),
      note: 'This is a weekly total-budget scenario for ruined_station after all repeatable=0 events are cleared. It treats recovery as usable when the next action needs it, carries bread drops through the week, and assumes the named recovery mode applies for all 168 hours. Passive experience uses the configured 40/120 per hour at 1.0x and is not rank-scaled. It ignores action/wait time, exploration choices, HP/death, tasks, other locations, and player-stat-dependent combat. The rest-high-activity row is an operational ceiling, not an expected player week; active experience by rank is an approximate multiplier before per-reward rounding.',
    };
  })()
  : null;

const weeklyStaminaCapacitySensitivity = process.argv.includes('--weekly') && stationRatCombatSupported
  ? (() => {
    const weeklyRuns = Math.min(iterations, 1000);
    const weeklySeconds = 7 * 24 * 3600;
    return staminaCapacityScenarios.map((maxStaminaCapacity) => {
      const restingSecondsPerStamina = Math.max(1, Math.floor(10800 / maxStaminaCapacity));
      const normalSecondsPerStamina = restingSecondsPerStamina * 3;
      const recoveryScenarios = [
        {
          key: 'daily_login',
          // One check-in per day, spending the prior bar before returning.
          // Every modeled capacity refills within 24h at the current formula.
          externalRecoveryStamina: 7 * Math.min(maxStaminaCapacity,
            Math.floor(86400 / normalSecondsPerStamina)),
          passiveExperiencePerHour: 120,
        },
        {
          key: 'normal_high_activity',
          externalRecoveryStamina: weeklySeconds / normalSecondsPerStamina,
          passiveExperiencePerHour: 40,
        },
        {
          key: 'rest_high_activity_theoretical_ceiling',
          externalRecoveryStamina: weeklySeconds / restingSecondsPerStamina,
          passiveExperiencePerHour: 120,
        },
      ];
      return {
        maxStaminaCapacity,
        secondsPerStamina: { resting: restingSecondsPerStamina, normal: normalSecondsPerStamina },
        startingStamina: Math.min(startingStamina, maxStaminaCapacity),
        capacityBonusGrantsNoImmediateStamina: true,
        iterations: weeklyRuns,
        scenarios: recoveryScenarios.map((scenario) => {
          const initialStamina = Math.min(startingStamina, maxStaminaCapacity);
          const totalStaminaBudget = initialStamina + scenario.externalRecoveryStamina;
          const means = simulate(0.5, true, true, totalStaminaBudget, weeklyRuns,
            null, maxStaminaCapacity).means;
          return {
            ...scenario,
            totalStaminaBudget: Number(totalStaminaBudget.toFixed(3)),
            assumesNoStaminaWasteFromDelayedCheckIns: true,
            ...means,
          };
        }),
      };
    });
  })()
  : null;

const weeklyStaminaCapacitySemanticsSensitivity = process.argv.includes('--weekly') && stationRatCombatSupported
  ? (() => {
    const weeklyRuns = Math.min(iterations, 1000);
    const weeklySeconds = 7 * 24 * 3600;
    const baseCapacity = 50;
    return [baseCapacity, 75].map((maxStaminaCapacity) => {
      const modes = [
        {
          key: 'current_capacity_scales_recovery',
          recoveryCapacity: maxStaminaCapacity,
        },
        {
          key: 'capacity_only_fixed_base_recovery',
          recoveryCapacity: baseCapacity,
        },
      ];
      return {
        maxStaminaCapacity,
        startingStamina: Math.min(startingStamina, maxStaminaCapacity),
        capacityBonusGrantsNoImmediateStamina: true,
        iterations: weeklyRuns,
        modes: modes.map((mode) => {
          const restingSecondsPerStamina = Math.max(1,
            Math.floor(10800 / mode.recoveryCapacity));
          const normalSecondsPerStamina = restingSecondsPerStamina * 3;
          const recoveryScenarios = [
            {
              key: 'daily_login',
              externalRecoveryStamina: 7 * Math.min(maxStaminaCapacity,
                Math.floor(86400 / normalSecondsPerStamina)),
            },
            {
              key: 'normal_high_activity',
              externalRecoveryStamina: weeklySeconds / normalSecondsPerStamina,
            },
            {
              key: 'rest_high_activity_theoretical_ceiling',
              externalRecoveryStamina: weeklySeconds / restingSecondsPerStamina,
            },
          ];
          return {
            key: mode.key,
            recoveryRateUsesCapacity: mode.recoveryCapacity,
            secondsPerStamina: {
              resting: restingSecondsPerStamina,
              normal: normalSecondsPerStamina,
            },
            scenarios: recoveryScenarios.map((scenario) => {
              const totalStaminaBudget = Math.min(startingStamina, maxStaminaCapacity)
                + scenario.externalRecoveryStamina;
              const means = simulate(0.5, true, true, totalStaminaBudget, weeklyRuns,
                null, maxStaminaCapacity).means;
              return {
                ...scenario,
                totalStaminaBudget: Number(totalStaminaBudget.toFixed(3)),
                assumesNoStaminaWasteFromDelayedCheckIns: true,
                ...means,
              };
            }),
          };
        }),
      };
    });
  })()
  : null;

console.log(JSON.stringify({
  explorationSource: 'springboot-backend/src/main/resources/db/seed_explore.json',
  combatRewardSource: 'springboot-backend/src/main/resources/db/seed_monsters.sql',
  questRewardSource: 'springboot-backend/src/main/resources/db/init.sql',
  locationKey,
  iterations,
  excludedConditionedEvents: conditionedEvents.map((event) => ({
    eventKey: event.event_key,
    eventType: event.event_type,
    conditionKeys: Object.keys(event.required_conditions ?? {}).sort(),
  })),
  assumptions: [
    `Start with ${startingStamina} stamina and stop after ${maxExplorations} explorations or when stamina is insufficient.`,
    'No passive recovery, skill cost reduction, item use, story-choice override, or live-database values in the exploration sample.',
    'One-time event filtering and story pity are modeled. Events with required_conditions are excluded; this baseline does not simulate story-choice unlocks or other player-specific gates.',
    'battle_placeholder rewards_json is ignored. Locations with complete monster mappings show 0% / 50% / 100% uniform win-rate sensitivities; ruined_station uses station_rat rewards. These are comparison assumptions, not measured combat win rates.',
    'The combat sensitivity does not model combat rounds, HP/death, build/skill effects, player stat growth, encounter time, or using drops. Failure coin penalties are reported separately under the conditional assumption that the player has at least 10 coins; they are not deducted from the simulated balance. Station-rat item drops are expected quantities, not guaranteed inventory. The liquidation estimate uses seed_items.sql sell prices (stamina_bread=5, monster_bone=8), assumes every modeled bread/bone is sold to the NPC, and is gross coin creation rather than net profit; it excludes any other item types.',
    ...(perMonsterCombat ? [
      `--per-monster-combat runs the checked-in combat simulator (${combatRuns} duels per encounter) and uses the no-equipment player profile at each explored location's recommended rank; victory XP uses that rank's configured multiplier. This is still a formula model, not observed player performance.`,
      'The per-monster economy sample applies each event-specific win rate to the weighted exploration event pool. It reports expected rewards and conditional failure-penalty sensitivity; it does not deduct the penalty because starting coin balances are unknown.',
    ] : []),
    'The high-rank exploration XP table scales the same F-rank sampled base rewards by ExperienceProgression multipliers. It is a reward-only comparison: it does not change combat win chance, route access, per-reward integer rounding, or model rank promotion.',
    'repeatableOnlySteadyStateSensitivity excludes every event configured repeatable=0 before simulating a 50-stamina pool. It represents an already-cleared one-time-event pool, not a player journey or weekly cap. Story choices, player-specific required conditions, and action time are not simulated.',
    'repeatableOnlyWithBreadSensitivity additionally uses a stamina bread only when the next selected repeatable event cannot be afforded; each bread restores up to 30 stamina, capped at max stamina 50. It models exploration-event bread probabilities and the seeded station-rat bread drop chance (0.2 per win), but not pre-existing inventory or action time, and is still not a weekly cap.',
    'Pass --weekly after the location and iteration arguments to run a separate 50%-win-rate weekly budget scenario for ruined_station. Weekly runs are capped at 5,000 iterations for runtime; recovery budgets assume 350 stamina/week for one daily refill, 933.333 for continuous normal recovery, or 2,800 for continuously used rest recovery. The initial full 50 stamina is added separately; passive experience is also estimated at 40/hour in normal mode or 120/hour in rest mode, without rank scaling.',
    'Passive recovery starts from empty HP/stamina with max HP 100 and max stamina 50; actual recovery is capped. Rest and exploration are mutually exclusive per server rules.',
    'staminaCapacityRecoverySensitivity compares maximum stamina 50/55/60/65/70/75. The modeled 75 ceiling is base 50 + first rank-promotion +10 + steady_breath +5 + one active faction skill +10; no seeded title adds maxStamina. Weekly capacity sensitivity starts all cases at 50 current stamina, so a capacity bonus does not grant stamina immediately; daily-login cases spend the prior bar and recover up to the new cap once per 24h, while continuous-use cases consume passive recovery as it accrues. It isolates maxStamina and does not also apply steady_breath staminaCostReduce.',
    'weeklyStaminaCapacitySemanticsSensitivity compares the current capacity-coupled recovery interval with a capacity-only alternative that keeps recovery intervals at the base 50-capacity values. Both start at 50 current stamina. The daily-login row uses one 24-hour recovery window and can fill the larger cap under either policy; high-activity rows expose the difference in continuous recovery throughput. This is a design comparison, not a production rule change.',
    'The first skill timing is the earliest sampled action where the F-rank player has 100 total base experience (level 2) and 50 coins for steady_breath; story-choice bonuses are omitted.',
    'Quest rewards are configuration ceilings only and assume completion and claiming. PK scenarios separately assume exactly five accepted challenges, an independent fixed win probability, no challenge costs, and successful claim of eligible weekly quests; they are expected rewards, not guaranteed outcomes. PK/quest XP is rounded per reward using the configured rank multiplier.',
  ],
  explorationMeans: simulate(0).means,
  firstSkillReadiness: simulate(0).steadyBreathReadiness,
  perMonsterCombatEconomy: perMonsterCombat ? {
    sourceModel: perMonsterCombat.report.model,
    runsPerEncounter: perMonsterCombat.report.runsPerCase,
    playerProfile: 'no equipment; each event location minimum level; event location recommended rank',
    encounterCount: battleEvents.length,
    winRatesByEvent: Object.fromEntries([...perMonsterCombat.ratesByEventKey.entries()]
      .map(([eventKey, outcome]) => [eventKey, {
        winRatePercent: Number((outcome.winRate * 100).toFixed(3)),
        experienceOnWin: outcome.experienceOnWin,
      }])),
    firstTenExplorations: simulate(0, false, false, startingStamina, iterations,
      perMonsterCombat.ratesByEventKey),
    repeatableOnlySteadyState: simulate(0, true, false, startingStamina, iterations,
      perMonsterCombat.ratesByEventKey),
    repeatableOnlyWithBread: simulate(0, true, true, startingStamina, iterations,
      perMonsterCombat.ratesByEventKey),
  } : null,
  battleOutcomeSensitivity: combatSensitivitySupported
    ? [0, 0.5, 1].map((assumedWinRate) => ({
      assumedWinRate,
      ...simulate(assumedWinRate),
    }))
    : null,
  activeExperienceByRankSensitivity,
  repeatableOnlySteadyStateSensitivity: combatSensitivitySupported
    ? [0, 0.5, 1].map((assumedWinRate) => ({
      assumedWinRate,
      ...simulate(assumedWinRate, true),
    }))
    : {
      means: simulate(0, true).means,
      note: 'Other locations are shown without guessed combat rewards; the repeatable pool simulation still includes exploration-event rewards.',
    },
  repeatableOnlyWithBreadSensitivity: combatSensitivitySupported
    ? [0, 0.5, 1].map((assumedWinRate) => ({
      assumedWinRate,
      ...simulate(assumedWinRate, true, true),
    }))
    : null,
  weeklyRepeatableScenario,
  grossLiquidationSensitivity,
  battleEventAudit: {
    count: battleEvents.length,
    combatRewardDataComplete,
    missingMonsterKeys,
    configuredEncounters: battleEvents.map((event) => {
      const monster = monsterByKey.get(event.risks_json?.monsterKey);
      return {
        eventKey: event.event_key,
        monsterKey: event.risks_json?.monsterKey ?? null,
        monsterName: monster?.name ?? null,
        monsterStats: monster?.stats ?? null,
        monsterLocationKey: monster?.locationKey ?? null,
        monsterLocationRecommendedRank: locationsByKey.get(monster?.locationKey)?.recommended_rank ?? 'F',
        eventLocationKey: event.location_key,
        eventLocationRecommendedRank: locationsByKey.get(event.location_key)?.recommended_rank ?? 'F',
        locationMatches: monster?.locationKey === event.location_key,
        rewards: monster?.rewards ?? null,
        drops: monster?.drops ?? null,
      };
    }),
  },
  stationRatPerWinEconomy: stationRatCombatSupported ? {
    directCoins: stationRatRewards.coins,
    expectedDropSaleCoins: Number((stationRatRewards.dropsExpected.stamina_bread
      * stationRatRewards.npcSellPrices.stamina_bread
      + stationRatRewards.dropsExpected.monster_bone
      * stationRatRewards.npcSellPrices.monster_bone).toFixed(3)),
    estimatedCoinsIfEveryDropIsSold: Number((stationRatRewards.coins
      + stationRatRewards.dropsExpected.stamina_bread * stationRatRewards.npcSellPrices.stamina_bread
      + stationRatRewards.dropsExpected.monster_bone * stationRatRewards.npcSellPrices.monster_bone).toFixed(3)),
    caveat: 'The bread may be more valuable when used to restore up to 30 stamina than sold for 5 coins; the bone may be used for synthesis.',
  } : null,
  battleOutcomeSensitivityNote: combatSensitivitySupported
    ? null
    : 'This location has no complete validated monster reward mapping; exploration-only baseline is retained and combat rewards are not guessed.',
  passiveRecoverySnapshots,
  staminaCapacityRecoverySensitivity,
  weeklyPkScenario,
  weeklyPkWinRateSensitivity,
  weeklyStaminaCapacitySensitivity,
  weeklyStaminaCapacitySemanticsSensitivity,
  firstUniversalSkillTarget: {
    key: 'steady_breath',
    name: '稳定呼吸',
    unlock: '等级 2（累计经验 100）',
    cost: '50 星币',
    effects: '最大体力 +5，探索体力消耗 -3%',
    source: 'springboot-backend/src/main/resources/db/seed_skills.sql',
  },
  questRewardCeilings: {
    dailyExploreAndCombatQuests: { coins: 110, baseExperience: 45 },
    allFourDailyQuests: { coins: 170, baseExperience: 65 },
    allThreeWeeklyQuests: { coins: 570, baseExperience: 230, storyFragments: 10 },
  },
}, null, 2));
