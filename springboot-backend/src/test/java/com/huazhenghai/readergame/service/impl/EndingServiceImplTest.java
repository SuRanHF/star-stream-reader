package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huazhenghai.readergame.entity.Ending;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.EndingMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndingServiceImplTest {

    @Mock private EndingMapper endingMapper;
    @Mock private PlayerMapper playerMapper;
    private EndingServiceImpl endingService;

    @BeforeEach
    void createService() {
        endingService = new EndingServiceImpl(endingMapper, playerMapper, new ObjectMapper());
    }

    @Test
    void canonicalFinalEndingFlagIsReadFromTheRequestedPlayersTimelineOnly() {
        Player playerWithFlag = player(11L, "{\"ending_new_oldest_dream\":true}");
        Player playerWithoutFlag = player(22L, "{\"ending_new_oldest_dream\":false}");
        when(playerMapper.selectById(11L)).thenReturn(playerWithFlag);
        when(playerMapper.selectById(22L)).thenReturn(playerWithoutFlag);
        when(endingMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(ending("end_continue_dream",
                        "{\"flags\":{\"ending_new_oldest_dream\":true}}")));

        assertEquals(List.of("end_continue_dream"), endingKeys(11L));
        assertEquals(List.of(), endingKeys(22L));

        verify(playerMapper).selectById(11L);
        verify(playerMapper).selectById(22L);
    }

    @Test
    void continuationAndFrozenWorldEndingsCannotLeakBetweenPlayers() {
        when(playerMapper.selectById(11L)).thenReturn(
                player(11L, "{\"ending_new_oldest_dream\":true}"));
        when(playerMapper.selectById(22L)).thenReturn(
                player(22L, "{\"ending_world_frozen\":true}"));
        when(endingMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                ending("end_continue_dream", "{\"flags\":{\"ending_new_oldest_dream\":true}}"),
                ending("end_frozen_world", "{\"flags\":{\"ending_world_frozen\":true}}")));

        assertEquals(List.of("end_continue_dream"), endingKeys(11L));
        assertEquals(List.of("end_frozen_world"), endingKeys(22L));
    }

    private List<String> endingKeys(Long playerId) {
        return endingService.checkEndings(playerId).stream()
                .map(ending -> (String) ending.get("endingKey"))
                .toList();
    }

    private static Player player(Long playerId, String storyFlagsJson) {
        Player player = new Player();
        player.setId(playerId);
        player.setStoryFlagsJson(storyFlagsJson);
        return player;
    }

    private static Ending ending(String endingKey, String conditionsJson) {
        Ending ending = new Ending();
        ending.setEndingKey(endingKey);
        ending.setName(endingKey);
        ending.setDescription(endingKey);
        ending.setPriority(1);
        ending.setConditionsJson(conditionsJson);
        ending.setIsHidden(1);
        return ending;
    }
}
