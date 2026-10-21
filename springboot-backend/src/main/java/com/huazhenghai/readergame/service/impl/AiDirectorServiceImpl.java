package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.entity.PkRecord;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.entity.WorldBoss;
import com.huazhenghai.readergame.mapper.PkRecordMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import com.huazhenghai.readergame.service.AiDirectorService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.WorldlineSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AiDirectorServiceImpl implements AiDirectorService {

    private static final Logger log = LoggerFactory.getLogger(AiDirectorServiceImpl.class);

    private final WorldlineService worldlineService;
    private final OnlinePlayerService onlinePlayerService;
    private final PlayerMapper playerMapper;
    private final WorldBossMapper worldBossMapper;
    private final PkRecordMapper pkRecordMapper;
    private final PlayerLogMapper playerLogMapper;
    private final ObjectMapper objectMapper;

    private static final int RECENT_EXPLORATION_DAYS = 30;
    private static final int RECENT_EXPLORATION_LOG_LIMIT = 5000;

    @Value("${app.ai-director.llm-api-key:}")
    private String llmApiKey;

    @Value("${app.ai-director.llm-provider:openai}")
    private String llmProvider;

    @Value("${app.ai-director.llm-model:gpt-4o}")
    private String llmModel;

    @Value("${app.ai-director.llm-base-url:}")
    private String llmBaseUrl;

    public AiDirectorServiceImpl(WorldlineService worldlineService, OnlinePlayerService onlinePlayerService,
                                 PlayerMapper playerMapper, WorldBossMapper worldBossMapper,
                                 PkRecordMapper pkRecordMapper, PlayerLogMapper playerLogMapper,
                                 ObjectMapper objectMapper) {
        this.worldlineService = worldlineService;
        this.onlinePlayerService = onlinePlayerService;
        this.playerMapper = playerMapper;
        this.worldBossMapper = worldBossMapper;
        this.pkRecordMapper = pkRecordMapper;
        this.playerLogMapper = playerLogMapper;
        this.objectMapper = objectMapper;
    }

    // ─── allowed types from Node.js BroadcastService ───
    private static final List<String> ALLOWED_EVENT_TYPES = List.of(
            "exploration_drive", "world_boss", "pk_tournament", "disaster",
            "story_hunt", "opportunity_rain", "broadcast_station_cleanup",
            "broadcast_pk_spark");

    private static final List<String> ALLOWED_CONTRIBUTION_TYPES = List.of(
            "explore_location", "trigger_story", "defeat_boss", "kill_monster", "win_pk");

    @Override
    public Map<String, Object> generateBroadcastDraft() {
        Map<String, Object> state = buildWorldState();
        if (state == null) return null;

        if (llmApiKey != null && !llmApiKey.isBlank()) {
            try {
                String response = callLLM(buildSystemPrompt(state));
                Map<String, Object> parsed = parseAIResponse(response);
                if (parsed != null) {
                    parsed.put("generatedBy", "ai:" + llmProvider + ":" + llmModel);
                    parsed.put("worldStateSnapshot", buildWorldStateSnapshot(state));
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("LLM API call failed, using fallback: {}", e.getMessage());
            }
        }

        Map<String, Object> draft = fallbackBroadcastGenerator(state);
        if (draft != null) draft.put("worldStateSnapshot", buildWorldStateSnapshot(state));
        return draft;
    }

    @Override
    public Map<String, Object> fallbackBroadcastGenerator(Map<String, Object> state) {
        int activePlayers = toInt(state.get("activePlayers"), 1);
        int averageLevel = toInt(state.get("averageLevel"), 1);
        int totalBossKills = toInt(state.get("totalBossKills"), 0);
        double worldLineShift = toDouble(state.get("worldlineShift"), 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> pkStats = (Map<String, Object>) state.getOrDefault("recentPkStats", Map.of());
        int pk24h = toInt(pkStats.get("last24h"), 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locationStats = (List<Map<String, Object>>) state.getOrDefault("locationExplorationStats", List.of());

        // Rule 1: Low level players → exploration drive
        if (averageLevel < 5 && activePlayers >= 1) {
            return buildEvent("exploration_drive",
                    "新星探索令：最初的足迹",
                    "星座频道检测到大量新手化身的活跃信号。为了鼓励探索，星之流发布全服探索令。在" + (averageLevel < 3 ? "废墟车站" : "废弃商场") + "留下你的足迹，向星座们证明你的价值。",
                    60, Map.of("min_level", 1),
                    List.of(
                            Map.of("type", "explore_location", "target", Math.max(10, activePlayers * 3), "label", "全服累计探索", "score_per_unit", 1),
                            Map.of("type", "trigger_story", "target", Math.max(3, activePlayers), "label", "触发剧情事件", "score_per_unit", 3)),
                    Map.of("coins", 150, "storyFragments", 3),
                    Map.of("coins", 400, "storyFragments", 8),
                    Map.of("coins", 600, "storyFragments", 12),
                    "当前平均等级 " + averageLevel + "，新手居多，适合探索驱动任务");
        }

        // Rule 2: Low boss kills → world boss
        if (totalBossKills < 5 && activePlayers >= 2) {
            return buildEvent("world_boss",
                    "星座通缉令：混沌残影",
                    "星座频道捕捉到一个不安定的残影信号——某个剧本残留的守护者正在集结力量。星座们发起了联合通缉令。所有化身均可参与讨伐。",
                    90, Map.of("min_level", 2),
                    List.of(
                            Map.of("type", "defeat_boss", "target", 1, "label", "击败Boss", "score_per_unit", 10),
                            Map.of("type", "kill_monster", "target", Math.max(15, activePlayers * 5), "label", "讨伐怪物", "score_per_unit", 1)),
                    Map.of("coins", 300, "storyFragments", 15),
                    Map.of("coins", 600, "storyFragments", 35),
                    Map.of("coins", 1200, "storyFragments", 25),
                    "Boss击杀数仅 " + totalBossKills + "，适合发布讨伐任务");
        }

        // Rule 3: PK active → PK tournament
        if (pk24h >= 5) {
            return buildEvent("pk_tournament",
                    "星座竞技场：化身对决",
                    "星座频道充满了好战的星座——它们渴望看到化身之间的对抗。竞技场已经搭建完毕。这是赢得星座赞助最快捷的方式。你敢站上擂台吗？",
                    45, Map.of("min_level", 3),
                    List.of(
                            Map.of("type", "win_pk", "target", Math.max(5, (int) (pk24h * 1.5)), "label", "PK获胜", "score_per_unit", 5)),
                    Map.of("coins", 200, "storyFragments", 3),
                    Map.of("coins", 500, "storyFragments", 10),
                    Map.of("coins", 1000, "storyFragments", 20),
                    "最近24小时PK活跃（" + pk24h + "次），适合举办PK锦标赛");
        }

        // Rule 4: High worldline shift → disaster
        if (worldLineShift > 3) {
            return buildEvent("disaster",
                    "星之流警报：世界线污染灾厄",
                    "星之流监测到世界线偏移已达到 " + String.format("%.1f", worldLineShift) + "。星座们发出了污染预警——某种不属于任何已知剧本的力量正在渗透。所有化身必须联合抵抗，否则世界线的编织将出现不可逆转的断裂。",
                    120, Map.of("min_level", 3),
                    List.of(
                            Map.of("type", "explore_location", "target", Math.max(30, activePlayers * 5), "label", "全服探索净化", "score_per_unit", 1),
                            Map.of("type", "trigger_story", "target", Math.max(5, activePlayers), "label", "触发剧情事件", "score_per_unit", 3)),
                    Map.of("coins", 400, "storyFragments", 18),
                    Map.of("coins", 800, "storyFragments", 40),
                    Map.of("coins", 1500, "storyFragments", 30),
                    "世界线偏移 " + String.format("%.1f", worldLineShift) + " 偏高，需要发布净化灾厄任务",
                    Map.of("worldLineShift", 1));
        }

        // Rule 5: Few locations explored → story hunt
        int totalExplore = locationStats.stream().mapToInt(l -> toInt(l.get("c"), 0)).sum();
        if (locationStats.size() <= 2 && totalExplore > 10) {
            return buildEvent("story_hunt",
                    "失落的叙述：剧情狩猎",
                    "星座们发现故事的流向过于集中。多道隐藏的叙述被冷落了——它们正在从星之流的记忆中褪色。时间不多了。在被彻底遗忘之前，找到那些被忽略的场景，激活沉睡的篇章。",
                    75, Map.of("min_level", 1),
                    List.of(
                            Map.of("type", "explore_location", "target", Math.max(20, activePlayers * 4), "label", "全服探索", "score_per_unit", 1),
                            Map.of("type", "trigger_story", "target", Math.max(4, (int) (activePlayers * 1.5)), "label", "触发剧情", "score_per_unit", 5)),
                    Map.of("coins", 250, "storyFragments", 5),
                    Map.of("coins", 500, "storyFragments", 22),
                    Map.of("coins", 1000, "storyFragments", 20),
                    "仅探索了 " + locationStats.size() + " 个地图，适合发布剧情狩猎任务");
        }

        // Rule 7: Default — opportunity rain
        return buildEvent("opportunity_rain",
                "星流恩赐：机遇之雨",
                "星之流的星座们感到无聊，决定来一场机遇之雨——这是最古老的游戏传统。在接下来的时间里，所有化身在探索时都有更高的奖励倍率。不要错过星座的慷慨——它从来不会持续太久。",
                60, Map.of("min_level", 1),
                List.of(
                        Map.of("type", "explore_location", "target", Math.max(20, activePlayers * 5), "label", "全服探索", "score_per_unit", 1),
                        Map.of("type", "trigger_story", "target", Math.max(3, activePlayers), "label", "触发剧情", "score_per_unit", 3),
                        Map.of("type", "kill_monster", "target", Math.max(10, activePlayers * 3), "label", "击杀怪物", "score_per_unit", 1)),
                Map.of("coins", 300, "storyFragments", 5),
                Map.of("coins", 500, "storyFragments", 10),
                Map.of("coins", 800, "storyFragments", 15),
                "默认机遇放送任务");
    }

    // ─── world state assembly ───

    private Map<String, Object> buildWorldState() {
        Map<String, Object> state = new LinkedHashMap<>();
        try {
            long totalPlayers = playerMapper.selectCount(null);
            int totalLevel = 0;
            int playersWithLevel = 0;
            for (String statsJson : playerMapper.selectStatsJsonForWorldAggregation()) {
                try {
                    int level = objectMapper.readTree(statsJson).path("level").asInt(0);
                    if (level > 0) {
                        totalLevel = Math.addExact(totalLevel, level);
                        playersWithLevel++;
                    }
                } catch (Exception malformedStats) {
                    log.debug("Skipping malformed player stats in aggregate");
                }
            }

            QueryWrapper<WorldBoss> killedBosses = new QueryWrapper<>();
            killedBosses.isNotNull("killed_at");
            long totalBossKills = worldBossMapper.selectCount(killedBosses);

            QueryWrapper<PkRecord> recentPk = new QueryWrapper<>();
            recentPk.ge("created_at", LocalDateTime.now().minusHours(24));
            long pkCount24h = pkRecordMapper.selectCount(recentPk);

            state.put("activePlayers", Math.max(0, onlinePlayerService.getOnlineSummary().getOnlineCount()));
            state.put("averageLevel", playersWithLevel == 0 ? 1 : Math.round((float) totalLevel / playersWithLevel));
            state.put("totalPlayers", totalPlayers);
            state.put("totalBossKills", totalBossKills);
            state.put("worldlineShift", worldlineService.getWorldlineSummary().getWorldlineShift());
            state.put("recentPkStats", Map.of("last24h", pkCount24h));
            state.put("locationExplorationStats", getRecentExplorationLocationStats());
        } catch (Exception e) {
            log.warn("Could not build an authoritative public-world snapshot; skipping AI broadcast", e);
            return null;
        }
        return state;
    }

    private List<Map<String, Object>> getRecentExplorationLocationStats() {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.in("type", List.of("exploration", "story"))
                .ge("created_at", LocalDateTime.now().minusDays(RECENT_EXPLORATION_DAYS))
                .orderByDesc("id")
                .last("LIMIT " + RECENT_EXPLORATION_LOG_LIMIT);
        List<PlayerLog> logs = playerLogMapper.selectList(query);
        if (logs == null || logs.isEmpty()) return List.of();

        Map<String, Integer> countByLocation = new HashMap<>();
        for (PlayerLog logEntry : logs) {
            try {
                Map<String, Object> metadata = objectMapper.readValue(logEntry.getMetadataJson(),
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                if (!"exploration_result".equals(metadata.get("kind"))) continue;
                Object location = metadata.get("requestLocationKey");
                if (location instanceof String locationKey && !locationKey.isBlank()) {
                    countByLocation.merge(locationKey, 1, Integer::sum);
                }
            } catch (Exception malformedMetadata) {
                // Legacy or non-exploration logs do not participate in the aggregate.
            }
        }

        return countByLocation.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> Map.<String, Object>of("location_key", entry.getKey(), "c", entry.getValue()))
                .toList();
    }

    private Map<String, Object> buildWorldStateSnapshot(Map<String, Object> state) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("activePlayers", state.getOrDefault("activePlayers", 0));
        snapshot.put("totalPlayers", state.getOrDefault("totalPlayers", 0));
        snapshot.put("averageLevel", state.getOrDefault("averageLevel", 1));
        snapshot.put("totalBossKills", state.getOrDefault("totalBossKills", 0));
        snapshot.put("recentPkStats", state.getOrDefault("recentPkStats", Map.of()));
        snapshot.put("locationExplorationStats", state.getOrDefault("locationExplorationStats", List.of()));
        snapshot.put("worldlineShift", state.getOrDefault("worldlineShift", 0));
        snapshot.put("timestamp", LocalDateTime.now().toString());
        return snapshot;
    }

    // ─── LLM integration ───

    private String buildSystemPrompt(Map<String, Object> state) {
        return "你是星之流（Star Stream）的星座广播系统。你的职责是观察当前世界状态，设计并发布一个临时的全服大任务（星流放送）。\n\n" +
                "## 世界状态\n" + toJson(state) + "\n\n" +
                "## 任务类型 (eventType)\n允许: " + String.join(", ", ALLOWED_EVENT_TYPES) + "\n\n" +
                "## 贡献类型 (objective.type)\n允许: " + String.join(", ", ALLOWED_CONTRIBUTION_TYPES) + "\n\n" +
                "## 约束\n" +
                "1. eventType 必须在允许列表中\n" +
                "2. durationMinutes 必须在 15-180 之间\n" +
                "3. 每个 objective 必须有 type 和 label(中文描述)\n" +
                "3a. objective.type 只能是 explore_location(完成探索)、trigger_story(触发个人故事事件)、kill_monster(战斗胜利击杀普通怪物)、defeat_boss(击破世界首领)、win_pk(PK胜利)；每种类型最多出现一次\n" +
                "3b. target 表示该类有效行动次数；不要要求无法由这些行动推进的目标；score_per_unit 是该目标对参与者贡献分的权重\n" +
                "4. 不允许包含外部网站链接\n" +
                "5. 任务必须能被玩家参与和完成\n" +
                "6. description 用中文，2000 字符以内\n" +
                "7. reason 用中文解释为什么选择这个任务\n" +
                "8. 只能依据上面的公开聚合数据；不得推断或使用任何玩家的剧情选择、个人旗标、路线或私人阶段进度来改变公共事件\n\n" +
                "## 输出格式\n请只输出以下 JSON，不要输出任何其他文字：\n\n" +
                "{\n  \"eventType\": \"exploration_drive\",\n  \"title\": \"中文标题\",\n  \"description\": \"中文描述\",\n  \"durationMinutes\": 60,\n  \"objectives\": [{\"type\": \"explore_location\", \"target\": 50, \"label\": \"全服累计探索 50 次\", \"score_per_unit\": 1}],\n  \"rewards\": {\"participation\": {\"coins\": 200, \"storyFragments\": 5}, \"completion\": {\"coins\": 500, \"storyFragments\": 10}, \"ranking\": {\"coins\": 800, \"storyFragments\": 15}},\n  \"reason\": \"...\"\n}";
    }

    private String callLLM(String systemPrompt) throws Exception {
        String url = llmBaseUrl;
        if (url == null || url.isBlank()) {
            url = "https://api.openai.com/v1/chat/completions";
        }
        if (!url.contains("/chat/completions")) {
            url = url.replaceAll("/$", "") + "/v1/chat/completions";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a JSON-only API. Output only valid JSON, no markdown, no explanation."),
                Map.of("role", "user", "content", systemPrompt)));
        body.put("temperature", 0.8);
        body.put("max_tokens", 2000);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode());
        }

        Map<String, Object> data = objectMapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        }
        return "";
    }

    private Map<String, Object> parseAIResponse(String text) {
        if (text == null || text.isBlank()) return null;

        try { return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {}); } catch (Exception ignored) {}

        // Try extracting from markdown code block
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```").matcher(text);
        if (m.find()) {
            try { return objectMapper.readValue(m.group(1).trim(), new TypeReference<Map<String, Object>>() {}); } catch (Exception ignored) {}
        }

        // Try first { to last }
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first >= 0 && last > first) {
            try { return objectMapper.readValue(text.substring(first, last + 1), new TypeReference<Map<String, Object>>() {}); } catch (Exception ignored) {}
        }

        return null;
    }

    // ─── event builder ───

    private Map<String, Object> buildEvent(String eventType, String title, String description,
                                            int durationMinutes, Map<String, Object> requirements,
                                            List<Map<String, Object>> objectives,
                                            Map<String, Object> participation, Map<String, Object> completion,
                                            Map<String, Object> ranking, String reason) {
        return buildEvent(eventType, title, description, durationMinutes, requirements, objectives,
                participation, completion, ranking, reason, Map.of());
    }

    private Map<String, Object> buildEvent(String eventType, String title, String description,
                                            int durationMinutes, Map<String, Object> requirements,
                                            List<Map<String, Object>> objectives,
                                            Map<String, Object> participation, Map<String, Object> completion,
                                            Map<String, Object> ranking, String reason,
                                            Map<String, Object> failurePenalty) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("title", title);
        event.put("description", description);
        event.put("durationMinutes", durationMinutes);
        event.put("requirements", requirements);
        event.put("objectives", objectives);
        event.put("rewards", Map.of("participation", participation, "completion", completion, "ranking", ranking));
        event.put("failurePenalty", failurePenalty);
        event.put("reason", reason);
        event.put("generatedBy", "fallback");
        return event;
    }

    // ─── utils ───

    private int toInt(Object val, int def) {
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private double toDouble(Object val, double def) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return def;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }
}
