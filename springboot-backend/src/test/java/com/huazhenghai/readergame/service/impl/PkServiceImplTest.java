package com.huazhenghai.readergame.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.PkChallenge;
import com.huazhenghai.readergame.entity.PkRecord;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Ranking;
import com.huazhenghai.readergame.mapper.PkChallengeMapper;
import com.huazhenghai.readergame.mapper.PkRecordMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.RankingMapper;
import com.huazhenghai.readergame.service.AvatarRankService;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PkServiceImplTest {

    @Mock private PlayerMapper playerMapper;
    @Mock private PkChallengeMapper challengeMapper;
    @Mock private PkRecordMapper recordMapper;
    @Mock private RankingMapper rankingMapper;
    @Mock private CombatService combatService;
    @Mock private RecoveryService recoveryService;
    @Mock private PlayerLogService playerLogService;
    @Mock private BroadcastService broadcastService;
    @Mock private FactionService factionService;
    @Mock private QuestService questService;
    @Mock private OnlinePlayerService onlinePlayerService;
    @Mock private AvatarRankService avatarRankService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private PkServiceImpl pkService;

    @BeforeEach
    void setUp() {
        pkService = new PkServiceImpl(playerMapper, challengeMapper, recordMapper, rankingMapper,
                combatService, recoveryService, playerLogService, broadcastService, factionService,
                questService, onlinePlayerService, avatarRankService, new ObjectMapper(),
                actionReceiptService, resourceTransactionService);
    }

    @Test
    void recommendsOnlineOpponentsByRatingAndRealCombatPowerCloseness() {
        Player self = player(1L, 9L, "我", "F", 20);
        Player far = player(2L, 12L, "越级者", "SS", 40);
        Player close = player(3L, 13L, "接近者", "E", 22);
        Player even = player(4L, 14L, "同级者", "F", 20);
        when(playerMapper.selectById(1L)).thenReturn(self);
        when(playerMapper.selectList(any())).thenReturn(List.of(far, close, even));
        when(challengeMapper.selectList(any())).thenReturn(List.<PkChallenge>of());
        when(onlinePlayerService.isOnline(any())).thenReturn(true);
        when(rankingMapper.selectOne(any())).thenReturn(ranking(1L, 1000));
        when(rankingMapper.selectList(any())).thenReturn(List.of(
                ranking(2L, 1700), ranking(3L, 1200), ranking(4L, 1050)));
        when(combatService.calculateCombatPower(1L)).thenReturn(power(100, 50, 100, 20, 20));
        when(combatService.calculateCombatPower(2L)).thenReturn(power(200, 100, 200, 40, 40));
        when(combatService.calculateCombatPower(3L)).thenReturn(power(110, 55, 110, 22, 22));
        when(combatService.calculateCombatPower(4L)).thenReturn(power(100, 50, 100, 20, 20));

        List<Map<String, Object>> results = pkService.getOpponents(1L, 9L);

        assertEquals(List.of(4L, 3L, 2L), results.stream().map(row -> row.get("id")).toList());
        assertEquals("F", results.get(0).get("avatarRank"));
        assertEquals(940, results.get(0).get("combat_power"));
        assertEquals("势均力敌", results.get(0).get("match_label"));
        assertEquals(true, results.get(0).get("ranked"));
        assertEquals("实力接近", results.get(1).get("match_label"));
        assertEquals("越级挑战", results.get(2).get("match_label"));
        assertEquals(50, results.get(2).get("power_gap_percent"));
        org.mockito.Mockito.verify(rankingMapper).selectList(any());
        org.mockito.Mockito.verify(rankingMapper).selectOne(any());
    }

    @Test
    void recommendsAnUnrankedOpponentWithAnExplicitInitialRating() {
        Player self = player(1L, 9L, "我", "F", 1);
        Player newcomer = player(2L, 10L, "新玩家", "F", 1);
        when(playerMapper.selectById(1L)).thenReturn(self);
        when(playerMapper.selectList(any())).thenReturn(List.of(newcomer));
        when(challengeMapper.selectList(any())).thenReturn(List.of());
        when(onlinePlayerService.isOnline(2L)).thenReturn(true);
        when(rankingMapper.selectOne(any())).thenReturn(null);
        when(rankingMapper.selectList(any())).thenReturn(List.of());
        when(combatService.calculateCombatPower(1L)).thenReturn(power(100, 50, 100, 20, 1));
        when(combatService.calculateCombatPower(2L)).thenReturn(power(100, 50, 100, 20, 1));

        List<Map<String, Object>> results = pkService.getOpponents(1L, 9L);

        assertEquals(1, results.size());
        assertEquals(false, results.get(0).get("ranked"));
        assertEquals(1000, results.get(0).get("rating"));
        assertEquals(0, results.get(0).get("wins"));
        assertEquals(0, results.get(0).get("losses"));
        org.mockito.Mockito.verify(rankingMapper).selectList(any());
    }

    @Test
    void rejectsChallengeThatWasAlreadyResolvedBeforeItsLockedRead() {
        PkChallenge challenge = new PkChallenge();
        challenge.setId(81L);
        challenge.setDefenderId(2L);
        challenge.setStatus("accepted");
        when(playerMapper.selectById(2L)).thenReturn(player(2L, 20L, "防守者", "F", 1));
        when(challengeMapper.selectByIdForUpdate(81L)).thenReturn(challenge);

        assertThrows(BusinessException.class,
                () -> pkService.resolveChallenge(81L, true, 2L, 20L, "pk-resolve-old"));

        verify(challengeMapper).selectByIdForUpdate(81L);
        verify(playerMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void rejectsDeadPlayerBeforeCreatingPkChallenge() {
        Player attacker = player(1L, 10L, "已死亡的挑战者", "F", 1);
        attacker.setStatsJson("{\"hp\":0,\"maxHp\":100,\"isResting\":false}");
        Player defender = player(2L, 20L, "应战者", "F", 1);
        defender.setStatsJson("{\"hp\":100,\"maxHp\":100,\"isResting\":false}");
        when(playerMapper.selectById(1L)).thenReturn(attacker);
        when(playerMapper.selectById(2L)).thenReturn(defender);

        assertThrows(BusinessException.class,
                () -> pkService.createChallenge(1L, 2L, "spar", 10L));

        verify(challengeMapper, never()).insert(any(PkChallenge.class));
    }

    @Test
    void rejectsChallengeWhenDefenderDiesAfterChallengeWasCreated() {
        PkChallenge challenge = new PkChallenge();
        challenge.setId(83L);
        challenge.setAttackerId(1L);
        challenge.setDefenderId(2L);
        challenge.setMode("spar");
        challenge.setStatus("pending");

        Player attacker = player(1L, 10L, "挑战者", "F", 1);
        attacker.setStatsJson("{\"hp\":100,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");
        Player defender = player(2L, 20L, "已死亡的应战者", "F", 1);
        defender.setStatsJson("{\"hp\":0,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");
        when(challengeMapper.selectByIdForUpdate(83L)).thenReturn(challenge);
        when(challengeMapper.updateById(challenge)).thenReturn(1);
        when(playerMapper.selectById(2L)).thenReturn(defender);
        when(actionReceiptService.findReplay(eq(2L), eq("pk.challenge.resolve"),
                eq("pk-resolve-dead"), any(), eq(Map.class))).thenReturn(null);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(attacker);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(defender);
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            int hp = player.getId().equals(2L) ? 0 : 100;
            return new java.util.LinkedHashMap<>(Map.of(
                    "hp", hp, "maxHp", 100, "exp", 0, "level", 1, "avatarRank", "F"));
        });

        assertThrows(BusinessException.class,
                () -> pkService.resolveChallenge(83L, true, 2L, 20L, "pk-resolve-dead"));

        verify(combatService, never()).calculateCombatPower(any());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(recordMapper, never()).insert(any(PkRecord.class));
        verify(resourceTransactionService, never()).record(any());
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void acceptedChallengeRecordsResourceChangesAndReplaysAfterTimeout() throws Exception {
        PkChallenge challenge = new PkChallenge();
        challenge.setId(81L);
        challenge.setAttackerId(1L);
        challenge.setDefenderId(2L);
        challenge.setMode("spar");
        challenge.setStatus("pending");

        Player attacker = player(1L, 10L, "挑战者", "F", 1);
        attacker.setCoins(100);
        attacker.setStatsJson("{\"hp\":100,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");
        Player defender = player(2L, 20L, "应战者", "F", 1);
        defender.setCoins(40);
        defender.setStatsJson("{\"hp\":100,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");

        Ranking attackerRanking = ranking(1L, 1000);
        Ranking defenderRanking = ranking(2L, 1000);
        when(challengeMapper.selectByIdForUpdate(81L)).thenReturn(challenge);
        when(challengeMapper.updateById(challenge)).thenReturn(1);
        when(playerMapper.selectById(2L)).thenReturn(defender);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(attacker);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(defender);
        when(playerMapper.updateById(attacker)).thenReturn(1);
        when(playerMapper.updateById(defender)).thenReturn(1);
        when(actionReceiptService.findReplay(eq(2L), eq("pk.challenge.resolve"),
                eq("pk-resolve-1"), any(), eq(Map.class))).thenReturn(null);
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                new java.util.LinkedHashMap<>(Map.of(
                        "hp", 100, "maxHp", 100, "exp", 0, "level", 1, "avatarRank", "F")));
        when(combatService.calculateCombatPower(1L)).thenReturn(power(1000, 0, 100, 20, 1));
        when(combatService.calculateCombatPower(2L)).thenReturn(power(1, 0, 100, 1, 1));
        when(avatarRankService.getExpMultiplier("F")).thenReturn(1.0);
        when(rankingMapper.selectOne(any())).thenReturn(
                attackerRanking, defenderRanking, attackerRanking, defenderRanking);
        when(recordMapper.insert(any(PkRecord.class))).thenReturn(1);

        Map<String, Object> first = pkService.resolveChallenge(81L, true, 2L, 20L, "pk-resolve-1");
        when(actionReceiptService.findReplay(eq(2L), eq("pk.challenge.resolve"),
                eq("pk-resolve-1"), any(), eq(Map.class))).thenReturn(first);
        Map<String, Object> replay = pkService.resolveChallenge(81L, true, 2L, 20L, "pk-resolve-1");

        assertEquals(first, replay);
        assertEquals("accepted", challenge.getStatus());
        assertEquals(150, attacker.getCoins());
        assertEquals(40, defender.getCoins());
        assertEquals(0, new ObjectMapper().readTree(defender.getStatsJson()).get("hp").asInt());
        verify(recordMapper).insert(any(PkRecord.class));
        verify(challengeMapper).updateById(challenge);
        verify(actionReceiptService).record(eq(2L), eq("pk.challenge.resolve"),
                eq("pk-resolve-1"), any(), eq(first));
        verify(questService).addProgress(1L, "pk_participate_count", 1, "pk", "1_vs_2");
        verify(questService).addProgress(2L, "pk_participate_count", 1, "pk", "1_vs_2");
        verify(questService).addProgress(1L, "pk_win_count", 1, "pk", "1_vs_2");
        verify(questService, org.mockito.Mockito.times(2)).addProgress(
                any(), eq("pk_participate_count"), org.mockito.ArgumentMatchers.anyInt(), any(), any());

        org.mockito.ArgumentCaptor<PlayerResourceTransactionService.Change> changes =
                org.mockito.ArgumentCaptor.forClass(PlayerResourceTransactionService.Change.class);
        verify(resourceTransactionService, org.mockito.Mockito.times(3)).record(changes.capture());
        assertEquals(List.of("coins", "exp", "hp"), changes.getAllValues().stream()
                .map(PlayerResourceTransactionService.Change::resourceKey).toList());
        assertEquals(List.of(1L, 1L, 2L), changes.getAllValues().stream()
                .map(PlayerResourceTransactionService.Change::playerId).toList());
    }

    @Test
    void acceptedChallengeRejectsWinnerCurrencyOverflowBeforeSavingPlayerState() throws Exception {
        PkChallenge challenge = new PkChallenge();
        challenge.setId(82L);
        challenge.setAttackerId(1L);
        challenge.setDefenderId(2L);
        challenge.setMode("spar");
        challenge.setStatus("pending");

        Player attacker = player(1L, 10L, "挑战者", "F", 1);
        attacker.setCoins(Integer.MAX_VALUE);
        attacker.setStatsJson("{\"hp\":100,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");
        Player defender = player(2L, 20L, "应战者", "F", 1);
        defender.setCoins(40);
        defender.setStatsJson("{\"hp\":100,\"maxHp\":100,\"exp\":0,\"level\":1,\"avatarRank\":\"F\"}");
        when(challengeMapper.selectByIdForUpdate(82L)).thenReturn(challenge);
        when(challengeMapper.updateById(challenge)).thenReturn(1);
        when(playerMapper.selectById(2L)).thenReturn(defender);
        when(playerMapper.selectByIdForUpdate(1L)).thenReturn(attacker);
        when(playerMapper.selectByIdForUpdate(2L)).thenReturn(defender);
        when(recoveryService.applyRecovery(any(Player.class))).thenAnswer(invocation ->
                new java.util.LinkedHashMap<>(Map.of(
                        "hp", 100, "maxHp", 100, "exp", 0, "level", 1, "avatarRank", "F")));
        when(combatService.calculateCombatPower(1L)).thenReturn(power(1000, 0, 100, 20, 1));
        when(combatService.calculateCombatPower(2L)).thenReturn(power(1, 0, 100, 1, 1));
        when(avatarRankService.getExpMultiplier("F")).thenReturn(1.0);
        when(rankingMapper.selectOne(any())).thenReturn(
                ranking(1L, 1000), ranking(2L, 1000), ranking(1L, 1000), ranking(2L, 1000));

        assertThrows(BusinessException.class,
                () -> pkService.resolveChallenge(82L, true, 2L, 20L, "pk-resolve-overflow"));

        assertEquals(Integer.MAX_VALUE, attacker.getCoins());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any());
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    private static Player player(Long id, Long userId, String name, String rank, int level) {
        Player player = new Player();
        player.setId(id);
        player.setUserId(userId);
        player.setPlayerName(name);
        player.setStatsJson("{\"avatarRank\":\"" + rank + "\",\"level\":" + level + "}");
        return player;
    }

    private static Ranking ranking(Long playerId, int rating) {
        Ranking ranking = new Ranking();
        ranking.setPlayerId(playerId);
        ranking.setRating(rating);
        ranking.setWins(0);
        ranking.setLosses(0);
        ranking.setHighestRating(rating);
        return ranking;
    }

    private static CombatStatsVO power(int attack, int defense, int hp, int speed, int level) {
        CombatStatsVO stats = new CombatStatsVO();
        stats.setAttack(attack);
        stats.setDefense(defense);
        stats.setHp(hp);
        stats.setSpeed(speed);
        stats.setLevel(level);
        stats.setCritRate(0.0);
        stats.setCritDamage(1.5);
        return stats;
    }
}
