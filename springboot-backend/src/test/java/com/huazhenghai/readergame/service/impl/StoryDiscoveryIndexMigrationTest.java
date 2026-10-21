package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryDiscoveryIndexMigrationTest {

    @Test
    void baseSchemaAndRepeatableMigrationDeclareTheSameCoveringIndex() throws IOException {
        String schema = readResource("/db/init.sql");
        String migration = readResource("/db/migrations/20261001_add_story_discovery_index.sql");
        String indexDefinition = "`idx_psl_discovery_first` (`player_id`, `event_key`, `created_at`, `choice_index`)";

        assertTrue(schema.contains(indexDefinition), "新建数据库应包含首次发现聚合索引");
        assertTrue(migration.contains("information_schema.statistics"), "已有数据库迁移应检查索引是否存在");
        assertTrue(migration.contains("table_schema = DATABASE()"), "索引检查必须限定当前数据库");
        assertTrue(migration.contains("table_name = 'player_story_logs'"));
        assertTrue(migration.contains("index_name = 'idx_psl_discovery_first'"));
        assertTrue(migration.contains("ADD INDEX " + indexDefinition));
        assertTrue(!migration.contains("UPDATE `players`") && !migration.contains("DELETE FROM `players`"),
                "故事榜索引迁移不得改写玩家存档");
    }

    private String readResource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
