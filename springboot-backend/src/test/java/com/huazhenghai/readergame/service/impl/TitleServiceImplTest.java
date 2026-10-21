package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Title;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleServiceImplTest {

    @Mock private TitleMapper titleMapper;
    @Mock private PlayerTitleMapper playerTitleMapper;
    @Mock private PlayerMapper playerMapper;

    private TitleServiceImpl titleService;

    @BeforeEach
    void setUp() {
        titleService = new TitleServiceImpl(titleMapper, playerTitleMapper, playerMapper, new ObjectMapper());
    }

    @Test
    void grantTitleRejectsMissingDefinitionBeforeWritingPlayerOwnership() {
        when(titleMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> titleService.grantTitle(1L, "missing_title"));

        assertEquals("称号配置不存在: missing_title", error.getMessage());
        verify(playerTitleMapper, never()).insert(any(PlayerTitle.class));
    }

    @Test
    void grantTitlePersistsOwnershipWhenDefinitionExists() {
        Title title = new Title();
        title.setTitleKey("growth_novice");
        when(titleMapper.selectOne(any(QueryWrapper.class))).thenReturn(title);
        when(playerTitleMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        titleService.grantTitle(1L, "growth_novice");

        ArgumentCaptor<PlayerTitle> captor = ArgumentCaptor.forClass(PlayerTitle.class);
        verify(playerTitleMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getPlayerId());
        assertEquals("growth_novice", captor.getValue().getTitleKey());
        assertEquals("任务奖励", captor.getValue().getSource());
    }
}
