package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.Party;
import com.huazhenghai.readergame.entity.PartyMember;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.PlayerLog;
import com.huazhenghai.readergame.mapper.PartyMapper;
import com.huazhenghai.readergame.mapper.PartyMemberMapper;
import com.huazhenghai.readergame.mapper.PlayerLogMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.service.ChatService;
import com.huazhenghai.readergame.service.OnlinePlayerService;
import com.huazhenghai.readergame.service.PlayerActionReceiptService;
import com.huazhenghai.readergame.service.WorldBossService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceImplTest {

    private static final Long PLAYER_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final String PARTY_NO = "PT-TEST";

    @Mock private PartyMapper partyMapper;
    @Mock private PartyMemberMapper partyMemberMapper;
    @Mock private PlayerMapper playerMapper;
    @Mock private PlayerLogMapper playerLogMapper;
    @Mock private OnlinePlayerService onlinePlayerService;
    @Mock private ChatService chatService;
    @Mock private WorldBossService worldBossService;
    @Mock private PlayerActionReceiptService actionReceiptService;

    private PartyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PartyServiceImpl(partyMapper, partyMemberMapper, playerMapper,
                playerLogMapper, onlinePlayerService, chatService, worldBossService, actionReceiptService);
    }

    @Test
    void joinPartyLocksPartyBeforeCapacityCheckAndRejectsFullParty() {
        Player player = new Player();
        player.setId(PLAYER_ID);
        player.setUserId(USER_ID);

        Party party = new Party();
        party.setPartyNo(PARTY_NO);
        party.setStatus("active");
        // Legacy rows may not have a capacity value; the service default is also three.

        when(playerMapper.selectById(PLAYER_ID)).thenReturn(player);
        when(partyMemberMapper.selectOne(any())).thenReturn(null);
        when(partyMapper.selectActiveForUpdate(PARTY_NO)).thenReturn(party);
        when(partyMemberMapper.selectList(any())).thenReturn(List.of(
                new PartyMember(), new PartyMember(), new PartyMember()));

        assertThrows(BusinessException.class, () -> service.joinParty(PLAYER_ID, PARTY_NO, USER_ID));

        InOrder order = inOrder(partyMapper, partyMemberMapper);
        order.verify(partyMemberMapper).selectOne(any());
        order.verify(partyMapper).selectActiveForUpdate(PARTY_NO);
        order.verify(partyMemberMapper).selectOne(any());
        ArgumentCaptor<QueryWrapper<PartyMember>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        order.verify(partyMemberMapper).selectList(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().getSqlSegment().trim().endsWith("FOR UPDATE"));
        verify(partyMemberMapper, never()).insert(any(PartyMember.class));
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void onlyThePartyLeaderCanStartTheSharedBossBattle() {
        Player memberPlayer = new Player();
        memberPlayer.setId(PLAYER_ID);
        memberPlayer.setUserId(USER_ID);
        PartyMember membership = activeMember(PLAYER_ID, 1);
        Party party = activeParty(99L);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(memberPlayer);
        when(partyMemberMapper.selectOne(any())).thenReturn(membership);
        when(partyMapper.selectOne(any())).thenReturn(party);

        assertThrows(BusinessException.class,
                () -> service.startPartyBossBattle(PARTY_NO, PLAYER_ID, USER_ID, "party-boss-request"));

        verify(worldBossService, never()).attackPartyBoss(any(), any(), any());
        verify(partyMapper, never()).updateById(any(Party.class));
    }

    @Test
    void partyLeaderMustBeReadyBeforeStartingSharedBossBattle() {
        Player leader = new Player();
        leader.setId(PLAYER_ID);
        leader.setUserId(USER_ID);
        PartyMember membership = activeMember(PLAYER_ID, 0);
        Party party = activeParty(PLAYER_ID);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(leader);
        when(partyMemberMapper.selectOne(any())).thenReturn(membership);
        when(partyMapper.selectOne(any())).thenReturn(party);

        assertThrows(BusinessException.class,
                () -> service.startPartyBossBattle(PARTY_NO, PLAYER_ID, USER_ID, "party-boss-request"));

        verify(worldBossService, never()).attackPartyBoss(any(), any(), any());
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
    }

    @Test
    void readyLeaderStartsTheSharedBossBattleAndStoresItsTarget() {
        Player leader = new Player();
        leader.setId(PLAYER_ID);
        leader.setUserId(USER_ID);
        leader.setPlayerName("队长");
        PartyMember membership = activeMember(PLAYER_ID, 1);
        Party party = activeParty(PLAYER_ID);
        party.setName("测试队伍");
        Map<String, Object> battleResult = Map.of("bossNo", "WB-TEST", "bossDefeated", false);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(leader);
        when(partyMemberMapper.selectOne(any())).thenReturn(membership);
        when(partyMapper.selectOne(any())).thenReturn(party);
        when(partyMemberMapper.selectCount(any())).thenReturn(1L);
        when(worldBossService.attackPartyBoss(PARTY_NO, PLAYER_ID, USER_ID)).thenReturn(battleResult);

        Map<String, Object> result = service.startPartyBossBattle(
                PARTY_NO, PLAYER_ID, USER_ID, "party-boss-request");

        assertEquals(battleResult, result);
        assertEquals("worldBoss", party.getTargetType());
        assertEquals("WB-TEST", party.getTargetKey());
        verify(worldBossService).attackPartyBoss(PARTY_NO, PLAYER_ID, USER_ID);
        verify(partyMapper).updateById(party);
        verify(playerLogMapper).insert(any(PlayerLog.class));
        ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
        verify(actionReceiptService).record(eq(PLAYER_ID), eq("party.world_boss.attack"),
                eq("party-boss-request"), requestCaptor.capture(), eq(battleResult));
        assertEquals(Map.of("playerId", PLAYER_ID, "partyNo", PARTY_NO), requestCaptor.getValue());
    }

    @Test
    void sameRequestKeyReplaysTheSavedResultWithoutReattacking() {
        Player leader = new Player();
        leader.setId(PLAYER_ID);
        leader.setUserId(USER_ID);
        Map<String, Object> savedResult = Map.of("bossNo", "WB-42", "totalDamage", 37);

        when(playerMapper.selectByIdForUpdate(PLAYER_ID)).thenReturn(leader);
        when(actionReceiptService.findReplay(eq(PLAYER_ID), eq("party.world_boss.attack"),
                eq("party-boss-request"), any(), eq(Map.class))).thenReturn(savedResult);

        Map<String, Object> result = service.startPartyBossBattle(
                PARTY_NO, PLAYER_ID, USER_ID, "party-boss-request");

        assertEquals(savedResult, result);
        verify(worldBossService, never()).attackPartyBoss(any(), any(), any());
        verify(partyMapper, never()).selectOne(any());
        verify(playerLogMapper, never()).insert(any(PlayerLog.class));
        verify(actionReceiptService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void missingRequestKeyIsRejectedBeforeLoadingPlayerState() {
        assertThrows(BusinessException.class,
                () -> service.startPartyBossBattle(PARTY_NO, PLAYER_ID, USER_ID, " "));

        verify(playerMapper, never()).selectByIdForUpdate(any());
        verify(worldBossService, never()).attackPartyBoss(any(), any(), any());
    }

    private static Party activeParty(Long leaderPlayerId) {
        Party party = new Party();
        party.setPartyNo(PARTY_NO);
        party.setLeaderPlayerId(leaderPlayerId);
        party.setStatus("active");
        return party;
    }

    private static PartyMember activeMember(Long playerId, int ready) {
        PartyMember member = new PartyMember();
        member.setPartyNo(PARTY_NO);
        member.setPlayerId(playerId);
        member.setStatus("active");
        member.setReady(ready);
        return member;
    }
}
