package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.common.ErrorCode;
import com.huazhenghai.readergame.vo.CombatStatsVO;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.WorldBoss;
import com.huazhenghai.readergame.entity.WorldBossParticipation;
import com.huazhenghai.readergame.mapper.PartyMapper;
import com.huazhenghai.readergame.mapper.PartyMemberMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.WorldBossMapper;
import com.huazhenghai.readergame.mapper.WorldBossParticipationMapper;
import com.huazhenghai.readergame.mapper.WorldBossDailyRunMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.CombatService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.WorldlineService;
import com.huazhenghai.readergame.vo.WorldBossAttackResultVO;
import com.huazhenghai.readergame.vo.WorldBossRewardVO;
import com.huazhenghai.readergame.vo.WorldBossRankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorldBossServiceImplTest {

    private static final Long PLAYER_ID = 42L;
    private static final Long USER_ID = 9L;
    private static final String BOSS_NO = "boss-test";

    @Mock private WorldBossMapper bossMapper;
    @Mock private WorldBossDailyRunMapper dailyRunMapper;
    @Mock private WorldBossParticipationMapper participationMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private CombatService combatService;
    @Mock private RecoveryService recoveryService;
    @Mock private ChatService chatService;
    @Mock private WorldlineService worldlineService;
    @Mock private BroadcastService broadcastService;
    @Mock private FactionService factionService;
    @Mock private QuestService questService;
    @Mock private InventoryService inventoryService;
    @Mock private PartyMapper partyMapper;
    @Mock private PartyMemberMapper partyMemberMapper;
    @Mock private PlayerEffectService playerEffectService;
    @Mock private PlayerActionReceiptService actionReceiptService;
    @Mock private PlayerResourceTransactionService resourceTransactionService;

    private WorldBossServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorldBossServiceImpl(bossMapper, dailyRunMapper, participationMapper, playerMapper,
                playerLogMapper, combatService, recoveryService, chatService, worldlineService,
                broadcastService, factionService, questService, inventoryService, partyMapper,
                partyMemberMapper, new ObjectMapper(), playerEffectService,
                resourceTransactionService, actionReceiptService);
    }

    @Test
    void scheduledBossClaimsOnlyOnceForTheLocalCalendarDate() {
        when(bossMapper.selectOne(any())).thenReturn(null);
        when(dailyRunMapper.claimDailyRun(LocalDate.now())).thenReturn(1);

        assertNotNull(service.openScheduledBoss());

        verify(dailyRunMapper).claimDailyRun(LocalDate.now());
        verify(bossMapper).insert(any(WorldBoss.class));
    }

    @Test
    void scheduledBossDoesNotReopenAfterTheDailyRunWasAlreadyClaimed() {
        when(bossMapper.selectOne(any())).thenReturn(null);
        when(dailyRunMapper.claimDailyRun(LocalDate.now())).thenReturn(0);

        assertNull(service.openScheduledBoss());

        verify(bossMapper, never()).insert(any(WorldBoss.class));
    }

    @Test
    void claimRewardLocksPlayerBeforeParticipationAndAddsToExistingBalances() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setCoins(30);
        player.setStoryFragments(8);
        WorldBoss boss = settledBoss();
        WorldBossParticipation participation = participation(0);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(bossMapper.selectOne(any())).thenReturn(boss);
        when(participationMapper.selectOne(any())).thenReturn(participation);
        when(playerMapper.updateById(player)).thenReturn(1);
        when(participationMapper.updateById(participation)).thenReturn(1);

        WorldBossRewardVO result = service.claimReward(PLAYER_ID, BOSS_NO, USER_ID, "wb-claim-1");

        assertEquals(true, result.isClaimed());
        assertEquals(55, player.getCoins());
        assertEquals(12, player.getStoryFragments());
        assertEquals(1, participation.getRewardClaimed());
        verify(playerMapper).updateById(player);
        verify(participationMapper).updateById(participation);
        verify(resourceTransactionService, org.mockito.Mockito.times(2)).record(any(
                PlayerResourceTransactionService.Change.class));
        verify(actionReceiptService).record(eq(PLAYER_ID), eq("world_boss.claim"),
                eq("wb-claim-1"), any(), eq(result));

        ArgumentCaptor<QueryWrapper<WorldBossParticipation>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        InOrder lockOrder = inOrder(playerMapper, participationMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        lockOrder.verify(participationMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().trim().endsWith("FOR UPDATE"));
    }

    @Test
    void claimRewardRejectsAlreadyClaimedParticipationWithoutGrantingAgain() {
        Player player = new Player();
        player.setUserId(USER_ID);
        player.setCoins(30);
        WorldBossParticipation participation = participation(1);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(bossMapper.selectOne(any())).thenReturn(settledBoss());
        when(participationMapper.selectOne(any())).thenReturn(participation);

        assertThrows(BusinessException.class,
                () -> service.claimReward(PLAYER_ID, BOSS_NO, USER_ID, "wb-claim-already-done"));

        verify(playerMapper, never()).updateById(any(Player.class));
        verify(participationMapper, never()).updateById(any(WorldBossParticipation.class));
    }

    @Test
    void claimRewardRejectsCurrencyOverflowWithoutSavingPlayerOrClaimMarker() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setCoins(Integer.MAX_VALUE);
        WorldBossParticipation participation = participation(0);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(bossMapper.selectOne(any())).thenReturn(settledBoss());
        when(participationMapper.selectOne(any())).thenReturn(participation);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.claimReward(PLAYER_ID, BOSS_NO, USER_ID, "wb-overflow"));

        assertEquals(ErrorCode.BAD_REQUEST, error.getCode());
        assertEquals(Integer.MAX_VALUE, player.getCoins());
        assertEquals(0, participation.getRewardClaimed());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(participationMapper, never()).updateById(any(WorldBossParticipation.class));
        verify(resourceTransactionService, never()).record(any(
                PlayerResourceTransactionService.Change.class));
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void claimRewardReplaysSavedResponseBeforeReadingParticipationAgain() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        WorldBossRewardVO saved = new WorldBossRewardVO();
        saved.setClaimed(true);
        saved.setRankNo(1);
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(actionReceiptService.findReplay(eq(PLAYER_ID), eq("world_boss.claim"),
                eq("wb-claim-replay"), any(), eq(WorldBossRewardVO.class))).thenReturn(saved);

        WorldBossRewardVO result = service.claimReward(
                PLAYER_ID, BOSS_NO, USER_ID, "wb-claim-replay");

        assertEquals(saved, result);
        verify(bossMapper, never()).selectOne(any());
        verify(participationMapper, never()).selectOne(any());
        verify(playerMapper, never()).updateById(any(Player.class));
        verify(resourceTransactionService, never()).record(any(PlayerResourceTransactionService.Change.class));
    }

    @Test
    void attackLocksPlayerThenBossThenParticipationBeforeApplyingDamage() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);
        player.setStatsJson("{\"hp\":100}");
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("active");
        boss.setDefense(0);
        boss.setCurrentHp(10_000L);
        boss.setMaxHp(10_000L);
        boss.setStartAt(LocalDateTime.now().minusMinutes(1));
        boss.setEndAt(LocalDateTime.now().plusMinutes(5));
        CombatStatsVO stats = new CombatStatsVO();
        stats.setAttack(100);
        stats.setLevel(1);
        stats.setCritRate(0);
        stats.setCritDamage(1.5);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(player);
        when(bossMapper.selectActiveByBossNoForUpdate(BOSS_NO)).thenReturn(boss);
        when(participationMapper.selectOne(any())).thenReturn(null);
        when(combatService.calculateCombatPower(PLAYER_ID)).thenReturn(stats);

        WorldBossAttackResultVO result = service.attackBoss(PLAYER_ID, BOSS_NO, USER_ID);

        assertTrue(result.isSuccess());
        assertTrue(result.getDamage() > 0);
        assertEquals(10_000L - result.getDamage(), result.getBossCurrentHp());
        verify(bossMapper).updateById(boss);
        verify(participationMapper).insert(any(WorldBossParticipation.class));
        verify(questService).addProgress(PLAYER_ID, "world_boss_attack_count", 1, "worldBoss", BOSS_NO);

        ArgumentCaptor<QueryWrapper<WorldBossParticipation>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        InOrder lockOrder = inOrder(playerMapper, bossMapper, participationMapper);
        lockOrder.verify(playerMapper).selectByIdForUpdate(PLAYER_ID);
        lockOrder.verify(bossMapper).selectActiveByBossNoForUpdate(BOSS_NO);
        lockOrder.verify(participationMapper).selectOne(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().trim().endsWith("FOR UPDATE"));
    }

    @Test
    void settleBossLocksTheBossRowBeforeApplyingSettlement() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("killed");
        boss.setWorldlineEffectsJson("{}");
        when(bossMapper.selectByBossNoForUpdate(BOSS_NO)).thenReturn(boss);
        when(participationMapper.selectList(any())).thenReturn(java.util.List.of());
        when(bossMapper.updateById(boss)).thenReturn(1);

        service.settleBoss(BOSS_NO);

        assertEquals("settled", boss.getStatus());
        verify(bossMapper).selectByBossNoForUpdate(BOSS_NO);
        verify(bossMapper).updateById(boss);
    }

    @Test
    void settleBossPropagatesWorldlineWriteFailureSoSettlementCanRetry() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("killed");
        boss.setWorldlineEffectsJson("{\"worldline_shift\":5}");
        when(bossMapper.selectByBossNoForUpdate(BOSS_NO)).thenReturn(boss);
        when(participationMapper.selectList(any())).thenReturn(java.util.List.of());
        when(bossMapper.updateById(boss)).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("worldline write failed"))
                .when(worldlineService).addWorldlineShift(5.0, "world_boss_" + BOSS_NO);

        assertThrows(IllegalStateException.class, () -> service.settleBoss(BOSS_NO));

        verify(bossMapper).updateById(boss);
        verify(worldlineService).addWorldlineShift(5.0, "world_boss_" + BOSS_NO);
    }

    @Test
    void settleBossGivesEqualDamageTheSameCompetitionRank() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("killed");
        boss.setWorldlineEffectsJson("{}");
        WorldBossParticipation first = participation(1L, 500L);
        WorldBossParticipation tiedFirst = participation(2L, 500L);
        WorldBossParticipation third = participation(3L, 250L);
        WorldBossParticipation fourth = participation(4L, 100L);
        when(bossMapper.selectByBossNoForUpdate(BOSS_NO)).thenReturn(boss);
        when(participationMapper.selectList(any())).thenReturn(
                java.util.List.of(first, tiedFirst, third, fourth));
        when(bossMapper.updateById(boss)).thenReturn(1);

        service.settleBoss(BOSS_NO);

        assertEquals(1, first.getRankNo());
        assertEquals(1, tiedFirst.getRankNo());
        assertEquals(3, third.getRankNo());
        assertEquals(4, fourth.getRankNo());
        ArgumentCaptor<QueryWrapper<WorldBossParticipation>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(participationMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("damage desc"));
        assertTrue(sqlSegment.contains("player_id asc"));
    }

    @Test
    void bossLeaderboardUsesTheSameCompetitionRanksAsSettlement() {
        WorldBossParticipation first = participation(1L, 500L);
        WorldBossParticipation tiedFirst = participation(2L, 500L);
        WorldBossParticipation third = participation(3L, 250L);
        when(participationMapper.selectList(any())).thenReturn(
                java.util.List.of(first, tiedFirst, third));

        java.util.List<WorldBossRankingVO> rankings = service.getBossRankings(BOSS_NO);

        assertEquals(java.util.List.of(1, 1, 3),
                rankings.stream().map(WorldBossRankingVO::getRank).toList());
        ArgumentCaptor<QueryWrapper<WorldBossParticipation>> queryCaptor =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(participationMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sqlSegment.contains("damage desc"));
        assertTrue(sqlSegment.contains("player_id asc"));
    }

    @Test
    void tiedTopDamageParticipantsBothReceiveTheTopRankReward() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("killed");
        boss.setWorldlineEffectsJson("{}");
        WorldBossParticipation first = participation(PLAYER_ID, 500L);
        WorldBossParticipation tiedFirst = participation(43L, 500L);
        when(bossMapper.selectByBossNoForUpdate(BOSS_NO)).thenReturn(boss);
        when(participationMapper.selectList(any())).thenReturn(java.util.List.of(first, tiedFirst));
        when(bossMapper.updateById(boss)).thenReturn(1);
        when(participationMapper.updateById(any(WorldBossParticipation.class))).thenReturn(1);

        service.settleBoss(BOSS_NO);

        Player firstPlayer = new Player();
        firstPlayer.setId(PLAYER_ID);
        firstPlayer.setUserId(USER_ID);
        firstPlayer.setCoins(0);
        firstPlayer.setStoryFragments(0);
        Player tiedPlayer = new Player();
        tiedPlayer.setId(43L);
        tiedPlayer.setUserId(10L);
        tiedPlayer.setCoins(0);
        tiedPlayer.setStoryFragments(0);
        WorldBoss rewardConfig = settledBoss();
        rewardConfig.setRankRewardsJson("{\"top1\":{\"coins\":50},\"top3\":{\"coins\":20}}");
        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(firstPlayer);
        when(playerMapper.selectByIdForUpdate(43L)).thenReturn(tiedPlayer);
        when(bossMapper.selectOne(any())).thenReturn(rewardConfig);
        when(participationMapper.selectOne(any())).thenReturn(first, tiedFirst);
        when(playerMapper.updateById(any(Player.class))).thenReturn(1);

        WorldBossRewardVO firstReward = service.claimReward(PLAYER_ID, BOSS_NO, USER_ID, "tie-top-a");
        WorldBossRewardVO tiedReward = service.claimReward(43L, BOSS_NO, 10L, "tie-top-b");

        assertEquals(1, firstReward.getRankNo());
        assertEquals(1, tiedReward.getRankNo());
        assertEquals(75L, firstReward.getRewards().get("coins"));
        assertEquals(75L, tiedReward.getRewards().get("coins"));
    }

    @Test
    void settleBossReturnsWithoutReplayingEffectsWhenAnotherWorkerSettledIt() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setStatus("settled");
        when(bossMapper.selectByBossNoForUpdate(BOSS_NO)).thenReturn(boss);

        service.settleBoss(BOSS_NO);

        verify(bossMapper).selectByBossNoForUpdate(BOSS_NO);
        verify(participationMapper, never()).selectList(any());
        verify(bossMapper, never()).updateById(any(WorldBoss.class));
        verify(worldlineService, never()).addWorldlineShift(any(Double.class), any());
    }

    private WorldBoss settledBoss() {
        WorldBoss boss = new WorldBoss();
        boss.setBossNo(BOSS_NO);
        boss.setName("测试Boss");
        boss.setStatus("settled");
        boss.setRewardsJson("{\"participation\":{\"minDamage\":1,\"coins\":25,\"storyFragments\":4}}");
        boss.setRankRewardsJson("{}");
        return boss;
    }

    private WorldBossParticipation participation(int claimed) {
        WorldBossParticipation participation = new WorldBossParticipation();
        participation.setDamage(100L);
        participation.setRewardClaimed(claimed);
        return participation;
    }

    private WorldBossParticipation participation(Long playerId, Long damage) {
        WorldBossParticipation participation = new WorldBossParticipation();
        participation.setPlayerId(playerId);
        participation.setDamage(damage);
        participation.setAttackCount(1);
        participation.setRewardClaimed(0);
        return participation;
    }
}
