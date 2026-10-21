package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.QuestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies real faction-contribution and quest persistence in a disposable H2 database. */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:faction_quest_persistence;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.ai-director.llm-api-key=",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class FactionQuestPersistenceIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private FactionService factionService;
    @Autowired private QuestService questService;

    @Test
    void actualContributionAdvancesValueQuestAndCapsAtItsTarget() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("jdbc:h2:mem:faction_quest_persistence"),
                    "Faction contribution integration must use only its isolated in-memory H2 database");
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "h2faction" + suffix;
        jdbcTemplate.update("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)",
                username, username + "@example.invalid", "isolated-test-only");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);

        String playerName = "隔离阵营角色" + suffix;
        jdbcTemplate.update("INSERT INTO players (user_id, player_name, coins, stats_json) VALUES (?, ?, ?, ?)",
                userId, playerName, 0, "{}");
        Long playerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE user_id = ? AND player_name = ?",
                Long.class, userId, playerName);

        String factionKey = "h2_faction_" + suffix;
        jdbcTemplate.update("INSERT INTO constellation_factions (faction_key, name, level, exp, "
                        + "member_count, total_contribution, buffs_json, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                factionKey, "隔离测试阵营", 1, 0, 1, 0, "{}", 1);
        jdbcTemplate.update("INSERT INTO player_factions (player_id, faction_key, role, reputation, "
                        + "contribution_total, joined_at, status) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                playerId, factionKey, "member", 0, 0, "active");

        String questKey = "h2_faction_value_" + suffix;
        jdbcTemplate.update("INSERT INTO quests (quest_key, title, description, quest_type, category, "
                        + "target_type, target_value, rewards_json, reset_cycle, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                questKey, "阵营贡献验收", "累计贡献十二点", "daily", "faction",
                "faction_contribution_value", 12, "{}", "daily", 1);
        questService.ensurePlayerQuests(playerId);

        String firstRelatedId = "h2-faction-source-" + suffix;
        var first = factionService.contribute(
                playerId, factionKey, "manual", 7L, "faction", firstRelatedId, userId);
        assertNotNull(first);
        assertEquals(7, questProgress(playerId, questKey));
        assertEquals(1, progressLogCount(playerId, questKey));

        var second = factionService.contribute(
                playerId, factionKey, "manual", 8L, "faction", firstRelatedId + "-second", userId);
        assertNotNull(second);

        assertEquals(12, questProgress(playerId, questKey), "任务进度应封顶在配置目标，而非超额贡献值");
        assertEquals("completed", jdbcTemplate.queryForObject(
                "SELECT status FROM player_quests WHERE player_id = ? AND quest_key = ?",
                String.class, playerId, questKey));
        assertEquals(2, progressLogCount(playerId, questKey));
        assertEquals(15, jdbcTemplate.queryForObject(
                "SELECT contribution_total FROM player_factions WHERE player_id = ? AND faction_key = ?",
                Integer.class, playerId, factionKey));
        assertEquals(15, jdbcTemplate.queryForObject(
                "SELECT reputation FROM player_factions WHERE player_id = ? AND faction_key = ?",
                Integer.class, playerId, factionKey));
        assertEquals(15, jdbcTemplate.queryForObject(
                "SELECT total_contribution FROM constellation_factions WHERE faction_key = ?",
                Integer.class, factionKey));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM faction_contributions WHERE faction_key = ? AND player_id = ?",
                Integer.class, factionKey, playerId));
    }

    private int questProgress(Long playerId, String questKey) {
        return jdbcTemplate.queryForObject(
                "SELECT progress FROM player_quests WHERE player_id = ? AND quest_key = ?",
                Integer.class, playerId, questKey);
    }

    private int progressLogCount(Long playerId, String questKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quest_progress_logs WHERE player_id = ? AND quest_key = ? "
                        + "AND source = 'faction'",
                Integer.class, playerId, questKey);
    }
}
