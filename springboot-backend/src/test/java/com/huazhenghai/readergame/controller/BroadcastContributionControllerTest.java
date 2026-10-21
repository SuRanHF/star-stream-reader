package com.huazhenghai.readergame.controller;

import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.dto.ContributeBroadcastRequest;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.security.LoginUser;
import com.huazhenghai.readergame.security.LoginUserContext;
import com.huazhenghai.readergame.service.BroadcastService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastContributionControllerTest {

    @Mock private BroadcastService broadcastService;
    @Mock private PlayerMapper playerMapper;

    private BroadcastController controller;

    @BeforeEach
    void setUp() {
        controller = new BroadcastController(broadcastService, playerMapper);
        Player ownedPlayer = new Player();
        ownedPlayer.setId(42L);
        ownedPlayer.setUserId(9L);
        when(playerMapper.selectById(42L)).thenReturn(ownedPlayer);
        LoginUserContext.set(new LoginUser(9L, "player", "player"));
    }

    @AfterEach
    void clearLoginUser() {
        LoginUserContext.clear();
    }

    @Test
    void rejectsClientSuppliedContributionWithoutChangingPublicProgress() {
        ContributeBroadcastRequest request = new ContributeBroadcastRequest();
        request.setEventKey("ai_test-event");
        request.setPlayerId(42L);
        request.setValue(Integer.MAX_VALUE);
        request.setContributionType("exploration");

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.contribute(request));

        assertEquals("公共活动贡献只能由探索、战斗、PK 等实际游戏行动自动累计", error.getMessage());
        verify(broadcastService, never()).contribute(
                request.getEventKey(), request.getPlayerId(), request.getValue(), request.getContributionType());
    }
}
