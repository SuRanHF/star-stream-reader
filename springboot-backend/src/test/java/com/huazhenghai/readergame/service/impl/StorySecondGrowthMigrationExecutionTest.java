package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes the unchanged migration in an isolated H2 MySQL-mode database. */
public class StorySecondGrowthMigrationExecutionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> NEXT = Map.of(
            "chungmuro_conspiracy_story", "chungmuro_council_audit",
            "chungmuro_compromise_ledger", "dongmyo_ledger_delivery",
            "chungmuro_script_blank", "chungmuro_paper_summon",
            "chungmuro_hidden_reader_wall", "geumho_wall_message");

    /** H2 lacks MySQL JSON_CONTAINS; bidirectional containment in this migration is JSON equality. */
    public static boolean jsonContains(String document, String candidate) throws Exception {
        return JSON.readTree(document).equals(JSON.readTree(candidate));
    }

    private static String mysqlSqlForH2(String statement) {
        // H2 does not interpret MySQL backslash escapes or CAST(text AS JSON) the same way.
        String sql = statement.replaceAll("CAST\\((('(?>[^']|'')*')) AS JSON\\)", "$1");
        StringBuilder converted = new StringBuilder();
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch != '\'') {
                converted.append(ch);
                continue;
            }
            converted.append('\'');
            for (++i; i < sql.length(); i++) {
                ch = sql.charAt(i);
                if (ch == '\\' && i + 1 < sql.length()) {
                    char escaped = sql.charAt(++i);
                    ch = switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case '0' -> '\0';
                        default -> escaped;
                    };
                } else if (ch == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') i++;
                    else { converted.append('\''); break; }
                }
                if (ch == '\'') converted.append('\'');
                else converted.append(ch);
            }
        }
        return converted.toString();
    }

    @Test
    void secondGrowthMigrationExecutesTwiceAndSecondRunChangesNothing() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream(
                "/db/migrations/20261101_story_tree_second_growth.sql")) {
            assertNotNull(input);
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> statements = new ArrayList<>();
        for (String statement : migration.split(";\\s*(?:\\R|$)")) {
            int start = statement.indexOf("UPDATE `exploration_events`");
            if (start < 0) start = statement.indexOf("INSERT IGNORE INTO `exploration_events`");
            if (start >= 0) statements.add(statement.substring(start).trim());
        }
        assertEquals(16, statements.size());

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:second_growth_migration;MODE=MySQL", "sa", "");
             Statement sql = connection.createStatement()) {
            sql.execute("CREATE ALIAS JSON_CONTAINS FOR '"
                    + getClass().getName() + ".jsonContains'");
            sql.execute("CREATE TABLE exploration_events ("
                    + "event_key VARCHAR(100) PRIMARY KEY, event_type VARCHAR(40),"
                    + "stage_key VARCHAR(100), location_key VARCHAR(100), name VARCHAR(255),"
                    + "description CLOB, weight INT, stamina_cost INT, repeatable INT,"
                    + "required_conditions_json CLOB, rewards_json CLOB,"
                    + "progress_effects_json CLOB, choices_json CLOB, log_template CLOB)");
            Pattern guard = Pattern.compile("WHERE `event_key` = '([^']+)'\\s+AND JSON_CONTAINS\\("
                    + "`choices_json`, CAST\\(('(?:[^']|'')*') AS JSON\\)\\)");
            for (int i = 0; i < 4; i++) {
                Matcher match = guard.matcher(statements.get(i));
                assertTrue(match.find(), "missing old-value guard: " + i);
                sql.executeUpdate(mysqlSqlForH2("INSERT INTO exploration_events(event_key, choices_json) VALUES ('"
                        + match.group(1) + "', " + match.group(2) + ")"));
            }
            int firstChanges = 0;
            for (String statement : statements) firstChanges += sql.executeUpdate(mysqlSqlForH2(statement));
            assertEquals(16, firstChanges);
            for (var route : NEXT.entrySet()) {
                try (ResultSet rows = sql.executeQuery("SELECT choices_json FROM exploration_events "
                        + "WHERE event_key='" + route.getKey() + "'")) {
                    assertTrue(rows.next());
                    JsonNode choices = JSON.readTree(rows.getString(1));
                    assertEquals(2, choices.size());
                    for (JsonNode choice : choices) {
                        assertFalse(choice.has("route_pending"), route.getKey());
                        assertEquals(route.getValue(), choice.path("unlock_events").get(0).asText());
                    }
                }
            }
            try (ResultSet rows = sql.executeQuery("SELECT event_key,stage_key,choices_json "
                    + "FROM exploration_events WHERE stage_key='vol1_ch4'")) {
                int count = 0;
                while (rows.next()) {
                    count++;
                    assertEquals("vol1_ch4", rows.getString(2));
                    assertEquals(2, JSON.readTree(rows.getString(3)).size(), rows.getString(1));
                }
                assertEquals(12, count);
            }
            int secondChanges = 0;
            for (String statement : statements) secondChanges += sql.executeUpdate(mysqlSqlForH2(statement));
            assertEquals(0, secondChanges);
        }
    }
}
