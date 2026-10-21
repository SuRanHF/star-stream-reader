import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const seedPath = resolve(repoRoot, 'springboot-backend/src/main/resources/db/seed_explore.json');
const seed = JSON.parse(readFileSync(seedPath, 'utf8'));
const seedSql = readFileSync(resolve(repoRoot, 'springboot-backend/src/main/resources/db/seed_explore.sql'), 'utf8');
const migration = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/migrations/20260925_enforce_story_choice_unlocks.sql'), 'utf8');
const finaleMigration = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/migrations/20260930_rewrite_orv_final_endings.sql'), 'utf8');
const personalStoryMigration = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/migrations/20261001_add_personal_story_resonance.sql'), 'utf8');
const locationMigrationSql = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/migrations/20260925_repair_location_story_gates.sql'), 'utf8');
const ironKingRiskMigration = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/migrations/20260926_add_iron_king_story_trial.sql'), 'utf8');
const monsterSeedSql = readFileSync(resolve(repoRoot,
  'springboot-backend/src/main/resources/db/seed_monsters.sql'), 'utf8');
const resourceKeys = ['coins', 'exp', 'storyFragments', 'channelHeat', 'worldLineShift'];
const rankMultipliers = { F: 1, E: 1.5, D: 2.5, C: 4, B: 7, A: 12, S: 20, SS: 35, SSS: 60 };
const numberOrZero = (value) => Number.isFinite(value) ? value : 0;
const rewardVector = (rewards = {}) => Object.fromEntries(
  resourceKeys.map((key) => [key, numberOrZero(rewards[key])]),
);

const locations = seed.filter((entry) => entry.type === 'location');
const locationsByKey = new Map(locations.map((location) => [location.location_key, location]));
const eventsByKey = new Map(seed.filter((entry) => entry.type === 'event')
  .map((event) => [event.event_key, event]));
const eventKeys = new Set(eventsByKey.keys());
const costMismatches = [];
const routeUnlockMismatches = [];
const routeConfigMismatches = [];
const locationGateMismatches = [];
const locationConfigMismatches = [];
const storyRiskConfigMismatches = [];

const normalizeSql = (value) => value.replace(/\s+/g, ' ').toLowerCase();
const routeConfigExistsInSql = (sql, targetKey, sourceKey, choiceIndices) => {
  const targetStatement = sql.split(';').find((statement) =>
    normalizeSql(statement).includes(`where \`event_key\` = '${targetKey}'`));
  if (!targetStatement) return false;
  const normalized = normalizeSql(targetStatement);
  const indices = `json_array(${choiceIndices.join(', ')})`;
  return normalized.includes(`'event_key', '${sourceKey}'`)
    && normalized.includes(`'choice_indices', ${indices}`);
};

const ironKingEvent = eventsByKey.get('chungmuro_iron_king_story');
const defianceRisk = ironKingEvent?.choices?.[0]?.risks;
const expectedIronKingRisk = {
  damagePercent: 15,
  monsterKey: 'iron_king_trial',
  battleName: '铁王的钢铁化试炼',
};
if (!defianceRisk || Object.entries(expectedIronKingRisk).some(([key, value]) =>
  defianceRisk[key] !== value)) {
  storyRiskConfigMismatches.push({ file: 'seed_explore.json', issue: 'defiance choice risk differs from runtime contract' });
}

const ironKingSqlStart = seedSql.indexOf("('chungmuro_iron_king_story'");
const ironKingSqlEnd = seedSql.indexOf("('chungmuro_shadow_king_story'", ironKingSqlStart);
const ironKingSqlEntry = ironKingSqlStart >= 0 && ironKingSqlEnd > ironKingSqlStart
  ? seedSql.slice(ironKingSqlStart, ironKingSqlEnd)
  : '';
if (!/"risks"\s*:\s*\{\s*"damagePercent"\s*:\s*15\s*,\s*"monsterKey"\s*:\s*"iron_king_trial"/.test(ironKingSqlEntry)
    || ironKingSqlEntry.includes('"risks_json"')) {
  storyRiskConfigMismatches.push({ file: 'seed_explore.sql', issue: 'defiance choice must use the runtime risks property' });
}

const normalizedIronKingRiskMigration = normalizeSql(ironKingRiskMigration);
if (!normalizedIronKingRiskMigration.includes("'$[0].risks'")
    || !normalizedIronKingRiskMigration.includes("'monsterkey', 'iron_king_trial'")) {
  storyRiskConfigMismatches.push({ file: '20260926_add_iron_king_story_trial.sql', issue: 'migration must restore the same risk config for existing databases' });
}
if (!monsterSeedSql.includes("('iron_king_trial'")) {
  storyRiskConfigMismatches.push({ file: 'seed_monsters.sql', issue: 'story encounter monster is missing' });
}

for (const source of eventsByKey.values()) {
  for (const [choiceIndex, choice] of (source.choices ?? []).entries()) {
    for (const targetKey of choice.unlock_events ?? []) {
      const target = eventsByKey.get(targetKey);
      const gates = target?.required_conditions?.choice_unlockers ?? [];
      const configured = gates.some((gate) => gate.event_key === source.event_key
        && gate.choice_indices?.includes(choiceIndex));
      if (!configured) {
        routeUnlockMismatches.push({
          sourceEventKey: source.event_key,
          choiceIndex,
          targetEventKey: targetKey,
          issue: target ? 'destination is missing this choice gate' : 'destination event is missing',
        });
      }
    }
  }
}

for (const target of eventsByKey.values()) {
  for (const gate of target.required_conditions?.choice_unlockers ?? []) {
    const source = eventsByKey.get(gate.event_key);
    for (const choiceIndex of gate.choice_indices ?? []) {
      if (!source?.choices?.[choiceIndex]?.unlock_events?.includes(target.event_key)) {
        routeUnlockMismatches.push({
          sourceEventKey: gate.event_key,
          choiceIndex,
          targetEventKey: target.event_key,
          issue: 'gate has no matching unlock_events declaration',
        });
      }
    }
    const routeMigration = target.event_key === 'ending_dream_continuation_choice'
      ? ['finale migration', finaleMigration]
      : ['darkest_spring_story_stitch', 'darkest_spring_abyss_gate'].includes(target.event_key)
        ? ['dark-spring personal story migration', personalStoryMigration]
        : ['migration', migration];
    for (const [name, sql] of [['seed_explore.sql', seedSql], routeMigration]) {
      if (!routeConfigExistsInSql(sql, target.event_key, gate.event_key, gate.choice_indices ?? [])) {
        routeConfigMismatches.push({
          targetEventKey: target.event_key,
          sourceEventKey: gate.event_key,
          choiceIndices: gate.choice_indices ?? [],
          file: name,
        });
      }
    }
  }
}

const grantedLocationKeys = new Set([...eventsByKey.values()].flatMap((event) =>
  (event.choices ?? []).flatMap((choice) => choice.unlock_locations ?? [])));
const locationChoiceProviders = new Map();
for (const event of eventsByKey.values()) {
  for (const [choiceIndex, choice] of (event.choices ?? []).entries()) {
    for (const locationKey of choice.unlock_locations ?? []) {
      const providers = locationChoiceProviders.get(locationKey) ?? [];
      providers.push({ eventKey: event.event_key, choiceIndex });
      locationChoiceProviders.set(locationKey, providers);
    }
  }
}

const locationLevelRequirement = (locationKey) => {
  const location = locationsByKey.get(locationKey);
  return Math.max(
    numberOrZero(location?.min_level) || 1,
    numberOrZero(location?.unlock_conditions?.required_level) || 1,
  );
};

const analyzeRouteProgression = (routeEvents, decisions) => {
  const routeEventKeys = new Set(routeEvents);
  const selectedChoices = new Map(decisions.map(({ eventKey, choiceIndex }) => [eventKey, choiceIndex]));
  const unlockedLocationKeys = new Set();
  for (const [eventKey, choiceIndex] of selectedChoices) {
    for (const locationKey of eventsByKey.get(eventKey)?.choices?.[choiceIndex]?.unlock_locations ?? []) {
      unlockedLocationKeys.add(locationKey);
    }
  }

  const usedLocationKeys = [...new Set(routeEvents.map((eventKey) =>
    eventsByKey.get(eventKey)?.location_key).filter(Boolean))].sort();
  const requiredLocations = usedLocationKeys.map((locationKey) => ({
    locationKey,
    name: locationsByKey.get(locationKey)?.name ?? locationKey,
    minimumPlayerLevel: locationLevelRequirement(locationKey),
  }));
  const externalEventGates = [];
  const routeIssues = [];
  const addExternalGate = (gate) => externalEventGates.push({
    ...gate,
    eventKeys: [...new Set(gate.eventKeys)].sort(),
  });

  for (const eventKey of routeEvents) {
    const event = eventsByKey.get(eventKey);
    const eventConditions = event?.required_conditions ?? {};
    for (const prerequisiteEventKey of eventConditions.required_events ?? []) {
      if (!routeEventKeys.has(prerequisiteEventKey)) {
        addExternalGate({ kind: 'event_all', sourceEventKey: eventKey,
          eventKeys: [prerequisiteEventKey] });
      }
    }
    const choiceUnlockers = eventConditions.choice_unlockers;
    if (Array.isArray(choiceUnlockers) && !choiceUnlockers.some((gate) =>
      routeEventKeys.has(gate.event_key)
        && gate.choice_indices?.includes(selectedChoices.get(gate.event_key)))) {
      const outsideRoute = choiceUnlockers.filter((gate) => !routeEventKeys.has(gate.event_key));
      if (outsideRoute.length > 0) {
        addExternalGate({
          kind: 'choice_any',
          sourceEventKey: eventKey,
          eventKeys: outsideRoute.map((gate) => gate.event_key),
          choices: outsideRoute.map((gate) => ({
            eventKey: gate.event_key,
            choiceIndices: gate.choice_indices ?? [],
          })),
        });
      } else {
        routeIssues.push({ eventKey, issue: 'route does not satisfy any configured choice gate' });
      }
    }
  }

  for (const locationKey of usedLocationKeys) {
    const conditions = locationsByKey.get(locationKey)?.unlock_conditions ?? {};
    for (const prerequisiteEventKey of conditions.prerequisite_events ?? []) {
      if (!routeEventKeys.has(prerequisiteEventKey)) {
        addExternalGate({ kind: 'event_all', locationKey, eventKeys: [prerequisiteEventKey] });
      }
    }
    const anyPrerequisites = conditions.prerequisite_events_any ?? [];
    if (anyPrerequisites.length > 0
        && !anyPrerequisites.some((eventKey) => routeEventKeys.has(eventKey))) {
      addExternalGate({ kind: 'event_any', locationKey, eventKeys: anyPrerequisites });
    }
    if (typeof conditions.unlock_by_choice === 'string'
        && !unlockedLocationKeys.has(conditions.unlock_by_choice)) {
      const providers = locationChoiceProviders.get(conditions.unlock_by_choice) ?? [];
      const usableExternalProviders = providers.filter((provider) =>
        !routeEventKeys.has(provider.eventKey));
      if (usableExternalProviders.length > 0) {
        addExternalGate({
          kind: 'location_choice_any',
          locationKey,
          unlockedLocationKey: conditions.unlock_by_choice,
          eventKeys: usableExternalProviders.map((provider) => provider.eventKey),
          choices: usableExternalProviders,
        });
      } else {
        routeIssues.push({
          locationKey,
          issue: `route does not unlock prerequisite location ${conditions.unlock_by_choice}`,
        });
      }
    }
  }

  const gateLevel = (gate) => {
    const levels = gate.eventKeys.filter((eventKey) => eventKeys.has(eventKey)).map((eventKey) =>
      locationLevelRequirement(eventsByKey.get(eventKey)?.location_key));
    if (gate.kind === 'event_any' || gate.kind === 'location_choice_any') {
      return levels.length ? Math.min(...levels) : 1;
    }
    return levels.length ? Math.max(...levels) : 1;
  };
  const minimumPlayerLevel = Math.max(
    ...requiredLocations.map((location) => location.minimumPlayerLevel),
    ...externalEventGates.map(gateLevel),
    1,
  );

  return {
    requiredLocations,
    externalEventGates,
    minimumPlayerLevel,
    routeIssues,
  };
};

const storyEvents = [...eventsByKey.values()].filter((event) =>
  ['story', 'side_story'].includes(event.event_type));
const reachableStoryEventKeys = new Set();
const reachableStoryLocations = new Set();
let reachabilityChanged = true;
while (reachabilityChanged) {
  reachabilityChanged = false;
  for (const event of storyEvents) {
    if (reachableStoryEventKeys.has(event.event_key)) continue;
    const locationConditions = locationsByKey.get(event.location_key)?.unlock_conditions ?? {};
    const eventConditions = event.required_conditions ?? {};
    const allEventsAvailable = (required) => Array.isArray(required)
      && required.every((eventKey) => reachableStoryEventKeys.has(eventKey));
    if (Object.hasOwn(locationConditions, 'prerequisite_events')
        && !allEventsAvailable(locationConditions.prerequisite_events)) continue;
    if (Object.hasOwn(locationConditions, 'prerequisite_events_any')
        && (!Array.isArray(locationConditions.prerequisite_events_any)
          || !locationConditions.prerequisite_events_any.some((eventKey) =>
            reachableStoryEventKeys.has(eventKey)))) continue;
    if (Object.hasOwn(locationConditions, 'unlock_by_choice')
        && !reachableStoryLocations.has(locationConditions.unlock_by_choice)) continue;
    if (Object.hasOwn(eventConditions, 'required_events')
        && !allEventsAvailable(eventConditions.required_events)) continue;
    const choiceUnlockers = eventConditions.choice_unlockers;
    if (Object.hasOwn(eventConditions, 'choice_unlockers')
        && (!Array.isArray(choiceUnlockers) || !choiceUnlockers.some((gate) => {
          const source = eventsByKey.get(gate.event_key);
          return reachableStoryEventKeys.has(gate.event_key)
            && gate.choice_indices?.some((choiceIndex) => Boolean(
              source?.choices?.[choiceIndex]?.unlock_events?.includes(event.event_key)));
        }))) continue;
    reachableStoryEventKeys.add(event.event_key);
    for (const choice of event.choices ?? []) {
      for (const locationKey of choice.unlock_locations ?? []) reachableStoryLocations.add(locationKey);
    }
    reachabilityChanged = true;
  }
}
const unreachableStoryEvents = storyEvents.filter((event) => !reachableStoryEventKeys.has(event.event_key))
  .map((event) => ({
    eventKey: event.event_key,
    locationKey: event.location_key,
    choiceUnlockers: event.required_conditions?.choice_unlockers ?? [],
  }));
const normalizedSeedSql = seedSql.replace(/\r\n/g, '\n');
const locationSeedTuple = (locationKey) => {
  const marker = `('${locationKey}',`;
  const start = normalizedSeedSql.indexOf(marker);
  if (start < 0) return '';
  const remainder = normalizedSeedSql.slice(start + marker.length);
  const nextTupleOffset = remainder.search(/\n\n\('/);
  return nextTupleOffset < 0 ? remainder : remainder.slice(0, nextTupleOffset);
};

for (const location of locations) {
  const conditions = location.unlock_conditions ?? {};
  for (const prerequisite of conditions.prerequisite_events ?? []) {
    if (!eventKeys.has(prerequisite)) {
      locationGateMismatches.push({
        locationKey: location.location_key,
        prerequisiteEvent: prerequisite,
        issue: 'required event key is absent from the exploration seed',
      });
    }
  }
  if (Object.hasOwn(conditions, 'prerequisite_events_any')) {
    const alternatives = conditions.prerequisite_events_any;
    if (!Array.isArray(alternatives) || alternatives.length === 0
        || !alternatives.some((eventKey) => eventKeys.has(eventKey))) {
      locationGateMismatches.push({
        locationKey: location.location_key,
        prerequisiteEventsAny: alternatives,
        issue: 'alternative prerequisites must contain at least one current seeded event key',
      });
    }
  }
  if (typeof conditions.unlock_by_choice === 'string'
      && !grantedLocationKeys.has(conditions.unlock_by_choice)) {
    locationGateMismatches.push({
      locationKey: location.location_key,
      unlockByChoice: conditions.unlock_by_choice,
      issue: 'no story choice grants this location',
    });
  }

  const sqlTuple = locationSeedTuple(location.location_key);
  if (!sqlTuple || (conditions.required_level != null
      && !sqlTuple.includes(`"required_level":${conditions.required_level}`))) {
    locationConfigMismatches.push({ locationKey: location.location_key, file: 'seed_explore.sql' });
    continue;
  }
  const sqlLocationConfig = normalizeSql(sqlTuple);
  for (const prerequisite of conditions.prerequisite_events ?? []) {
    if (!sqlLocationConfig.includes(prerequisite.toLowerCase())) {
      locationConfigMismatches.push({
        locationKey: location.location_key,
        prerequisiteEvent: prerequisite,
        file: 'seed_explore.sql',
      });
    }
  }
  for (const prerequisite of conditions.prerequisite_events_any ?? []) {
    if (!sqlLocationConfig.includes(prerequisite.toLowerCase())) {
      locationConfigMismatches.push({
        locationKey: location.location_key,
        prerequisiteEvent: prerequisite,
        file: 'seed_explore.sql',
      });
    }
  }
  if (conditions.unlock_by_choice && !sqlLocationConfig.includes(conditions.unlock_by_choice.toLowerCase())) {
    locationConfigMismatches.push({
      locationKey: location.location_key,
      unlockByChoice: conditions.unlock_by_choice,
      file: 'seed_explore.sql',
    });
  }

  const locationMigrationStatement = locationMigrationSql.split(';').find((statement) =>
    normalizeSql(statement).includes(`where \`location_key\` = '${location.location_key}'`));
  for (const prerequisite of conditions.prerequisite_events_any ?? []) {
    if (!locationMigrationStatement
        || !normalizeSql(locationMigrationStatement).includes(`'${prerequisite.toLowerCase()}'`)) {
      locationConfigMismatches.push({
        locationKey: location.location_key,
        prerequisiteEvent: prerequisite,
        file: 'location migration',
      });
    }
  }
  if (location.location_key === 'seoul_forest'
      && (!locationMigrationStatement || !normalizeSql(locationMigrationStatement).includes('json_remove'))) {
    locationConfigMismatches.push({
      locationKey: location.location_key,
      issue: 'self-referential unlock_by_choice removal is missing',
      file: 'location migration',
    });
  }
}

const routeNodes = new Set();
const routeAdjacency = new Map();
const routeIncoming = new Set();
for (const target of eventsByKey.values()) {
  for (const gate of target.required_conditions?.choice_unlockers ?? []) {
    routeNodes.add(gate.event_key);
    routeNodes.add(target.event_key);
    routeAdjacency.set(gate.event_key, [...(routeAdjacency.get(gate.event_key) ?? []), target.event_key]);
    routeAdjacency.set(target.event_key, [...(routeAdjacency.get(target.event_key) ?? []), gate.event_key]);
    routeIncoming.add(target.event_key);
  }
}

const addRewardVectors = (left, right) => Object.fromEntries(
  resourceKeys.map((key) => [key, left[key] + right[key]]),
);
const maximumEnumeratedRoutes = 100_000;
const visitedRouteNodes = new Set();
const reachableStoryRouteComponents = [];
const componentRouteSets = new Map();
const routeComponentIssues = [];

for (const firstNode of routeNodes) {
  if (visitedRouteNodes.has(firstNode)) continue;
  const componentKeys = new Set();
  const pendingComponentNodes = [firstNode];
  while (pendingComponentNodes.length > 0) {
    const current = pendingComponentNodes.pop();
    if (componentKeys.has(current)) continue;
    componentKeys.add(current);
    visitedRouteNodes.add(current);
    for (const neighbor of routeAdjacency.get(current) ?? []) pendingComponentNodes.push(neighbor);
  }

  const rootEventKeys = [...componentKeys].filter((key) => !routeIncoming.has(key)).sort();
  if (rootEventKeys.length === 0) {
    routeComponentIssues.push({
      eventKeys: [...componentKeys].sort(),
      issue: 'choice-gated component has no root event; its unlock graph may contain a cycle',
    });
    continue;
  }

  const routes = [];
  let routesTruncated = false;
  const enumerate = (pending, visited, rewards, decisions, experienceByRank, requiredLocationLevel) => {
    if (routes.length >= maximumEnumeratedRoutes) {
      routesTruncated = true;
      return;
    }
    const [eventKey, ...remaining] = pending;
    if (!eventKey) {
      const routeEventKeys = [...visited].sort();
      routes.push({
        rewards,
        eventKeys: routeEventKeys,
        decisions,
        experienceByRank,
        requiredLocationLevel,
        progression: analyzeRouteProgression(routeEventKeys, decisions),
      });
      return;
    }
    if (visited.has(eventKey)) {
      enumerate(remaining, visited, rewards, decisions);
      return;
    }

    const event = eventsByKey.get(eventKey);
    if (!event) return;
    const visitedWithEvent = new Set(visited).add(eventKey);
    const baseReward = rewardVector(event.rewards);
    const withBaseReward = addRewardVectors(rewards, baseReward);
    const location = locationsByKey.get(event.location_key);
    const eventLocationLevel = Math.max(
      numberOrZero(location?.min_level) || 1,
      numberOrZero(location?.unlock_conditions?.required_level) || 1,
    );
    const levelAfterEvent = Math.max(requiredLocationLevel, eventLocationLevel);
    const experienceAfterEventByRank = Object.fromEntries(Object.entries(rankMultipliers).map(
      ([rank, multiplier]) => [rank,
        experienceByRank[rank] + Math.round(baseReward.exp * multiplier)],
    ));
    const choices = event.choices?.length ? event.choices : [null];
    for (const [choiceIndex, choice] of choices.entries()) {
      const choiceReward = rewardVector(choice?.rewards_override);
      const withChoiceReward = addRewardVectors(withBaseReward, choiceReward);
      const experienceAfterChoiceByRank = Object.fromEntries(Object.entries(rankMultipliers).map(
        ([rank, multiplier]) => [rank,
          experienceAfterEventByRank[rank] + Math.round(choiceReward.exp * multiplier)],
      ));
      const nextPending = [...remaining];
      for (const unlockedKey of choice?.unlock_events ?? []) {
        if (componentKeys.has(unlockedKey)
            && !visitedWithEvent.has(unlockedKey)
            && !nextPending.includes(unlockedKey)) {
          nextPending.push(unlockedKey);
        }
      }
      const nextDecisions = choice
        ? [...decisions, {
          eventKey,
          choiceIndex,
          choiceLabel: choice.label ?? '',
        }]
        : decisions;
      enumerate(nextPending, visitedWithEvent, withChoiceReward, nextDecisions,
        experienceAfterChoiceByRank, levelAfterEvent);
      if (routesTruncated) break;
    }
  };

  enumerate(rootEventKeys, new Set(), rewardVector(), [],
    Object.fromEntries(Object.keys(rankMultipliers).map((rank) => [rank, 0])), 1);
  if (routesTruncated) {
    routeComponentIssues.push({
      eventKeys: [...componentKeys].sort(),
      issue: `route enumeration exceeded ${maximumEnumeratedRoutes} combinations`,
    });
  }

  const rewardExtremes = Object.fromEntries(resourceKeys.map((key) => {
    const minimum = routes.reduce((best, route) =>
      !best || route.rewards[key] < best.rewards[key] ? route : best, null);
    const maximum = routes.reduce((best, route) =>
      !best || route.rewards[key] > best.rewards[key] ? route : best, null);
    const summarize = (route) => route ? {
      amount: route.rewards[key],
      eventKeys: route.eventKeys,
      decisions: route.decisions,
    } : null;
    return [key, { minimum: summarize(minimum), maximum: summarize(maximum) }];
  }));
  const rankExperienceRanges = Object.fromEntries(Object.keys(rankMultipliers).map((rank) => {
    const values = routes.map((route) => route.experienceByRank[rank]);
    return [rank, values.length ? { minimum: Math.min(...values), maximum: Math.max(...values) } : null];
  }));
  const locationLevelRequirements = routes.map((route) => route.requiredLocationLevel);
  const progressionProfilesByKey = new Map();
  for (const route of routes) {
    const profileKey = JSON.stringify(route.progression);
    const profile = progressionProfilesByKey.get(profileKey);
    if (profile) profile.routeCount += 1;
    else progressionProfilesByKey.set(profileKey, {
      routeCount: 1,
      ...route.progression,
    });
  }

  const componentId = reachableStoryRouteComponents.length + 1;
  componentRouteSets.set(componentId, routes);
  reachableStoryRouteComponents.push({
    componentId,
    rootEventKeys,
    eventKeys: [...componentKeys].sort(),
    routeCount: routes.length,
    routesTruncated,
    locationLevelRequirementRange: locationLevelRequirements.length ? {
      minimum: Math.min(...locationLevelRequirements),
      maximum: Math.max(...locationLevelRequirements),
    } : null,
    routeProgressionProfiles: [...progressionProfilesByKey.values()],
    experienceRangesByRank: rankExperienceRanges,
    rewardExtremes,
  });
}

const routeComponentByEvent = new Map(reachableStoryRouteComponents.flatMap((component) =>
  component.eventKeys.map((eventKey) => [eventKey, component.componentId])));
for (const component of reachableStoryRouteComponents) {
  const directDependencies = new Set();
  for (const profile of component.routeProgressionProfiles) {
    profile.prerequisiteComponents = [...new Set(profile.externalEventGates.flatMap((gate) =>
      gate.eventKeys.map((eventKey) => routeComponentByEvent.get(eventKey))
        .filter((componentId) => componentId && componentId !== component.componentId)))].sort();
    for (const componentId of profile.prerequisiteComponents) directDependencies.add(componentId);
  }
  component.directPrerequisiteComponents = [...directDependencies].sort();
}

const componentDependenciesById = new Map(reachableStoryRouteComponents.map((component) =>
  [component.componentId, component.directPrerequisiteComponents]));
const collectTransitiveDependencies = (componentId, visited = new Set()) => {
  for (const dependencyId of componentDependenciesById.get(componentId) ?? []) {
    if (visited.has(dependencyId)) continue;
    visited.add(dependencyId);
    collectTransitiveDependencies(dependencyId, visited);
  }
  return [...visited].sort();
};
for (const component of reachableStoryRouteComponents) {
  component.transitivePrerequisiteComponents = collectTransitiveDependencies(component.componentId);
}

const componentRouteDependencies = (route) => [...new Set(route.progression.externalEventGates
  .flatMap((gate) => gate.eventKeys.map((eventKey) => routeComponentByEvent.get(eventKey))
    .filter((componentId) => componentId && componentId !== route.componentId)))].sort();
const topologicalComponentIds = [];
const visitedForOrdering = new Set();
const visitForOrdering = (componentId) => {
  if (visitedForOrdering.has(componentId)) return;
  visitedForOrdering.add(componentId);
  for (const dependencyId of componentDependenciesById.get(componentId) ?? []) {
    visitForOrdering(dependencyId);
  }
  topologicalComponentIds.push(componentId);
};
for (const component of reachableStoryRouteComponents) visitForOrdering(component.componentId);

const gatedPrerequisiteRouteClosures = [];
const closedRoutesByComponent = new Map();
const gatedClosureIssues = [];
const gateSatisfiedByCombinedRoute = (gate, combinedEventKeys, combinedDecisions) => {
  const selectedChoices = new Map(combinedDecisions.map(({ eventKey, choiceIndex }) =>
    [eventKey, choiceIndex]));
  if (gate.kind === 'event_all') {
    return gate.eventKeys.length > 0 && gate.eventKeys.every((eventKey) =>
      combinedEventKeys.has(eventKey));
  }
  if (gate.kind === 'event_any') {
    return gate.eventKeys.some((eventKey) => eventKeys.has(eventKey)
      && combinedEventKeys.has(eventKey));
  }
  return (gate.choices ?? []).some((choice) => selectedChoices.has(choice.eventKey)
    && (choice.choiceIndices?.includes(selectedChoices.get(choice.eventKey))
      || choice.choiceIndex === selectedChoices.get(choice.eventKey)));
};
const prerequisiteChoiceBundles = (gate, routeEventKeys) => {
  const optionsForEvent = (eventKey, allowedChoiceIndices) => {
    const event = eventsByKey.get(eventKey);
    if (!event || routeEventKeys.has(eventKey)) return [];
    const choices = event.choices ?? [];
    if (choices.length === 0) return [{ eventKey, choiceIndex: null }];
    const choiceIndices = allowedChoiceIndices
      ? choices.map((_, index) => index).filter((index) => allowedChoiceIndices.includes(index))
      : choices.map((_, index) => index);
    return choiceIndices.map((choiceIndex) => ({ eventKey, choiceIndex }));
  };

  if (gate.kind === 'event_all') {
    const missingEventKeys = gate.eventKeys.filter((eventKey) => !routeEventKeys.has(eventKey));
    const eventOptions = missingEventKeys.map((eventKey) => optionsForEvent(eventKey));
    if (eventOptions.some((options) => options.length === 0)) return [];
    let bundles = [[]];
    for (const options of eventOptions) {
      bundles = bundles.flatMap((bundle) => options.map((option) => [...bundle, option]));
    }
    return bundles;
  }
  if (gate.kind === 'event_any') {
    return gate.eventKeys.flatMap((eventKey) => optionsForEvent(eventKey).map((option) => [option]));
  }
  return (gate.choices ?? []).flatMap((choice) => optionsForEvent(
    choice.eventKey,
    choice.choiceIndices ?? (Number.isInteger(choice.choiceIndex) ? [choice.choiceIndex] : null),
  ).map((option) => [option]));
};

const expandSeededPrerequisiteEvents = (routes) => {
  const expandedRoutes = [];
  let routesTruncated = false;
  const visit = (route, blockedGateKeys = new Set()) => {
    if (expandedRoutes.length >= maximumEnumeratedRoutes) {
      routesTruncated = true;
      return;
    }
    const combinedEventKeys = new Set(route.eventKeys);
    const unsatisfiedGates = route.progression.externalEventGates.filter((gate) =>
      !gateSatisfiedByCombinedRoute(gate, combinedEventKeys, route.decisions));
    const nextGate = unsatisfiedGates.find((gate) => !blockedGateKeys.has(JSON.stringify(gate)));
    if (!nextGate) {
      expandedRoutes.push({ ...route, remainingPrerequisites: unsatisfiedGates });
      return;
    }

    const bundles = prerequisiteChoiceBundles(nextGate, combinedEventKeys);
    if (bundles.length === 0) {
      const blocked = new Set(blockedGateKeys).add(JSON.stringify(nextGate));
      visit(route, blocked);
      return;
    }

    for (const bundle of bundles) {
      const next = {
        ...route,
        eventKeys: [...route.eventKeys],
        decisions: [...route.decisions],
        rewards: { ...route.rewards },
        experienceByRank: { ...route.experienceByRank },
        progression: {
          ...route.progression,
          requiredLocations: [...route.progression.requiredLocations],
          externalEventGates: [...route.progression.externalEventGates],
          routeIssues: [...(route.progression.routeIssues ?? [])],
        },
      };
      for (const { eventKey, choiceIndex } of bundle) {
        if (next.eventKeys.includes(eventKey)) continue;
        const event = eventsByKey.get(eventKey);
        const choice = Number.isInteger(choiceIndex) ? event.choices?.[choiceIndex] : null;
        const baseReward = rewardVector(event.rewards);
        const choiceReward = rewardVector(choice?.rewards_override);
        for (const key of resourceKeys) {
          next.rewards[key] += baseReward[key] + choiceReward[key];
        }
        for (const [rank, multiplier] of Object.entries(rankMultipliers)) {
          next.experienceByRank[rank] += Math.round(baseReward.exp * multiplier)
            + Math.round(choiceReward.exp * multiplier);
        }
        next.eventKeys.push(eventKey);
        if (choice) next.decisions.push({ eventKey, choiceIndex, choiceLabel: choice.label ?? '' });

        const eventDecisions = choice
          ? [{ eventKey, choiceIndex, choiceLabel: choice.label ?? '' }]
          : [];
        const eventProgression = analyzeRouteProgression([eventKey], eventDecisions);
        next.progression.minimumPlayerLevel = Math.max(
          next.progression.minimumPlayerLevel,
          eventProgression.minimumPlayerLevel,
        );
        const knownLocations = new Set(next.progression.requiredLocations.map((location) =>
          location.locationKey));
        next.progression.requiredLocations.push(...eventProgression.requiredLocations.filter((location) =>
          !knownLocations.has(location.locationKey)));
        next.progression.externalEventGates.push(...eventProgression.externalEventGates);
        next.progression.routeIssues.push(...eventProgression.routeIssues);
      }
      next.eventKeys.sort();
      visit(next, new Set());
      if (routesTruncated) return;
    }
  };

  for (const route of routes) {
    visit(route);
    if (routesTruncated) break;
  }
  return { routes: expandedRoutes, routesTruncated };
};

for (const componentId of topologicalComponentIds) {
  const localRoutes = componentRouteSets.get(componentId) ?? [];
  let endToEndRoutes = [];
  let closureTruncated = false;
  for (const localRoute of localRoutes) {
    const dependencyIds = componentRouteDependencies({ ...localRoute, componentId });
    const dependencyRouteSets = dependencyIds.map((dependencyId) =>
      closedRoutesByComponent.get(dependencyId) ?? []);
    if (dependencyRouteSets.some((dependencyRoutes) => dependencyRoutes.length === 0)) {
      gatedClosureIssues.push({
        componentId,
        eventKeys: localRoute.eventKeys,
        issue: 'a required story component has no reachable prerequisite route',
      });
      continue;
    }

    const routesBeforeLocalRoute = endToEndRoutes.length;
    const dependencyCombinations = [];
    const enumerateDependencies = (index, selectedRoutes) => {
      if (dependencyCombinations.length >= maximumEnumeratedRoutes) {
        closureTruncated = true;
        return;
      }
      if (index === dependencyRouteSets.length) {
        dependencyCombinations.push(selectedRoutes);
        return;
      }
      for (const dependencyRoute of dependencyRouteSets[index]) {
        if (selectedRoutes.some((selected) => selected.componentIds.some((id) =>
          dependencyRoute.componentIds.includes(id)))) continue;
        enumerateDependencies(index + 1, [...selectedRoutes, dependencyRoute]);
        if (closureTruncated) return;
      }
    };
    enumerateDependencies(0, []);

    for (const dependencyRoutes of dependencyCombinations) {
      if (endToEndRoutes.length >= maximumEnumeratedRoutes) {
        closureTruncated = true;
        break;
      }
      const combinedEventKeys = new Set([
        ...dependencyRoutes.flatMap((route) => route.eventKeys),
        ...localRoute.eventKeys,
      ]);
      const combinedDecisions = [...dependencyRoutes.flatMap((route) => route.decisions),
        ...localRoute.decisions];
      if (!localRoute.progression.externalEventGates.every((gate) =>
        componentRouteDependencies({
          ...localRoute,
          progression: { externalEventGates: [gate] },
          componentId,
        }).length === 0 || gateSatisfiedByCombinedRoute(gate, combinedEventKeys, combinedDecisions))) {
        continue;
      }

      const allParts = [...dependencyRoutes, {
        componentIds: [componentId],
        ...localRoute,
      }];
      const allGates = allParts.flatMap((route) => route.progression.externalEventGates);
      const remainingPrerequisites = allGates.filter((gate) =>
        !gateSatisfiedByCombinedRoute(gate, combinedEventKeys, combinedDecisions));
      const combinedRewards = rewardVector();
      const combinedExperience = Object.fromEntries(Object.keys(rankMultipliers)
        .map((rank) => [rank, 0]));
      const requiredLocationsByKey = new Map();
      for (const route of allParts) {
        for (const key of resourceKeys) combinedRewards[key] += route.rewards[key];
        for (const rank of Object.keys(rankMultipliers)) {
          combinedExperience[rank] += route.experienceByRank[rank];
        }
        for (const location of route.progression.requiredLocations) {
          requiredLocationsByKey.set(location.locationKey, location);
        }
      }
      endToEndRoutes.push({
        componentIds: [...new Set(allParts.flatMap((route) => route.componentIds))].sort(),
        eventKeys: [...combinedEventKeys].sort(),
        decisions: combinedDecisions,
        rewards: combinedRewards,
        experienceByRank: combinedExperience,
        minimumPlayerLevel: Math.max(
          ...allParts.map((route) => route.progression.minimumPlayerLevel),
          1,
        ),
        requiredLocations: [...requiredLocationsByKey.values()].sort((left, right) =>
          left.locationKey.localeCompare(right.locationKey)),
        remainingPrerequisites,
        progression: {
          minimumPlayerLevel: Math.max(
            ...allParts.map((route) => route.progression.minimumPlayerLevel),
            1,
          ),
          requiredLocations: [...requiredLocationsByKey.values()],
          externalEventGates: allGates,
          routeIssues: allParts.flatMap((route) => route.progression.routeIssues ?? []),
        },
      });
    }
    if (dependencyIds.length > 0 && endToEndRoutes.length === routesBeforeLocalRoute
        && !closureTruncated) {
      gatedClosureIssues.push({
        componentId,
        eventKeys: localRoute.eventKeys,
        issue: 'no prerequisite component route satisfies this route\'s external story gates',
      });
    }
  }
  const externallyExpandedRoutes = expandSeededPrerequisiteEvents(endToEndRoutes);
  endToEndRoutes = externallyExpandedRoutes.routes;
  closureTruncated ||= externallyExpandedRoutes.routesTruncated;
  closedRoutesByComponent.set(componentId, endToEndRoutes);

  const minimumLevel = endToEndRoutes.reduce((minimum, route) =>
    Math.min(minimum, route.minimumPlayerLevel), Number.POSITIVE_INFINITY);
  const maximumLevel = endToEndRoutes.reduce((maximum, route) =>
    Math.max(maximum, route.minimumPlayerLevel), 0);
  const experienceRangesByRank = Object.fromEntries(Object.keys(rankMultipliers).map((rank) => {
    const values = endToEndRoutes.map((route) => route.experienceByRank[rank]);
    return [rank, values.length ? { minimum: Math.min(...values), maximum: Math.max(...values) } : null];
  }));
  const rewardRanges = Object.fromEntries(resourceKeys.map((key) => {
    const values = endToEndRoutes.map((route) => route.rewards[key]);
    return [key, values.length ? { minimum: Math.min(...values), maximum: Math.max(...values) } : null];
  }));
  const remainingPrerequisiteProfiles = new Map();
  for (const route of endToEndRoutes) {
    const signature = JSON.stringify(route.remainingPrerequisites);
    const profile = remainingPrerequisiteProfiles.get(signature);
    if (profile) profile.routeCount += 1;
    else remainingPrerequisiteProfiles.set(signature, {
      routeCount: 1,
      gates: route.remainingPrerequisites,
    });
  }

  gatedPrerequisiteRouteClosures.push({
    componentId,
    eventKeys: reachableStoryRouteComponents.find((component) =>
      component.componentId === componentId)?.eventKeys ?? [],
    componentDependencies: reachableStoryRouteComponents.find((component) =>
      component.componentId === componentId)?.transitivePrerequisiteComponents ?? [],
    routeCount: endToEndRoutes.length,
    routesTruncated: closureTruncated,
    fullySeedResolvedRouteCount: endToEndRoutes.filter((route) =>
      route.remainingPrerequisites.length === 0).length,
    minimumPlayerLevelRange: endToEndRoutes.length ? {
      minimum: minimumLevel,
      maximum: maximumLevel,
    } : null,
    experienceRangesByRank,
    rewardRanges,
    remainingPrerequisiteProfiles: [...remainingPrerequisiteProfiles.values()],
  });
}

const routeProgressionIssues = reachableStoryRouteComponents.flatMap((component) =>
  component.routeProgressionProfiles.flatMap((profile) => profile.routeIssues.map((issue) => ({
    componentId: component.componentId,
    rootEventKeys: component.rootEventKeys,
    routeCount: profile.routeCount,
    ...issue,
  }))));
const routeDependencyCycles = reachableStoryRouteComponents.filter((component) =>
  component.transitivePrerequisiteComponents.includes(component.componentId))
  .map((component) => ({
    componentId: component.componentId,
    eventKeys: component.eventKeys,
    issue: 'cross-component location prerequisites contain a cycle',
  }));

const summaries = locations.map((location) => {
  const storyEvents = seed.filter((entry) => entry.type === 'event'
    && entry.location_key === location.location_key
    && ['story', 'side_story'].includes(entry.event_type));
  const options = storyEvents.flatMap((event) => (event.choices ?? []).map((choice, index) => ({
    event,
    choice,
    index,
    delta: rewardVector(choice.rewards_override),
  })));
  const baseRewardDefinitionSum = rewardVector();
  for (const event of storyEvents) {
    const base = rewardVector(event.rewards);
    for (const key of resourceKeys) baseRewardDefinitionSum[key] += base[key];
  }

  const choiceDeltaRange = Object.fromEntries(resourceKeys.map((key) => {
    const values = options.map((option) => option.delta[key]);
    return [key, {
      min: values.length ? Math.min(...values) : 0,
      max: values.length ? Math.max(...values) : 0,
    }];
  }));
  const largestPositiveChoiceDelta = Object.fromEntries(resourceKeys.map((key) => {
    const best = options.reduce((current, option) => option.delta[key] > (current?.value ?? 0)
      ? {
        value: option.delta[key],
        eventKey: option.event.event_key,
        choiceIndex: option.index,
        label: option.choice.label,
      }
      : current, null);
    return [key, best];
  }));
  const recommendedRank = location.recommended_rank || 'F';
  const recommendedRankMultiplier = rankMultipliers[recommendedRank] ?? 1;
  const minimumLevel = Math.max(1, numberOrZero(location.min_level) || 1);
  const nextLevelExperienceBand = 100 * (2 * minimumLevel - 1);
  const largestChoiceBaseExperience = largestPositiveChoiceDelta.exp?.value ?? 0;
  const recommendedRankExperienceForLargestChoice = Math.round(
    largestChoiceBaseExperience * recommendedRankMultiplier,
  );

  for (const option of options) {
    const explicitFragmentCost = option.choice.label?.match(/消耗\s*(\d+)\s*(?:个)?碎片/);
    if (explicitFragmentCost) {
      const statedCost = Number(explicitFragmentCost[1]);
      const configuredCost = Math.max(0, -option.delta.storyFragments);
      if (configuredCost !== statedCost) {
        costMismatches.push({
          locationKey: location.location_key,
          eventKey: option.event.event_key,
          choiceIndex: option.index,
          label: option.choice.label,
          statedStoryFragmentCost: statedCost,
          configuredStoryFragmentCost: configuredCost,
        });
      }
    }
  }

  return {
    locationKey: location.location_key,
    locationName: location.name,
    minLevel: minimumLevel,
    recommendedRank,
    recommendedRankMultiplier,
    storyAndSideStoryEventCount: storyEvents.length,
    choiceCount: options.length,
    // This sum covers all listed event definitions; it is not a guaranteed or
    // single-route total because event unlocks and player choices can diverge.
    baseRewardDefinitionSum,
    choiceDeltaRange,
    largestPositiveChoiceDelta,
    experienceSensitivityAtRecommendedRank: {
      largestSingleChoiceBaseExperience: largestChoiceBaseExperience,
      largestSingleChoiceExperienceAfterMultiplier: recommendedRankExperienceForLargestChoice,
      fullLevelExperienceBandFromMinimumLevel: nextLevelExperienceBand,
      shareOfFullBandPercent: Number((100 * recommendedRankExperienceForLargestChoice
        / nextLevelExperienceBand).toFixed(1)),
    },
    optionsWithConfiguredWalletCosts: options.filter((option) =>
      option.delta.coins < 0 || option.delta.storyFragments < 0).length,
  };
});

const report = {
  source: 'springboot-backend/src/main/resources/db/seed_explore.json',
  assumptions: [
    'Includes story and side_story event definitions and their listed choices only.',
    'Choice rewards are additional to the event base rewards in ExploreServiceImpl.',
    'The baseRewardDefinitionSum aggregates every event definition in a location and is not a guaranteed single-route reward.',
    'Experience values are base values before avatar-rank multipliers; items, equipment, title bias, unlocks, and narrative value are not priced.',
    'Recommended rank is used only for an illustrative multiplier sensitivity; the runtime applies the player\'s actual rank.',
    'The cost-text check only validates explicit labels of the form 消耗N碎片; it does not infer costs from narrative prose.',
    'Local gated story-route ranges assume all root events in a component are encountered and every unlocked gated event is completed. End-to-end prerequisite closures additionally include seeded external prerequisite stories and their choice rewards, and recursively combine other gated components when required; prerequisite keys absent from the current seed remain explicit unresolved gates. Rank multipliers and per-award rounding follow ExperienceProgression, but random encounter odds, level progression during play, battle results, items, equipment, and non-prerequisite non-gated reward events are not simulated.',
    'Story reachability is existential per event: it verifies that each seeded story can be reached on some personal route, not that mutually exclusive branches can all be completed in one timeline; level/rank/item/chapter requirements are treated as external progression.',
    'Reads repository seed files only; no database or player data is accessed.',
  ],
  costMismatches,
  routeUnlockMismatches,
  routeConfigMismatches,
  locationGateMismatches,
  locationConfigMismatches,
  storyRiskConfigMismatches,
  unreachableStoryEvents,
  routeComponentIssues,
  routeProgressionIssues,
  routeDependencyCycles,
  gatedClosureIssues,
  gatedPrerequisiteRouteClosures,
  routeUnlockEdgeCount: [...eventsByKey.values()].reduce((count, event) => count
    + (event.choices ?? []).reduce((choiceCount, choice) =>
      choiceCount + (choice.unlock_events ?? []).length, 0), 0),
  reachableStoryRouteComponents,
  locations: summaries,
};

console.log(JSON.stringify(report, null, 2));
if (costMismatches.length > 0 || routeUnlockMismatches.length > 0
    || routeConfigMismatches.length > 0 || locationGateMismatches.length > 0
    || locationConfigMismatches.length > 0 || storyRiskConfigMismatches.length > 0
    || unreachableStoryEvents.length > 0
    || routeComponentIssues.length > 0 || routeProgressionIssues.length > 0
    || routeDependencyCycles.length > 0 || gatedClosureIssues.length > 0) {
  process.exitCode = 1;
}
