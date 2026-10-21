import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const seedPath = resolve(repoRoot, 'springboot-backend/src/main/resources/db/seed_explore.json');
const seed = JSON.parse(readFileSync(seedPath, 'utf8'));
const locations = seed.filter((entry) => entry.type === 'location');
const events = seed.filter((entry) => entry.type === 'event');
const eventsByKey = new Map(events.map((event) => [event.event_key, event]));
const pitySamples = [0, 1, 2, 4, 7, 8];
const cliArguments = process.argv.slice(2);
const runsArgument = cliArguments.find((argument) => /^\d+$/.test(argument));
const simulationRuns = Number.parseInt(runsArgument || '50000', 10);
const maximumSimulatedExplorations = 10;
const startingStamina = 50;
const cumulativeActionCheckpoints = [1, 3, 5, 8, 10];
const locationArgument = cliArguments.find((argument) => argument.startsWith('--location='));
const choiceRouteArgument = cliArguments.find((argument) => argument.startsWith('--choice-route='));
const selectedLocationKey = locationArgument?.slice('--location='.length);
const choiceRoute = new Map((choiceRouteArgument?.slice('--choice-route='.length) || '')
  .split(',').filter(Boolean).map((entry) => {
    const separator = entry.lastIndexOf(':');
    if (separator <= 0) throw new Error(`Invalid choice route entry: ${entry}`);
    const eventKey = entry.slice(0, separator);
    const choiceIndex = Number.parseInt(entry.slice(separator + 1), 10);
    if (!Number.isInteger(choiceIndex) || choiceIndex < 0) {
      throw new Error(`Invalid choice index in route entry: ${entry}`);
    }
    return [eventKey, choiceIndex];
  }));
const numberOr = (value, fallback) => value != null && Number.isFinite(Number(value))
  ? Number(value) : fallback;
const percent = (value) => Number((100 * value).toFixed(2));
const hasConditions = (event) => Object.keys(event.required_conditions ?? {}).length > 0;

if (!Number.isInteger(simulationRuns) || simulationRuns < 1) {
  throw new Error('simulationRuns must be a positive integer');
}
if (selectedLocationKey && !locations.some((location) =>
  location.location_key === selectedLocationKey)) {
  throw new Error(`Unknown location: ${selectedLocationKey}`);
}
for (const [eventKey, choiceIndex] of choiceRoute) {
  const event = events.find((candidate) => candidate.event_key === eventKey);
  if (!event || event.event_type !== 'story') throw new Error(`Unknown story event: ${eventKey}`);
  if (!Array.isArray(event.choices) || choiceIndex >= event.choices.length) {
    throw new Error(`Choice ${choiceIndex} does not exist for ${eventKey}`);
  }
}

function typeAdjustedWeight(event, location) {
  const configuredWeight = numberOr(event.weight, 10);
  const eventRate = numberOr(location.event_rates?.[event.event_type], 100);
  return Math.max(1, Math.round(configuredWeight * eventRate / 100));
}

function adjustedWeight(event, location, storyPity) {
  let weight = typeAdjustedWeight(event, location);
  if (event.event_type === 'story') {
    // Mirrors ExploreServiceImpl: at least one weight point per missed main story.
    weight += Math.max(0, storyPity) * Math.max(1, Math.floor(weight / 2));
  }
  return weight;
}

function calculatePool(location, eligibleEvents, storyPity) {
  const stories = eligibleEvents.filter((event) => event.event_type === 'story');
  if (storyPity >= 8 && stories.length > 0) {
    const weightedStories = stories.map((event) => ({
      event,
      weight: adjustedWeight(event, location, storyPity),
    }));
    const totalStoryWeight = weightedStories.reduce((sum, entry) => sum + entry.weight, 0);
    const sharesByType = {};
    for (const type of new Set(eligibleEvents.map((event) => event.event_type))) {
      sharesByType[type] = type === 'story' ? 100 : 0;
    }
    return {
      totalWeight: totalStoryWeight,
      sharesByType,
      eligibleStoryEvents: weightedStories.map(({ event, weight }) => ({
        eventKey: event.event_key,
        probabilityPercent: percent(weight / totalStoryWeight),
        adjustedWeight: typeAdjustedWeight(event, location),
        weightAtCurrentPity: weight,
        softPityBonusPerMiss: Math.max(1, Math.floor(typeAdjustedWeight(event, location) / 2)),
        selectionAtGuarantee: 'weighted among eligible main stories',
      })),
      guaranteedStory: true,
    };
  }

  const weightedEvents = eligibleEvents.map((event) => ({
    event,
    weight: adjustedWeight(event, location, storyPity),
  }));
  const totalWeight = weightedEvents.reduce((sum, entry) => sum + entry.weight, 0);
  const sharesByType = {};
  for (const { event, weight } of weightedEvents) {
    sharesByType[event.event_type] = (sharesByType[event.event_type] ?? 0) + weight / totalWeight;
  }
  return {
    totalWeight,
    sharesByType: Object.fromEntries(Object.entries(sharesByType)
      .map(([type, share]) => [type, percent(share)])),
    eligibleStoryEvents: weightedEvents.filter(({ event }) => event.event_type === 'story')
      .map(({ event, weight }) => ({
        eventKey: event.event_key,
        probabilityPercent: percent(weight / totalWeight),
        adjustedWeight: typeAdjustedWeight(event, location),
        weightAtCurrentPity: weight,
        softPityBonusPerMiss: Math.max(1, Math.floor(typeAdjustedWeight(event, location) / 2)),
        selectionAtGuarantee: null,
      })),
    guaranteedStory: false,
  };
}

function createRandom(seedValue) {
  let state = seedValue >>> 0;
  return () => {
    state = (Math.imul(1664525, state) + 1013904223) >>> 0;
    return state / 0x1_0000_0000;
  };
}

function selectEvent(location, pool, storyPity, random) {
  let selectionPool = pool;
  if (storyPity >= 8) {
    const stories = pool.filter((event) => event.event_type === 'story');
    if (stories.length > 0) selectionPool = stories;
  }
  const weightedPool = selectionPool.map((event) => ({
    event,
    weight: adjustedWeight(event, location, storyPity),
  }));
  const totalWeight = weightedPool.reduce((sum, entry) => sum + entry.weight, 0);
  let roll = random() * totalWeight;
  for (const entry of weightedPool) {
    roll -= entry.weight;
    if (roll < 0) return entry.event;
  }
  return weightedPool.at(-1)?.event ?? null;
}

function wilsonInterval(successes, samples) {
  if (samples === 0) return null;
  const z = 1.96;
  const probability = successes / samples;
  const zSquared = z * z;
  const denominator = 1 + zSquared / samples;
  const center = (probability + zSquared / (2 * samples)) / denominator;
  const margin = z * Math.sqrt((probability * (1 - probability) / samples)
    + zSquared / (4 * samples * samples)) / denominator;
  return {
    lowerPercent: percent(Math.max(0, center - margin)),
    upperPercent: percent(Math.min(1, center + margin)),
  };
}

function simulateCumulativeStoryChances(location, eligibleEvents) {
  const targetStories = eligibleEvents.filter((event) => event.event_type === 'story');
  const actionCountsByStory = new Map(targetStories.map((event) =>
    [event.event_key, new Array(simulationRuns).fill(null)]));
  const seedValue = [...location.location_key].reduce((hash, character) =>
    ((hash * 31) + character.charCodeAt(0)) >>> 0, 20260925);
  const random = createRandom(seedValue);

  for (let run = 0; run < simulationRuns; run += 1) {
    const triggeredEvents = new Set();
    let storyPity = 0;
    let stamina = startingStamina;
    let explorations = 0;
    while (explorations < maximumSimulatedExplorations) {
      const pool = eligibleEvents.filter((event) =>
        event.repeatable !== 0 || !triggeredEvents.has(event.event_key));
      if (pool.length === 0) break;
      const selected = selectEvent(location, pool, storyPity, random);
      if (!selected) break;
      const staminaCost = numberOr(selected.stamina_cost, 5);
      if (stamina < staminaCost) break;
      stamina -= staminaCost;
      explorations += 1;

      if (selected.event_type === 'story') {
        const actions = actionCountsByStory.get(selected.event_key);
        if (actions && actions[run] === null) actions[run] = explorations;
        storyPity = 0;
      } else {
        storyPity += 1;
      }
      if (selected.repeatable === 0) triggeredEvents.add(selected.event_key);
    }
  }

  return [...actionCountsByStory.entries()].map(([eventKey, actions]) => {
    const reached = actions.filter((action) => action !== null).sort((left, right) => left - right);
    const countByCheckpoint = Object.fromEntries(cumulativeActionCheckpoints.map((checkpoint) => {
      const successes = actions.filter((action) => action !== null && action <= checkpoint).length;
      return [checkpoint, {
        probabilityPercent: percent(successes / simulationRuns),
        confidence95Percent: wilsonInterval(successes, simulationRuns),
      }];
    }));
    const percentile = (fraction) => reached.length === 0 ? null
      : reached[Math.max(0, Math.ceil(reached.length * fraction) - 1)];
    return {
      eventKey,
      probabilityWithinActions: countByCheckpoint,
      meanActionAmongRunsThatReachedIt: reached.length === 0 ? null
        : Number((reached.reduce((sum, action) => sum + action, 0) / reached.length).toFixed(3)),
      actionPercentilesAmongRunsThatReachedIt: {
        p50: percentile(0.5),
        p90: percentile(0.9),
      },
    };
  });
}

function conditionsAreSatisfied(event, triggeredStories, decisions, assumedLevel, assumedRank) {
  const conditions = event.required_conditions ?? {};
  if (conditions.required_level != null
      && assumedLevel < numberOr(conditions.required_level, Number.POSITIVE_INFINITY)) return false;
  if (Array.isArray(conditions.required_events)
      && conditions.required_events.some((eventKey) => !triggeredStories.has(eventKey))) return false;
  if (conditions.required_rank != null) {
    const rankOrder = ['F', 'E', 'D', 'C', 'B', 'A', 'S', 'SS', 'SSS'];
    const locationRank = rankOrder.indexOf(assumedRank);
    const requiredRank = rankOrder.indexOf(conditions.required_rank);
    if (requiredRank >= 0 && locationRank >= 0 && locationRank < requiredRank) return false;
  }
  if (Object.hasOwn(conditions, 'choice_unlockers')) {
    const unlockers = conditions.choice_unlockers;
    if (!Array.isArray(unlockers) || !unlockers.some((unlocker) => {
      const chosenIndex = decisions.get(unlocker.event_key);
      return Number.isInteger(chosenIndex) && unlocker.choice_indices?.includes(chosenIndex);
    })) return false;
  }
  return true;
}

function simulateFixedChoiceRoute(location, allLocationEvents) {
  const assumedLevel = Math.max(1, numberOr(location.min_level, 1));
  const assumedRank = location.recommended_rank ?? 'F';
  const targetStories = allLocationEvents.filter((event) => event.event_type === 'story');
  const actionCountsByStory = new Map(targetStories.map((event) =>
    [event.event_key, new Array(simulationRuns).fill(null)]));
  const externalRouteEntries = [...choiceRoute.entries()].filter(([eventKey]) =>
    events.find((event) => event.event_key === eventKey)?.location_key !== location.location_key);
  const routeSignature = [...choiceRoute.entries()].map(([key, value]) => `${key}:${value}`).join(',');
  const seedValue = [...`${location.location_key}|${routeSignature}`].reduce((hash, character) =>
    ((hash * 31) + character.charCodeAt(0)) >>> 0, 20260925);
  const random = createRandom(seedValue);

  for (let run = 0; run < simulationRuns; run += 1) {
    const triggeredStories = new Set(externalRouteEntries.map(([eventKey]) => eventKey));
    const triggeredOtherEvents = new Set();
    const decisions = new Map(externalRouteEntries);
    let storyPity = 0;
    let stamina = startingStamina;
    let explorations = 0;

    while (explorations < maximumSimulatedExplorations) {
      const pool = allLocationEvents.filter((event) => {
        const triggered = event.event_type === 'story' ? triggeredStories : triggeredOtherEvents;
        if (event.repeatable === 0 && triggered.has(event.event_key)) return false;
        return conditionsAreSatisfied(event, triggeredStories, decisions, assumedLevel, assumedRank);
      });
      if (pool.length === 0) break;
      const selected = selectEvent(location, pool, storyPity, random);
      if (!selected) break;
      const staminaCost = numberOr(selected.stamina_cost, 5);
      if (stamina < staminaCost) break;
      stamina -= staminaCost;
      explorations += 1;

      if (selected.event_type === 'story') {
        const actions = actionCountsByStory.get(selected.event_key);
        if (actions && actions[run] === null) actions[run] = explorations;
        if (Array.isArray(selected.choices) && selected.choices.length > 0) {
          const choiceIndex = choiceRoute.get(selected.event_key) ?? 0;
          if (choiceIndex < selected.choices.length) decisions.set(selected.event_key, choiceIndex);
        }
        storyPity = 0;
      } else {
        storyPity += 1;
      }
      if (selected.repeatable === 0) {
        const triggered = selected.event_type === 'story' ? triggeredStories : triggeredOtherEvents;
        triggered.add(selected.event_key);
      }
    }
  }

  return [...actionCountsByStory.entries()].map(([eventKey, actions]) => {
    const reached = actions.filter((action) => action !== null).sort((left, right) => left - right);
    const countByCheckpoint = Object.fromEntries(cumulativeActionCheckpoints.map((checkpoint) => {
      const successes = actions.filter((action) => action !== null && action <= checkpoint).length;
      return [checkpoint, {
        probabilityPercent: percent(successes / simulationRuns),
        confidence95Percent: wilsonInterval(successes, simulationRuns),
      }];
    }));
    const percentile = (fraction) => reached.length === 0 ? null
      : reached[Math.max(0, Math.ceil(reached.length * fraction) - 1)];
    return {
      eventKey,
      initiallyUnlocked: conditionsAreSatisfied(eventsByKey.get(eventKey),
        new Set(externalRouteEntries.map(([key]) => key)), new Map(externalRouteEntries),
        assumedLevel, assumedRank),
      probabilityWithinActions: countByCheckpoint,
      meanActionAmongRunsThatReachedIt: reached.length === 0 ? null
        : Number((reached.reduce((sum, action) => sum + action, 0) / reached.length).toFixed(3)),
      actionPercentilesAmongRunsThatReachedIt: { p50: percentile(0.5), p90: percentile(0.9) },
    };
  });
}

const locationResults = locations.map((location) => {
  const allLocationEvents = events.filter((event) => event.location_key === location.location_key);
  const eligibleAtFreshLocalProgress = allLocationEvents.filter((event) => !hasConditions(event));
  const conditionGatedEvents = allLocationEvents.filter(hasConditions).map((event) => ({
    eventKey: event.event_key,
    eventType: event.event_type,
    conditionKeys: Object.keys(event.required_conditions ?? {}).sort(),
  }));
  const pityCurve = pitySamples.map((storyPity) => {
    const pool = calculatePool(location, eligibleAtFreshLocalProgress, storyPity);
    return {
      storyPity,
      mainStoryChancePercent: pool.sharesByType.story ?? 0,
      sideStoryChancePercent: pool.sharesByType.side_story ?? 0,
      battleChancePercent: pool.sharesByType.battle_placeholder ?? 0,
      otherEventChancePercent: percent(1 - (pool.sharesByType.story ?? 0) / 100
        - (pool.sharesByType.side_story ?? 0) / 100
        - (pool.sharesByType.battle_placeholder ?? 0) / 100),
      storyGuaranteeActive: pool.guaranteedStory,
      eligibleStoryEvents: pool.eligibleStoryEvents,
      totalWeight: pool.totalWeight,
    };
  });

  return {
    locationKey: location.location_key,
    locationName: location.name,
    configuredTypeRates: location.event_rates ?? {},
    eligibleEventCountAtFreshLocalProgress: eligibleAtFreshLocalProgress.length,
    eligibleStoryCount: eligibleAtFreshLocalProgress.filter((event) =>
      event.event_type === 'story').length,
    eligibleSideStoryCount: eligibleAtFreshLocalProgress.filter((event) =>
      event.event_type === 'side_story').length,
    conditionGatedEventCount: conditionGatedEvents.length,
    conditionGatedEvents,
    pityCurve,
    cumulativeMainStoryChanceSimulation: {
      runs: simulationRuns,
      maximumExplorations: maximumSimulatedExplorations,
      startingStamina,
      targetStories: simulateCumulativeStoryChances(location, eligibleAtFreshLocalProgress),
    },
  };
});

const minimumWeightStoryEvents = events.filter((event) => event.event_type === 'story').flatMap((event) => {
  const location = locations.find((candidate) => candidate.location_key === event.location_key);
  if (!location) return [];
  const typeWeight = typeAdjustedWeight(event, location);
  return typeWeight === 1 ? [{
    locationKey: location.location_key,
    locationName: location.name,
    eventKey: event.event_key,
    softPityBonusPerMiss: Math.max(1, Math.floor(typeWeight / 2)),
    gatedUntilConditions: Object.keys(event.required_conditions ?? {}).sort(),
  }] : [];
});

const conditionalRouteSimulation = selectedLocationKey
  ? (() => {
    const location = locations.find((candidate) => candidate.location_key === selectedLocationKey);
    const allLocationEvents = events.filter((event) => event.location_key === selectedLocationKey);
    return {
      locationKey: location.location_key,
      locationName: location.name,
      runs: simulationRuns,
      maximumExplorations: maximumSimulatedExplorations,
      startingStamina,
      assumedPlayerLevel: Math.max(1, numberOr(location.min_level, 1)),
      assumedAvatarRank: location.recommended_rank ?? 'F',
      fixedChoiceRoute: Object.fromEntries(choiceRoute),
      externalRouteChoicesTreatedAsAlreadyCompleted: Object.fromEntries(externalRouteEntriesFor(location)),
      defaultChoiceForUnspecifiedLocalStories: 0,
      targetStories: simulateFixedChoiceRoute(location, allLocationEvents),
    };
  })()
  : null;

function externalRouteEntriesFor(location) {
  return [...choiceRoute.entries()].filter(([eventKey]) =>
    eventsByKey.get(eventKey)?.location_key !== location.location_key);
}

const report = {
  source: 'springboot-backend/src/main/resources/db/seed_explore.json',
  assumptions: [
    'Computes exact one-draw weighted shares from the seeded event weights and location event_rates; it mirrors Java integer rounding for type rates and the current minimum-one-point soft-pity rule.',
    'Assumes neutral storyEventBonus (0), a location the player can already enter, no pending choice snapshot, no previously triggered one-time local event, and no lower-location unfinished-story block.',
    'Only events with empty required_conditions are included in the baseline pool; condition-gated events are listed separately and their player-specific availability is not guessed.',
    'At storyPity >= 8 the backend filters out non-story events, then keeps weighted selection among eligible main stories; event query order does not decide which story is selected.',
    'This is a seed-file baseline, not a live database or deployed-server measurement; the exact per-action table is a snapshot, while cumulative chances are a separate condition-free Monte Carlo scenario.',
  ],
  cumulativeSimulationAssumptions: [
    'Runs each location independently from storyPity 0 and 50 stamina, stopping at 10 explorations, empty event pool, or insufficient stamina.',
    'Recomputes the event pool after each selected non-repeatable event, increments pity for every non-story event including side_story, and resets pity on main story.',
    'Events with required_conditions remain excluded throughout the run; story choices, skill/title bonuses, lower-location unfinished-story blocks, and combat outcome rewards are not simulated.',
    'Cumulative percentages use deterministic-seed Monte Carlo. The 95% Wilson interval describes sampling error only, not omitted game systems or live configuration drift.',
  ],
  conditionalRouteSimulationAssumptions: selectedLocationKey ? [
    'This optional scenario simulates one selected location after the character has reached it; it is not the full cross-location journey cost.',
    'Choice indices are fixed player decisions, never random rolls. Choices configured for other locations are loaded as already completed personal history; local choices are applied when their story event is first triggered.',
    'Unspecified local story choices default to option 0. The character is assumed to have completed all lower-location stories that remain reachable or have unresolved prerequisite choices, so this isolates the selected location pool from prior progression time.',
    'Level and avatar rank are assumed at the selected location minimum/recommended values. Skill/title bonuses, pending choice snapshots, combat rewards, stamina regeneration, and live database drift are excluded.',
  ] : [],
  minimumWeightStoryEvents,
  conditionalRouteSimulation,
  locations: locationResults,
};

console.log(JSON.stringify(report, null, 2));
