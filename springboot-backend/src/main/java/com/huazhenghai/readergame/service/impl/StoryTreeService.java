package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.huazhenghai.readergame.entity.ExplorationEvent;
import com.huazhenghai.readergame.entity.Location;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.ExplorationEventMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper.PlayerRouteSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/** Read-only projection of the current story graph and delayed aggregate choices. */
@Service
public class StoryTreeService {
    private static final Logger LOG = LoggerFactory.getLogger(StoryTreeService.class);
    private static final Duration STATS_TTL = Duration.ofMinutes(7);
    private static final Duration REDIS_RETRY_DELAY = Duration.ofSeconds(30);
    private final ExplorationEventMapper eventMapper;
    private final LocationMapper locationMapper;
    private final PlayerMapper playerMapper;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final byte[] nodeSecret;
    private final String cachePrefix;
    private final AtomicLong lastRedisWarningNanos = new AtomicLong();
    private final AtomicLong redisRetryAfterNanos = new AtomicLong();
    private final AtomicBoolean redisRecoveryProbe = new AtomicBoolean();

    public StoryTreeService(ExplorationEventMapper eventMapper, LocationMapper locationMapper,
            PlayerMapper playerMapper, ObjectMapper json, StringRedisTemplate redis,
            @Value("${app.story-tree.node-secret:${app.jwt.secret}}") String nodeSecret,
            @Value("${app.cache.key-prefix:star-reader}") String cachePrefix) {
        this.eventMapper = eventMapper;
        this.locationMapper = locationMapper;
        this.playerMapper = playerMapper;
        this.json = json;
        this.redis = redis;
        if (nodeSecret == null || nodeSecret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("Story tree node secret must contain at least 32 UTF-8 bytes");
        this.nodeSecret = nodeSecret.getBytes(StandardCharsets.UTF_8);
        this.cachePrefix = cachePrefix == null ? "star-reader" : cachePrefix.replaceAll(":+$", "");
    }

    public Map<String, Object> getStoryTree(Player player) {
        List<ExplorationEvent> all = eventMapper.selectList(new QueryWrapper<ExplorationEvent>()
                .eq("event_type", "story").eq("enabled", 1));
        List<Location> locations = locationMapper.selectList(null);
        Map<String, Location> locationByKey = new HashMap<>();
        for (Location location : locations) locationByKey.put(location.getLocationKey(), location);
        Map<String, ExplorationEvent> byKey = new LinkedHashMap<>();
        Map<String, Map<String, Object>> conditions = new HashMap<>();
        for (ExplorationEvent event : all) {
            Map<String, Object> condition = map(event.getRequiredConditionsJson());
            if (!temporarilyPaused(condition)) {
                byKey.put(event.getEventKey(), event);
                conditions.put(event.getEventKey(), condition);
            }
        }
        Map<String, Object> progress = map(player.getStageProgressJson());
        List<?> decisions = list(player.getDecisionHistoryJson());
        RouteContinuationPolicy.restoreCompletedFirstGrowthTip(progress, decisions);
        List<String> triggered = strings(progress.get("storyEventsTriggered"));
        List<String> triggeredSides = strings(progress.get("sideEventsTriggered"));
        Map<?, ?> pending = progress.get("pendingStorySnapshots") instanceof Map<?, ?> p ? p : Map.of();
        Map<String, Object> stats = map(player.getStatsJson());
        Map<String, Object> flags = map(player.getStoryFlagsJson());
        Function<String, Map<String, Object>> ancestors = conditions::get;
        Map<String, String> states = new HashMap<>();
        boolean continuationAvailable = false;
        for (ExplorationEvent event : byKey.values()) {
            String key = event.getEventKey();
            Map<String, Object> condition = conditions.get(key);
            boolean completed = hasDecision(decisions, key) || triggered.contains(key)
                    && (event.getChoicesJson() == null || list(event.getChoicesJson()).isEmpty());
            String state;
            if (triggered.contains(key) && !completed) state = "pending_choice";
            else if (completed) state = "done";
            else if (ExploreStoryEligibility.choiceHistoryPermanentlyClosesEvent(
                    condition, decisions, triggered, ancestors)) state = "closed";
            else {
                Location location = locationByKey.get(event.getLocationKey());
                boolean lowerBlocked = lowerStoriesBlock(location, all, locationByKey,
                        conditions, triggered, decisions, progress, ancestors);
                state = ExploreCandidatePolicy.canDraw(event, condition, location,
                        location == null ? Map.of() : map(location.getUnlockConditionsJson()),
                        stats, flags, player.getCurrentChapter(), progress, decisions,
                        triggered, triggeredSides, pending, lowerBlocked, ancestors)
                        ? "available" : "locked";
                if ("available".equals(state) && RouteContinuationPolicy.isPending(progress)
                        && RouteContinuationPolicy.allowsStory(progress, condition)) continuationAvailable = true;
            }
            states.put(key, state);
        }
        Object oldTip = progress.get("routePendingEventKey");
        if (RouteContinuationPolicy.isPending(progress) && !continuationAvailable
                && oldTip instanceof String key && "done".equals(states.get(key))) states.put(key, "tip");

        Map<String, String> ids = new HashMap<>();
        for (String key : byKey.keySet()) ids.put(key, nodeId(key));
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> stages = new LinkedHashSet<>();
        for (ExplorationEvent event : byKey.values()) {
            String key = event.getEventKey();
            String state = states.get(key);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("node_id", ids.get(key));
            node.put("state", state);
            node.put("branch", branch(key, conditions, new HashSet<>()));
            node.put("stage_key", event.getStageKey());
            Location location = locationByKey.get(event.getLocationKey());
            if ("done".equals(state) || "pending_choice".equals(state) || "tip".equals(state)) {
                node.put("event_key", key);
                node.put("title", event.getName());
                node.put("location_name", location == null ? null : location.getName());
            } else if ("available".equals(state)) {
                node.put("location_name", location == null ? null : location.getName());
            }
            nodes.add(node);
            stages.add(event.getStageKey());
        }
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (String stage : stages) chapters.add(Map.of("stage_key", stage, "label", chapterLabel(stage)));

        Aggregate aggregate = aggregate(all, byKey);
        List<Map<String, Object>> edges = buildEdges(byKey, conditions, ids, states, decisions, aggregate);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chapters", chapters);
        result.put("nodes", nodes);
        result.put("edges", edges);
        Map<String, Object> continuation = RouteContinuationPolicy.response(progress, continuationAvailable);
        if (continuation.get("event_key") == null) continuation.put("event_key", NullNode.getInstance());
        result.put("route_continuation", continuation);
        result.put("route_stats", routeStats(decisions, byKey, conditions, aggregate));
        return result;
    }

    public Map<String, Object> getRouteCodex(Player player) {
        List<ExplorationEvent> all = eventMapper.selectList(new QueryWrapper<ExplorationEvent>()
                .eq("event_type", "story").eq("enabled", 1));
        Map<String, ExplorationEvent> byKey = new LinkedHashMap<>();
        Map<String, Map<String, Object>> conditions = new HashMap<>();
        for (ExplorationEvent event : all) {
            Map<String, Object> condition = map(event.getRequiredConditionsJson());
            if (!temporarilyPaused(condition)) {
                byKey.put(event.getEventKey(), event);
                conditions.put(event.getEventKey(), condition);
            }
        }
        Map<String, Set<Integer>> candidates = RouteCodexPolicy.tips(byKey, json);
        Map<String, Object> progress = map(player.getStageProgressJson());
        List<?> decisions = list(player.getDecisionHistoryJson());
        Set<String> reached = RouteCodexPolicy.reached(progress, decisions, candidates);
        Aggregate aggregate = aggregate(all, byKey);
        String current = RouteContinuationPolicy.isPending(progress)
                && progress.get("routePendingEventKey") instanceof String key && candidates.containsKey(key)
                ? nodeId(key) : null;
        List<Map<String, Object>> tips = new ArrayList<>();
        int sample = aggregate.codexEligible();
        for (String key : candidates.keySet()) {
            ExplorationEvent event = byKey.get(key);
            boolean seen = reached.contains(key);
            Map<String, Object> tip = new LinkedHashMap<>();
            tip.put("tip_id", nodeId(key));
            tip.put("reached", seen);
            if (seen) {
                tip.put("branch", branch(key, conditions, new HashSet<>()));
                tip.put("title", event.getName());
                tip.put("chapter_label", chapterLabel(event.getStageKey()));
            }
            Map<String, Object> tipStats = stat(sample, aggregate.codexTipCounts().getOrDefault(key, 0));
            tip.put("stats", tipStats == null ? NullNode.getInstance() : tipStats);
            tips.add(tip);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("growth", RouteCodexPolicy.growth());
        result.put("tips", tips);
        result.put("my_tip_id", current == null ? NullNode.getInstance() : current);
        result.put("total_tips", tips.size());
        return result;
    }

    private boolean lowerStoriesBlock(Location location, List<ExplorationEvent> all,
            Map<String, Location> locations, Map<String, Map<String, Object>> conditions,
            List<String> triggered, List<?> decisions, Map<String, Object> progress,
            Function<String, Map<String, Object>> ancestors) {
        if (location == null || location.getMinLevel() == null) return false;
        for (ExplorationEvent lower : all) {
            Location lowerLocation = locations.get(lower.getLocationKey());
            if (lowerLocation == null || lowerLocation.getMinLevel() == null
                    || lowerLocation.getMinLevel() >= location.getMinLevel()) continue;
            Map<String, Object> condition = conditions.get(lower.getEventKey());
            if (condition != null && ExploreStoryEligibility.isUnfinishedStory(lower.getEventKey(),
                    condition, triggered, decisions, ancestors, progress)) return true;
        }
        return false;
    }

    private List<Map<String, Object>> buildEdges(Map<String, ExplorationEvent> events,
            Map<String, Map<String, Object>> conditions, Map<String, String> ids,
            Map<String, String> states, List<?> decisions, Aggregate aggregate) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (ExplorationEvent target : events.values()) {
            Map<String, Object> c = conditions.get(target.getEventKey());
            Object unlockers = c.get("choice_unlockers");
            if (unlockers instanceof List<?> alternatives) {
                for (int i = 0; i < alternatives.size(); i++) {
                    if (alternatives.get(i) instanceof Map<?, ?> gate) addChoiceEdges(edges, gate,
                            "or:" + i, target.getEventKey(), events, ids, states, decisions, aggregate);
                }
            }
            Object requiredEvents = c.get("required_events");
            if (requiredEvents instanceof List<?> requirements) {
                for (Object source : requirements) if (source instanceof String key && events.containsKey(key))
                    addEdge(edges, key, target.getEventKey(), "and", null, events, ids, states, decisions, aggregate);
            }
            Object requiredChoices = c.get("required_choices");
            if (requiredChoices instanceof List<?> requirements) {
                for (Object requirement : requirements) if (requirement instanceof Map<?, ?> gate)
                    addChoiceEdges(edges, gate, "and", target.getEventKey(), events, ids, states, decisions, aggregate);
            }
        }
        return edges;
    }

    private void addChoiceEdges(List<Map<String, Object>> edges, Map<?, ?> gate, String group,
            String target, Map<String, ExplorationEvent> events, Map<String, String> ids,
            Map<String, String> states, List<?> decisions, Aggregate aggregate) {
        if (!(gate.get("event_key") instanceof String source) || !events.containsKey(source)
                || !(gate.get("choice_indices") instanceof List<?> indices)) return;
        for (Object value : indices) if (value instanceof Number number)
            addEdge(edges, source, target, group, number.intValue(), events, ids, states, decisions, aggregate);
    }

    private void addEdge(List<Map<String, Object>> edges, String source, String target, String group,
            Integer index, Map<String, ExplorationEvent> events, Map<String, String> ids,
            Map<String, String> states, List<?> decisions, Aggregate aggregate) {
        boolean seen = "done".equals(states.get(source)) || "tip".equals(states.get(source));
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("from", ids.get(source));
        edge.put("to", ids.get(target));
        edge.put("gate", group);
        edge.put("choice_index", index);
        edge.put("choice_label", seen && index != null ? choiceLabel(events.get(source), index) : null);
        edge.put("taken", index == null ? seen : chosen(decisions, source, index));
        edge.put("stats", seen && index != null ? choiceStats(aggregate, source, index) : null);
        edges.add(edge);
    }

    private Map<String, Object> choiceStats(Aggregate aggregate, String key, int index) {
        int sample = aggregate.denominators().getOrDefault(key, 0);
        return stat(sample, aggregate.choices().getOrDefault(key + ":" + index, 0));
    }

    private Map<String, Object> routeStats(List<?> decisions, Map<String, ExplorationEvent> events,
            Map<String, Map<String, Object>> conditions, Aggregate aggregate) {
        List<String> route = criticalChoices(decisions, events, conditions);
        if (route.isEmpty()) return null;
        int depth = route.size();
        return stat(aggregate.routeDenominators().getOrDefault(depth, 0),
                aggregate.routes().getOrDefault(routeKey(route), 0));
    }

    private Map<String, Object> stat(int sample, int count) {
        if (sample < 20) return null;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("percent", (int) Math.round(count * 100.0 / sample));
        value.put("sample_band", sample >= 500 ? "500+" : sample >= 100 ? "100+"
                : sample >= 50 ? "50+" : "20+");
        return value;
    }

    private Aggregate aggregate(List<ExplorationEvent> all, Map<String, ExplorationEvent> events) {
        String cacheKey = cachePrefix + ":cache:v3:story-tree-stats:" + contentVersion(all);
        int redisAccess = redisAccessMode();
        boolean readFailed = false;
        try {
            if (redisAccess != 0) try {
                String cached = redis.opsForValue().get(cacheKey);
                if (cached != null) {
                    Aggregate value = json.readValue(cached, Aggregate.class);
                    if (redisAccess == 2) redisRetryAfterNanos.set(0);
                    return value;
                }
            } catch (Exception e) {
                readFailed = true;
                redisFailed("read", e);
            }
            Map<String, Integer> denominators = new HashMap<>();
            Map<String, Integer> choices = new HashMap<>();
            Map<Integer, Integer> routeDenominators = new HashMap<>();
            Map<String, Integer> routes = new HashMap<>();
            Map<String, Integer> codexTipCounts = new HashMap<>();
            int codexEligible = 0;
            Map<String, Map<String, Object>> conditions = conditionsFor(events);
            Set<String> forks = criticalSources(events, conditions);
            Map<String, Set<Integer>> tipCandidates = RouteCodexPolicy.tips(events, json);
            List<PlayerRouteSnapshot> histories = playerMapper.selectPlayerRoutesForStoryTree();
            if (histories != null) for (PlayerRouteSnapshot history : histories) {
                List<?> decisions = list(history.getDecisionHistoryJson());
                Set<String> counted = new HashSet<>();
                for (Object value : decisions) {
                    if (!(value instanceof Map<?, ?> decision)) continue;
                    String key = decisionKey(decision);
                    Integer index = decisionIndex(decision);
                    if (!events.containsKey(key) || index == null || !counted.add(key)) continue;
                    denominators.merge(key, 1, Integer::sum);
                    choices.merge(key + ":" + index, 1, Integer::sum);
                }
                List<String> route = criticalChoices(decisions, forks);
                if (!route.isEmpty()) {
                    routeDenominators.merge(route.size(), 1, Integer::sum);
                    routes.merge(routeKey(route), 1, Integer::sum);
                }
                // The first route fork is station_first_trace. Count each eligible character once.
                if (counted.contains("station_first_trace")) {
                    codexEligible++;
                    Set<String> reached = RouteCodexPolicy.reached(
                            map(history.getStageProgressJson()), decisions, tipCandidates);
                    for (String key : reached) codexTipCounts.merge(key, 1, Integer::sum);
                }
            }
            Aggregate aggregate = new Aggregate(denominators, choices, routeDenominators, routes,
                    codexEligible, codexTipCounts);
            if (redisAccess != 0 && !readFailed) try {
                redis.opsForValue().set(cacheKey, json.writeValueAsString(aggregate), STATS_TTL);
                if (redisAccess == 2) redisRetryAfterNanos.set(0);
            } catch (Exception e) { redisFailed("write", e); }
            return aggregate;
        } finally {
            if (redisAccess == 2) redisRecoveryProbe.set(false);
        }
    }

    /** 0 skips Redis, 1 is normal access, 2 holds the sole recovery probe. */
    private int redisAccessMode() {
        long retryAfter = redisRetryAfterNanos.get();
        if (retryAfter == 0) return 1;
        if (System.nanoTime() - retryAfter < 0) return 0;
        return redisRecoveryProbe.compareAndSet(false, true) ? 2 : 0;
    }

    private void redisFailed(String operation, Exception error) {
        redisRetryAfterNanos.set(System.nanoTime() + REDIS_RETRY_DELAY.toNanos());
        warnRedis(operation, error);
    }

    private void warnRedis(String operation, Exception error) {
        long now = System.nanoTime();
        long previous = lastRedisWarningNanos.get();
        if ((previous == 0 || now - previous >= Duration.ofMinutes(1).toNanos())
                && lastRedisWarningNanos.compareAndSet(previous, now))
            LOG.warn("Story tree Redis {} unavailable; serving uncached statistics", operation, error);
    }

    private Map<String, Map<String, Object>> conditionsFor(Map<String, ExplorationEvent> events) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (ExplorationEvent event : events.values()) result.put(event.getEventKey(), map(event.getRequiredConditionsJson()));
        return result;
    }

    private List<String> criticalChoices(List<?> decisions, Map<String, ExplorationEvent> events,
            Map<String, Map<String, Object>> conditions) {
        return criticalChoices(decisions, criticalSources(events, conditions));
    }

    private Set<String> criticalSources(Map<String, ExplorationEvent> events,
            Map<String, Map<String, Object>> conditions) {
        Map<String, Map<Integer, Set<String>>> targets = new HashMap<>();
        for (ExplorationEvent target : events.values()) {
            Object unlockers = conditions.get(target.getEventKey()).get("choice_unlockers");
            if (!(unlockers instanceof List<?> gates)) continue;
            for (Object value : gates) if (value instanceof Map<?, ?> gate
                    && gate.get("event_key") instanceof String source && events.containsKey(source)
                    && gate.get("choice_indices") instanceof List<?> indices)
                for (Object index : indices) if (index instanceof Number n)
                    targets.computeIfAbsent(source, k -> new HashMap<>())
                            .computeIfAbsent(n.intValue(), k -> new HashSet<>()).add(target.getEventKey());
        }
        Set<String> forks = new HashSet<>();
        for (var entry : targets.entrySet()) {
            Set<String> destinations = new HashSet<>();
            entry.getValue().values().forEach(destinations::addAll);
            if (destinations.size() >= 2) forks.add(entry.getKey());
        }
        return forks;
    }

    private List<String> criticalChoices(List<?> decisions, Set<String> forks) {
        List<String> route = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object value : decisions) if (value instanceof Map<?, ?> decision) {
            String key = decisionKey(decision);
            Integer index = decisionIndex(decision);
            if (forks.contains(key) && index != null && seen.add(key)) route.add(key + ":" + index);
        }
        return route;
    }

    private String routeKey(List<String> choices) { return choices.size() + "|" + String.join("|", choices); }

    private String contentVersion(List<ExplorationEvent> events) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<ExplorationEvent> ordered = new ArrayList<>(events);
            ordered.sort((a, b) -> a.getEventKey().compareTo(b.getEventKey()));
            for (ExplorationEvent event : ordered) {
                String content = event.getEventKey() + "|" + event.getRequiredConditionsJson() + "|"
                        + event.getChoicesJson() + "|" + event.getEnabled();
                digest.update(content.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch (Exception e) { throw new IllegalStateException("Cannot hash story content", e); }
    }

    private String nodeId(String eventKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(nodeSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("story-tree:v1:" + eventKey).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot create story node id", e); }
    }

    private boolean temporarilyPaused(Map<String, Object> conditions) {
        Object requirements = conditions.get("required_choices");
        if (!(requirements instanceof List<?> list)) return false;
        Map<String, Set<Integer>> allowed = new HashMap<>();
        for (Object value : list) if (value instanceof Map<?, ?> gate
                && gate.get("event_key") instanceof String source
                && gate.get("choice_indices") instanceof List<?> indices) {
            Set<Integer> current = new HashSet<>();
            for (Object index : indices) if (index instanceof Number n) current.add(n.intValue());
            if (allowed.containsKey(source)) allowed.get(source).retainAll(current);
            else allowed.put(source, current);
            if (allowed.get(source).isEmpty()) return true;
        }
        return false;
    }

    private String branch(String key, Map<String, Map<String, Object>> conditions, Set<String> visiting) {
        if ("station_first_trace".equals(key)) return "root";
        if (!visiting.add(key)) return "root";
        try {
            Map<String, Object> c = conditions.getOrDefault(key, Map.of());
            Object requirements = c.get("required_choices");
            if (requirements instanceof List<?> list) for (Object value : list)
                if (value instanceof Map<?, ?> gate && "station_first_trace".equals(gate.get("event_key"))
                        && gate.get("choice_indices") instanceof List<?> indices) {
                    if (indices.contains(0)) return "companion";
                    if (indices.contains(1)) return "nameless";
                }
            Object alternatives = c.get("choice_unlockers");
            if (alternatives instanceof List<?> list) for (Object value : list)
                if (value instanceof Map<?, ?> gate && gate.get("event_key") instanceof String source) {
                    if ("station_first_trace".equals(source) && gate.get("choice_indices") instanceof List<?> indices) {
                        if (indices.contains(0)) return "companion";
                        if (indices.contains(1)) return "nameless";
                    }
                    String inherited = branch(source, conditions, visiting);
                    if (!"root".equals(inherited)) return inherited;
                }
            Object requiredEvents = c.get("required_events");
            if (requiredEvents instanceof List<?> list) for (Object value : list)
                if (value instanceof String source) {
                    String inherited = branch(source, conditions, visiting);
                    if (!"root".equals(inherited)) return inherited;
                }
            return "root";
        } finally { visiting.remove(key); }
    }

    private String chapterLabel(String stage) {
        if (stage == null) return "";
        int pos = stage.lastIndexOf("_ch");
        if (pos < 0) return stage;
        try {
            int n = Integer.parseInt(stage.substring(pos + 3));
            return "第" + switch (n) { case 1 -> "一"; case 2 -> "二"; case 3 -> "三";
                case 4 -> "四"; case 5 -> "五"; default -> n; } + "章";
        } catch (NumberFormatException e) { return stage; }
    }

    private String choiceLabel(ExplorationEvent event, int index) {
        List<?> choices = list(event.getChoicesJson());
        if (index < 0 || index >= choices.size() || !(choices.get(index) instanceof Map<?, ?> choice)) return null;
        return choice.get("label") instanceof String label ? label : null;
    }

    private boolean hasDecision(List<?> decisions, String key) {
        for (Object value : decisions) if (value instanceof Map<?, ?> decision && key.equals(decisionKey(decision))) return true;
        return false;
    }

    private boolean chosen(List<?> decisions, String key, int index) {
        for (Object value : decisions) if (value instanceof Map<?, ?> decision
                && key.equals(decisionKey(decision)) && Integer.valueOf(index).equals(decisionIndex(decision))) return true;
        return false;
    }

    private String decisionKey(Map<?, ?> decision) {
        Object key = decision.containsKey("event_key") ? decision.get("event_key") : decision.get("eventKey");
        return key instanceof String s ? s : null;
    }

    private Integer decisionIndex(Map<?, ?> decision) {
        Object index = decision.containsKey("choice_index") ? decision.get("choice_index") : decision.get("choiceIndex");
        return index instanceof Number n ? n.intValue() : null;
    }

    private List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) for (Object item : list) if (item instanceof String s) result.add(s);
        return result;
    }

    private Map<String, Object> map(String source) {
        if (source == null || source.isBlank()) return new LinkedHashMap<>();
        try { return json.readValue(source, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private List<?> list(String source) {
        if (source == null || source.isBlank()) return Collections.emptyList();
        try { return json.readValue(source, List.class); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    public record Aggregate(Map<String, Integer> denominators, Map<String, Integer> choices,
                            Map<Integer, Integer> routeDenominators, Map<String, Integer> routes,
                            int codexEligible, Map<String, Integer> codexTipCounts) { }
}
