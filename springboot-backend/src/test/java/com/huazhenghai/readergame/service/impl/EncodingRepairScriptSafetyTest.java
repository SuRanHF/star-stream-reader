package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingRepairScriptSafetyTest {

    private static final Pattern INSERT_ROW_KEY = Pattern.compile("(?m)^\\('([a-z0-9_]+)'\\s*,");

    @Test
    void repairUsesUpsertsAndDoesNotDeletePlayerProgressOrLogs() throws Exception {
        String repair = readResource("/db/fix_encoding_all.sql");

        assertFalse(Pattern.compile("(?im)^\\s*DELETE\\s+FROM\\b").matcher(repair).find(),
                "编码修复脚本不得通过删除静态表或玩家表来重建数据");
        assertEquals(3, occurrences(repair, "ON DUPLICATE KEY UPDATE"),
                "装备、任务和技能定义都应使用按键更新");
        assertFalse(repair.contains("player_logs` WHERE id IN"),
                "不得把其他数据库的自增日志 ID 当成本库乱码记录");
    }

    @Test
    void repairQuestAndSkillDefinitionsStayInSyncWithCanonicalSeeds() throws Exception {
        String repair = readResource("/db/fix_encoding_all.sql");
        String init = readResource("/db/init.sql");
        String skillSeed = readResource("/db/seed_skills.sql");

        assertEquals(insertedRows(init, "quests"), insertedRows(repair, "quests"),
                "编码修复脚本应与 init.sql 保持完整任务定义一致");
        assertEquals(insertedRows(skillSeed, "skills"), insertedRows(repair, "skills"),
                "编码修复脚本应与技能种子保持完整定义一致");
    }

    @Test
    void repairedEquipmentKeysMustAlreadyExistInTheEquipmentSeed() throws Exception {
        Set<String> canonicalEquipment = insertedKeys(readResource("/db/seed_equipment.sql"), "equipment");
        Set<String> repairedEquipment = insertedKeys(readResource("/db/fix_encoding_all.sql"), "equipment");

        assertTrue(canonicalEquipment.containsAll(repairedEquipment),
                () -> "编码修复脚本包含主装备种子中不存在的装备键：" + difference(repairedEquipment, canonicalEquipment));
        assertFalse(repairedEquipment.isEmpty(), "编码修复脚本应保留明确的装备修复集合");
    }

    @Test
    void repairStatementsUpdateDefinitionsWithoutLosingDependentPlayerRows() throws Exception {
        String repair = readResource("/db/fix_encoding_all.sql");
        String databaseName = "encoding_repair_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE equipment (
                      equipment_key VARCHAR(100) PRIMARY KEY, name VARCHAR(100), slot VARCHAR(50),
                      rarity VARCHAR(30), description VARCHAR(1000), base_stats_json VARCHAR(2000),
                      special_effects_json VARCHAR(2000), max_durability INT, repair_cost INT,
                      sell_price INT, enabled INT)
                    """);
            statement.execute("""
                    CREATE TABLE player_equipment (
                      player_id BIGINT, equipment_key VARCHAR(100), durability INT,
                      FOREIGN KEY (equipment_key) REFERENCES equipment(equipment_key))
                    """);
            statement.executeUpdate("INSERT INTO equipment VALUES "
                    + "('broken_pipe','乱码','weapon','common','乱码','{\"attack\":99}',"
                    + "'{\"dropRateBonus\":1}',77,66,55,0)");
            statement.executeUpdate("INSERT INTO player_equipment VALUES (42,'broken_pipe',37)");

            statement.execute("""
                    CREATE TABLE quests (
                      quest_key VARCHAR(100) PRIMARY KEY, title VARCHAR(200), description VARCHAR(1000),
                      quest_type VARCHAR(30), category VARCHAR(50), target_type VARCHAR(100),
                      target_value INT, rewards_json VARCHAR(2000), reset_cycle VARCHAR(30), sort_order INT)
                    """);
            statement.execute("""
                    CREATE TABLE player_quests (
                      player_id BIGINT, quest_key VARCHAR(100), progress INT,
                      FOREIGN KEY (quest_key) REFERENCES quests(quest_key))
                    """);
            statement.execute("CREATE TABLE quest_progress_logs (player_id BIGINT, quest_key VARCHAR(100))");
            statement.executeUpdate("INSERT INTO quests VALUES "
                    + "('daily_explore_3','乱码','乱码','daily','explore','explore_count',99,"
                    + "'{\"coins\":999}','daily',1)");
            statement.executeUpdate("INSERT INTO player_quests VALUES (42,'daily_explore_3',2)");
            statement.executeUpdate("INSERT INTO quest_progress_logs VALUES (42,'daily_explore_3')");

            statement.execute("""
                    CREATE TABLE skills (
                      skill_key VARCHAR(100) PRIMARY KEY, name VARCHAR(100), `type` VARCHAR(50),
                      rarity VARCHAR(30), description VARCHAR(1000), unlock_conditions_json VARCHAR(2000),
                      effects_json VARCHAR(2000), cost_json VARCHAR(2000), cooldown_seconds INT, enabled INT)
                    """);
            statement.execute("""
                    CREATE TABLE player_skills (
                      player_id BIGINT, skill_key VARCHAR(100), skill_level INT,
                      FOREIGN KEY (skill_key) REFERENCES skills(skill_key))
                    """);
            statement.executeUpdate("INSERT INTO skills VALUES "
                    + "('steady_breath','乱码','passive','common','乱码','{}',"
                    + "'{\"maxStamina\":500}','{}',0,0)");
            statement.executeUpdate("INSERT INTO player_skills VALUES (42,'steady_breath',2)");

            statement.execute(insertStatement(repair, "equipment"));
            statement.execute(insertStatement(repair, "quests"));
            statement.execute(insertStatement(repair, "skills"));

            assertEquals(1, scalarInt(statement, "SELECT COUNT(*) FROM player_equipment"));
            assertEquals(37, scalarInt(statement,
                    "SELECT durability FROM player_equipment WHERE player_id=42 AND equipment_key='broken_pipe'"));
            assertEquals("弯曲钢管", scalarString(statement,
                    "SELECT name FROM equipment WHERE equipment_key='broken_pipe'"));
            assertEquals("{\"attack\":99}", scalarString(statement,
                    "SELECT base_stats_json FROM equipment WHERE equipment_key='broken_pipe'"));
            assertEquals(0, scalarInt(statement,
                    "SELECT enabled FROM equipment WHERE equipment_key='broken_pipe'"));
            assertEquals(10, scalarInt(statement, "SELECT COUNT(*) FROM quests"));
            assertEquals(2, scalarInt(statement,
                    "SELECT progress FROM player_quests WHERE player_id=42 AND quest_key='daily_explore_3'"));
            assertEquals(1, scalarInt(statement, "SELECT COUNT(*) FROM quest_progress_logs"));
            assertEquals("{\"coins\":999}", scalarString(statement,
                    "SELECT rewards_json FROM quests WHERE quest_key='daily_explore_3'"));
            assertEquals(99, scalarInt(statement,
                    "SELECT target_value FROM quests WHERE quest_key='daily_explore_3'"));
            assertEquals(6, scalarInt(statement, "SELECT COUNT(*) FROM skills"));
            assertEquals(2, scalarInt(statement,
                    "SELECT skill_level FROM player_skills WHERE player_id=42 AND skill_key='steady_breath'"));
            assertEquals("稳定呼吸", scalarString(statement,
                    "SELECT name FROM skills WHERE skill_key='steady_breath'"));
            assertEquals("{\"maxStamina\":500}", scalarString(statement,
                    "SELECT effects_json FROM skills WHERE skill_key='steady_breath'"));
            assertEquals(0, scalarInt(statement,
                    "SELECT enabled FROM skills WHERE skill_key='steady_breath'"));
        }
    }

    private static Set<String> insertedKeys(String sql, String table) {
        Pattern insert = Pattern.compile("(?i)INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`"
                + Pattern.quote(table) + "`(?=\\s|\\()");
        Matcher insertMatcher = insert.matcher(sql);
        if (!insertMatcher.find()) throw new IllegalArgumentException("缺少表 " + table + " 的 INSERT");
        int valuesStart = sql.indexOf("VALUES", insertMatcher.end());
        int statementEnd = valuesStart < 0 ? -1 : sql.indexOf(';', valuesStart);
        if (valuesStart < 0 || statementEnd < 0) {
            throw new IllegalArgumentException("表 " + table + " 的 VALUES 语句不完整");
        }
        Set<String> keys = new HashSet<>();
        Matcher rowMatcher = INSERT_ROW_KEY.matcher(sql.substring(valuesStart, statementEnd));
        while (rowMatcher.find()) keys.add(rowMatcher.group(1));
        return keys;
    }

    private static Map<String, List<String>> insertedRows(String sql, String table) {
        Pattern insert = Pattern.compile("(?i)INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`"
                + Pattern.quote(table) + "`(?=\\s|\\()");
        Matcher insertMatcher = insert.matcher(sql);
        if (!insertMatcher.find()) {
            throw new IllegalArgumentException("缺少表 " + table + " 的 INSERT");
        }
        int valuesStart = sql.indexOf("VALUES", insertMatcher.end());
        int statementEnd = valuesStart < 0 ? -1 : sql.indexOf("ON DUPLICATE KEY UPDATE", valuesStart);
        if (statementEnd < 0 && valuesStart >= 0) statementEnd = sql.indexOf(';', valuesStart);
        if (valuesStart < 0 || statementEnd < 0) {
            throw new IllegalArgumentException("表 " + table + " 的 VALUES 语句不完整");
        }

        String values = sql.substring(valuesStart + "VALUES".length(), statementEnd);
        Map<String, List<String>> rows = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < values.length()) {
            int rowStart = values.indexOf('(', cursor);
            if (rowStart < 0) break;
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inString = false;
            boolean rowComplete = false;
            for (int index = rowStart + 1; index < values.length(); index++) {
                char current = values.charAt(index);
                if (current == '\'') {
                    if (inString && index + 1 < values.length() && values.charAt(index + 1) == '\'') {
                        field.append("''");
                        index++;
                        continue;
                    }
                    inString = !inString;
                    field.append(current);
                } else if (!inString && current == ',') {
                    fields.add(field.toString().trim());
                    field.setLength(0);
                } else if (!inString && current == ')') {
                    fields.add(field.toString().trim());
                    rowComplete = true;
                    cursor = index + 1;
                    break;
                } else {
                    field.append(current);
                }
            }
            if (!rowComplete || fields.isEmpty()) {
                throw new IllegalArgumentException("表 " + table + " 的 VALUES 行不完整");
            }
            String keyLiteral = fields.get(0);
            if (keyLiteral.length() < 2 || keyLiteral.charAt(0) != '\''
                    || keyLiteral.charAt(keyLiteral.length() - 1) != '\'') {
                throw new IllegalArgumentException("表 " + table + " 的首列不是字符串键：" + keyLiteral);
            }
            String key = keyLiteral.substring(1, keyLiteral.length() - 1).replace("''", "'");
            rows.put(key, List.copyOf(fields));
        }
        return rows;
    }

    private static String insertStatement(String sql, String table) {
        Pattern insert = Pattern.compile("(?i)INSERT\\s+(?:IGNORE\\s+)?INTO\\s+`"
                + Pattern.quote(table) + "`(?=\\s|\\()");
        Matcher matcher = insert.matcher(sql);
        if (!matcher.find()) throw new IllegalArgumentException("缺少表 " + table + " 的 INSERT");
        int end = sql.indexOf(';', matcher.end());
        if (end < 0) throw new IllegalArgumentException("表 " + table + " 的 INSERT 未结束");
        return sql.substring(matcher.start(), end + 1);
    }

    private static int scalarInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new IllegalStateException("查询无结果：" + sql);
            return result.getInt(1);
        }
    }

    private static String scalarString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new IllegalStateException("查询无结果：" + sql);
            return result.getString(1);
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> difference = new HashSet<>(left);
        difference.removeAll(right);
        return difference;
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(
                EncodingRepairScriptSafetyTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
