package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable public snapshots. A random nonce is retained to reproduce an active code. */
@Service
public class RouteShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PlayerMapper players;
    private final StoryTreeService tree;
    private final ObjectMapper json;
    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public RouteShareService(JdbcTemplate jdbc, PlayerMapper players, StoryTreeService tree,
            ObjectMapper json, @Value("${app.story-share.code-secret:${app.jwt.secret}}") String secret) {
        this(jdbc, players, tree, json, secret, Clock.systemUTC());
    }

    RouteShareService(JdbcTemplate jdbc, PlayerMapper players, StoryTreeService tree,
            ObjectMapper json, String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("Route share secret must contain at least 32 UTF-8 bytes");
        this.jdbc = jdbc;
        this.players = players;
        this.tree = tree;
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(Long playerId) {
        // Serialise create/revoke for one player; the controller has already checked ownership.
        Player player = players.selectByIdForUpdate(playerId);
        if (player == null) throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<ShareRow> existing = jdbc.query(
                "SELECT nonce,code_hash,expires_at FROM route_shares WHERE player_id=?",
                (rs, i) -> new ShareRow(rs.getString(1), rs.getString(2),
                        rs.getTimestamp(3).toLocalDateTime()), playerId);
        if (!existing.isEmpty() && existing.get(0).expiresAt().isAfter(now)) {
            ShareRow row = existing.get(0);
            String code = code(playerId, row.nonce());
            if (!hash(code).equals(row.hash()))
                throw new IllegalStateException("Route share code integrity check failed");
            return response(code, row.expiresAt());
        }
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String code = code(playerId, nonce);
        LocalDateTime expiresAt = now.plusDays(7);
        String snapshot;
        try { snapshot = json.writeValueAsString(tree.getRouteShareSnapshot(player)); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize route snapshot", e); }
        if (existing.isEmpty()) jdbc.update("INSERT INTO route_shares"
                + "(player_id,code_hash,nonce,snapshot_json,created_at,expires_at) VALUES(?,?,?,?,?,?)",
                playerId, hash(code), nonce, snapshot, Timestamp.valueOf(now), Timestamp.valueOf(expiresAt));
        else jdbc.update("UPDATE route_shares SET code_hash=?,nonce=?,snapshot_json=?,created_at=?,expires_at=?"
                + " WHERE player_id=?", hash(code), nonce, snapshot,
                Timestamp.valueOf(now), Timestamp.valueOf(expiresAt), playerId);
        return response(code, expiresAt);
    }

    @Transactional
    public void revoke(Long playerId) {
        if (players.selectByIdForUpdate(playerId) == null)
            throw new BusinessException(ErrorCode.PLAYER_NOT_FOUND, "玩家不存在");
        jdbc.update("DELETE FROM route_shares WHERE player_id=?", playerId);
    }

    @Transactional(readOnly = true)
    public JsonNode get(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}"))
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享路线不存在");
        List<String> rows = jdbc.query("SELECT snapshot_json FROM route_shares WHERE code_hash=? AND expires_at>?",
                (rs, i) -> rs.getString(1), hash(code), Timestamp.valueOf(LocalDateTime.now(clock)));
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "分享路线不存在");
        try { return json.readTree(rows.get(0)); }
        catch (Exception e) { throw new IllegalStateException("Cannot read route snapshot", e); }
    }

    private Map<String, Object> response(String code, LocalDateTime expiresAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("expires_at", expiresAt.toInstant(ZoneOffset.UTC).toString());
        return result;
    }

    private String code(Long playerId, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(
                    ("route-share:v1:" + playerId + ":" + nonce).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Cannot create route share code", e); }
    }

    private String hash(String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(code.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Cannot hash route share code", e); }
    }

    private record ShareRow(String nonce, String hash, LocalDateTime expiresAt) { }
}
