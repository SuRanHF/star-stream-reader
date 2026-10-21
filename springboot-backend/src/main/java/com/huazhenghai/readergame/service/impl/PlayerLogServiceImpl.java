package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.vo.LogEntry;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlayerLogServiceImpl implements PlayerLogService {

    private static final int PENDING_BATTLE_PAGE_SIZE = 100;
    private static final List<String> RECEIPT_LOG_TYPES = List.of(
            "exploration", "story", "battle", "milestone", "info");

    private final PlayerLogMapper playerLogMapper;
    private final BattleLogMapper battleLogMapper;
    private final ObjectMapper objectMapper;

    public PlayerLogServiceImpl(PlayerLogMapper playerLogMapper, BattleLogMapper battleLogMapper,
                                ObjectMapper objectMapper) {
        this.playerLogMapper = playerLogMapper;
        this.battleLogMapper = battleLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addLog(Long playerId, String type, String message) {
        addLog(playerId, type, message, null);
    }

    @Override
    public void addLog(Long playerId, String type, String message, Map<String, Object> metadata) {
        PlayerLog log = new PlayerLog();
        log.setPlayerId(playerId);
        log.setType(type);
        log.setMessage(message);
        if (metadata != null) {
            try {
                log.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("玩家日志元数据序列化失败", exception);
            }
        }
        if (playerLogMapper.insert(log) <= 0) {
            throw new IllegalStateException("玩家日志保存失败");
        }
    }

    @Override
    public Map<String, Object> findExplorationReceipt(Long playerId, String requestKey) {
        return findReceipt(playerId, "exploration_result", requestKey);
    }

    @Override
    public Map<String, Object> findStoryChoiceReceipt(Long playerId, String requestKey) {
        return findReceipt(playerId, "story_choice", requestKey);
    }

    private Map<String, Object> findReceipt(Long playerId, String kind, String requestKey) {
        PlayerLog receipt = findReceiptLog(playerId, kind, requestKey);
        return receipt == null ? null : parseMetadata(receipt.getMetadataJson());
    }

    private PlayerLog findReceiptLog(Long playerId, String kind, String requestKey) {
        int offset = 0;
        while (true) {
            List<PlayerLog> receipts = selectReceiptPage(playerId, false, offset);
            if (receipts == null || receipts.isEmpty()) return null;
            for (PlayerLog receipt : receipts) {
                Map<String, Object> metadata = parseMetadata(receipt.getMetadataJson());
                if (metadata != null && Objects.equals(metadata.get("kind"), kind)
                        && Objects.equals(metadata.get("requestKey"), requestKey)) return receipt;
            }
            if (receipts.size() < PENDING_BATTLE_PAGE_SIZE) return null;
            offset += receipts.size();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findPendingBattleEncounters(Long playerId) {
        List<Map<String, Object>> pending = new ArrayList<>();
        int offset = 0;
        while (pending.size() < PENDING_BATTLE_PAGE_SIZE) {
            List<PlayerLog> receipts = selectReceiptPage(playerId, true, offset);
            if (receipts == null || receipts.isEmpty()) break;
            for (PlayerLog receipt : receipts) {
                Map<String, Object> metadata = parseMetadata(receipt.getMetadataJson());
                if (metadata == null || Boolean.TRUE.equals(metadata.get("battleEncounterResolved"))) continue;
                Object storedResponse = metadata.get("requestResponse");
                if (!(storedResponse instanceof Map<?, ?> response)) continue;

                boolean storyChoice = "story_choice".equals(metadata.get("kind"));
                if (!storyChoice && !"exploration_result".equals(metadata.get("kind"))) continue;
                Object battleSnapshot = storyChoice ? response.get("pending_battle") : response;
                if (!(battleSnapshot instanceof Map<?, ?> battle)
                        || !"battle".equals(battle.get("result_type"))) continue;
                String requestKey = metadata.get(storyChoice ? "battleRequestKey" : "requestKey")
                        instanceof String key ? key : null;
                // A committed combat receipt is authoritative even if the separate acknowledgement
                // request was lost. Replaying this key would be safe, but needlessly reopening the
                // encounter is confusing to the player.
                if (requestKey != null && !requestKey.isBlank()
                        && battleLogMapper.selectByPlayerAndRequestKey(playerId, requestKey) != null) continue;

                Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) battle);
                if (requestKey != null && !requestKey.isBlank()) result.putIfAbsent("request_key", requestKey);
                pending.add(result);
                if (pending.size() >= PENDING_BATTLE_PAGE_SIZE) break;
            }

            // Settled or acknowledged receipts must not permanently occupy the first page and
            // hide a newer encounter that the player still needs to resolve.
            if (receipts.size() < PENDING_BATTLE_PAGE_SIZE || pending.size() >= PENDING_BATTLE_PAGE_SIZE) break;
            offset += receipts.size();
        }
        return pending;
    }

    @Override
    public void acknowledgeBattleEncounter(Long playerId, String requestKey) {
        PlayerLog receipt = findPendingBattleReceipt(playerId, requestKey);
        if (receipt == null) return;
        Map<String, Object> metadata = parseMetadata(receipt.getMetadataJson());
        if (metadata == null) return;
        Object response = metadata.get("requestResponse");
        if (!(response instanceof Map<?, ?> map)) return;
        Object battleSnapshot = "story_choice".equals(metadata.get("kind"))
                ? map.get("pending_battle") : map;
        if (!(battleSnapshot instanceof Map<?, ?> battle)
                || !"battle".equals(battle.get("result_type"))) return;
        metadata.put("battleEncounterResolved", true);
        try {
            receipt.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException ignored) {
            // The combat receipt remains authoritative and prevents duplicate resolution.
            return;
        }
        if (playerLogMapper.updateById(receipt) <= 0) {
            throw new IllegalStateException("战斗遭遇回执更新失败");
        }
    }

    private PlayerLog findPendingBattleReceipt(Long playerId, String battleRequestKey) {
        int offset = 0;
        while (true) {
            List<PlayerLog> receipts = selectReceiptPage(playerId, false, offset);
            if (receipts == null || receipts.isEmpty()) return null;
            for (PlayerLog receipt : receipts) {
                Map<String, Object> metadata = parseMetadata(receipt.getMetadataJson());
                if (metadata == null) continue;
                Object kind = metadata.get("kind");
                if (!"exploration_result".equals(kind) && !"story_choice".equals(kind)) continue;
                Object storedResponse = metadata.get("requestResponse");
                if (!(storedResponse instanceof Map<?, ?> response)) continue;
                boolean storyChoice = "story_choice".equals(kind);
                Object battleSnapshot = storyChoice ? response.get("pending_battle") : response;
                if (!(battleSnapshot instanceof Map<?, ?> battle)
                        || !"battle".equals(battle.get("result_type"))) continue;
                String receiptRequestKey = metadata.get(storyChoice ? "battleRequestKey" : "requestKey")
                        instanceof String key ? key : null;
                if (Objects.equals(receiptRequestKey, battleRequestKey)) return receipt;
            }
            if (receipts.size() < PENDING_BATTLE_PAGE_SIZE) return null;
            offset += receipts.size();
        }
    }

    private List<PlayerLog> selectReceiptPage(Long playerId, boolean ascending, int offset) {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
             .in("type", RECEIPT_LOG_TYPES);
        if (ascending) query.orderByAsc("id");
        else query.orderByDesc("id");
        query.last("LIMIT " + PENDING_BATTLE_PAGE_SIZE + " OFFSET " + offset);
        return playerLogMapper.selectList(query);
    }

    @Override
    public List<LogEntry> getRecentLogs(Long playerId, int limit) {
        QueryWrapper<PlayerLog> query = new QueryWrapper<>();
        query.eq("player_id", playerId)
             .orderByDesc("created_at")
             .orderByDesc("id")
             .last("LIMIT " + limit);
        List<PlayerLog> logs = playerLogMapper.selectList(query);

        List<LogEntry> result = new ArrayList<>();
        for (PlayerLog log : logs) {
            result.add(new LogEntry(
                    log.getId(),
                    log.getMessage(),
                    log.getType(),
                    log.getCreatedAt(),
                    parsePublicMetadata(log.getMetadataJson())
            ));
        }
        Collections.reverse(result);
        return result;
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() {});
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private Map<String, Object> parsePublicMetadata(String metadataJson) {
        Map<String, Object> metadata = parseMetadata(metadataJson);
        if (metadata == null || (!metadata.containsKey("requestKey")
                && !metadata.containsKey("storyInstanceKey")
                && !metadata.containsKey("requestLocationKey")
                && !metadata.containsKey("requestResponse")
                && !metadata.containsKey("battleRequestKey")
                && !metadata.containsKey("battleEncounterResolved"))) return metadata;

        Map<String, Object> publicMetadata = new LinkedHashMap<>(metadata);
        publicMetadata.remove("requestKey");
        publicMetadata.remove("storyInstanceKey");
        publicMetadata.remove("requestLocationKey");
        publicMetadata.remove("requestResponse");
        publicMetadata.remove("battleRequestKey");
        publicMetadata.remove("battleEncounterResolved");
        return publicMetadata;
    }
}
