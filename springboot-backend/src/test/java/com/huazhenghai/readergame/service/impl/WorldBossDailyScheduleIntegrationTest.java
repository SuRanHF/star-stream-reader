package com.huazhenghai.readergame.service.impl;

import com.huazhenghai.readergame.service.WorldBossService;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.ExploreService;
import com.huazhenghai.readergame.service.NarrativeService;
import com.huazhenghai.readergame.vo.WorldBossVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that only one scheduled world boss can be opened per local date,
 * including concurrent application calls. Uses only a disposable H2 database.
 */
@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:world_boss_daily_schedule;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.scheduler.enabled=false"
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Sql("/db/combat-persistence-test-schema.sql")
class WorldBossDailyScheduleIntegrationTest {

    @Autowired private WorldBossService worldBossService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ChatService chatService;
    @MockitoBean private ExploreService exploreService;
    @MockitoBean private NarrativeService narrativeService;

    @BeforeEach
    void clearOnlyWorldBossScheduleFixtures() {
        jdbcTemplate.execute("ALTER TABLE world_bosses DROP CONSTRAINT IF EXISTS reject_scheduled_boss");
        jdbcTemplate.update("DELETE FROM world_boss_participation");
        jdbcTemplate.update("DELETE FROM world_bosses");
        jdbcTemplate.update("DELETE FROM world_boss_daily_runs");
    }

    @Test
    void defeatedBossDoesNotReopenAgainOnTheSameDate() {
        WorldBossVO opened = worldBossService.openScheduledBoss();
        assertNotNull(opened);

        jdbcTemplate.update("UPDATE world_bosses SET status = 'killed' WHERE boss_no = ?", opened.getBossNo());

        assertNull(worldBossService.openScheduledBoss());
        assertEquals(1, count("world_bosses"));
        assertEquals(1, countDailyClaims());
    }

    @Test
    void concurrentSchedulerCallsCreateExactlyOneBossForTheDate() throws Exception {
        int workers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<WorldBossVO>> results = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("Concurrent-start latch timed out");
                        return worldBossService.openScheduledBoss();
                    }))
                    .toList();

            if (!ready.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Scheduler workers did not become ready");
            start.countDown();

            long created = 0;
            for (Future<WorldBossVO> result : results) {
                if (result.get(30, TimeUnit.SECONDS) != null) created++;
            }

            assertEquals(1, created);
            assertEquals(1, count("world_bosses"));
            assertEquals(1, countDailyClaims());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void existingBossFromPreviousDateDoesNotConsumeTodaysRun() {
        jdbcTemplate.update("""
                INSERT INTO world_bosses (
                    boss_key, boss_no, name, status, level, max_hp, current_hp,
                    attack, defense, speed, start_at, end_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, DATEADD('DAY', -1, CURRENT_TIMESTAMP), DATEADD('HOUR', 1, CURRENT_TIMESTAMP))
                """,
                "previous-day-test", "previous-day-active", "Previous Day Boss", "active",
                1, 1000L, 1000L, 1, 1, 1);

        assertNull(worldBossService.openScheduledBoss());
        assertEquals(0, countDailyClaims());

        jdbcTemplate.update("UPDATE world_bosses SET status = 'expired' WHERE boss_no = ?", "previous-day-active");
        assertNotNull(worldBossService.openScheduledBoss());
        assertEquals(1, countDailyClaims());
    }

    @Test
    void previousCalendarDayClaimAllowsTheNextDateToOpenItsRun() {
        jdbcTemplate.update("INSERT INTO world_boss_daily_runs (run_date) "
                + "VALUES (DATEADD('DAY', -1, CURRENT_DATE))");

        assertNotNull(worldBossService.openScheduledBoss());
        assertEquals(2, count("world_boss_daily_runs"));
        assertEquals(1, countDailyClaims());
    }

    @Test
    void migrationBackfillsExistingBossDatesIdempotentlyAndPreventsAnotherRunToday() {
        LocalDate today = LocalDate.now();
        insertHistoricalBoss("same-day-one", today.atStartOfDay(), "killed");
        insertHistoricalBoss("same-day-two", today.atStartOfDay().plusMinutes(1), "settled");
        insertHistoricalBoss("previous-day", today.minusDays(1).atStartOfDay(), "expired");

        String backfill = """
                INSERT IGNORE INTO world_boss_daily_runs (run_date, created_at)
                SELECT DATE(start_at), MIN(created_at)
                FROM world_bosses
                WHERE start_at IS NOT NULL
                GROUP BY DATE(start_at)
                """;
        jdbcTemplate.update(backfill);

        assertEquals(2, count("world_boss_daily_runs"));
        assertEquals(1, countDailyClaims());
        assertEquals(0, jdbcTemplate.update(backfill));
        assertNull(worldBossService.openScheduledBoss());
        assertEquals(3, count("world_bosses"));
    }

    @Test
    void failedBossInsertRollsBackDailyClaimAndAllowsRetry() {
        jdbcTemplate.execute("ALTER TABLE world_bosses ADD CONSTRAINT reject_scheduled_boss "
                + "CHECK (boss_key <> 'station_disaster_core')");

        org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                () -> worldBossService.openScheduledBoss());
        assertEquals(0, countDailyClaims());

        jdbcTemplate.execute("ALTER TABLE world_bosses DROP CONSTRAINT reject_scheduled_boss");
        assertNotNull(worldBossService.openScheduledBoss());
        assertEquals(1, countDailyClaims());
    }

    private void insertHistoricalBoss(String bossNo, LocalDateTime startAt, String status) {
        jdbcTemplate.update("""
                INSERT INTO world_bosses (
                    boss_key, boss_no, name, status, level, max_hp, current_hp,
                    attack, defense, speed, start_at, end_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "history-test", bossNo, "Historical Boss " + bossNo, status,
                1, 1000L, 1000L, 1, 1, 1,
                startAt, startAt.plusHours(2));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countDailyClaims() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM world_boss_daily_runs WHERE run_date = ?",
                Integer.class,
                LocalDate.now());
    }
}
