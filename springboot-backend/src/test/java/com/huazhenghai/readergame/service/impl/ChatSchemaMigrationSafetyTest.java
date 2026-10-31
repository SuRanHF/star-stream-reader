package com.huazhenghai.readergame.service.impl;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSchemaMigrationSafetyTest {
    @Test
    void migrationMatchesBaselineAndGuardsEachRepeatExecution() throws Exception {
        Path directory = Path.of("src/main/resources/db/migrations");
        String file = "20261031_chat_scope_and_reply.sql";
        try (Stream<Path> paths = Files.list(directory)) {
            List<String> names = paths.map(path -> path.getFileName().toString()).toList();
            assertEquals(names.size(), names.stream().distinct().count());
            assertEquals(1, names.stream().filter(file::equals).count());
            assertTrue(names.stream().allMatch(name -> name.matches("\\d{8}_[a-z0-9_]+\\.sql")));
        }
        String migration = Files.readString(directory.resolve(file));
        String baseline = Files.readString(Path.of("src/main/resources/db/init.sql"));
        for (String column : List.of("scope_id", "reply_to_id", "idx_chat_channel_scope_id")) {
            assertTrue(migration.contains(column));
            assertTrue(baseline.contains(column));
        }
        assertTrue(migration.contains("information_schema.COLUMNS"));
        assertTrue(migration.contains("information_schema.STATISTICS"));
        assertTrue(migration.contains("SELECT 1"));
        assertFalse(migration.matches("(?is).*\\b(UPDATE|DELETE FROM|INSERT INTO)\\s+[^;]*players\\b.*"));
    }
}
