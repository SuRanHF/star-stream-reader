package com.huazhenghai.readergame.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huazhenghai.readergame.common.BusinessException;
import com.huazhenghai.readergame.entity.PlayerTitle;
import com.huazhenghai.readergame.entity.Player;
import com.huazhenghai.readergame.entity.Title;
import com.huazhenghai.readergame.mapper.AvatarRankConfigMapper;
import com.huazhenghai.readergame.mapper.BattleLogMapper;
import com.huazhenghai.readergame.mapper.LocationMapper;
import com.huazhenghai.readergame.mapper.PlayerMapper;
import com.huazhenghai.readergame.mapper.PlayerTitleMapper;
import com.huazhenghai.readergame.mapper.TitleMapper;
import com.huazhenghai.readergame.service.BroadcastService;
import com.huazhenghai.readergame.service.EquipmentService;
import com.huazhenghai.readergame.service.FactionService;
import com.huazhenghai.readergame.service.InventoryService;
import com.huazhenghai.readergame.service.MonsterService;
import com.huazhenghai.readergame.service.PlayerEffectService;
import com.huazhenghai.readergame.service.PlayerLogService;
import com.huazhenghai.readergame.service.PlayerMaxHpProjectionService;
import com.huazhenghai.readergame.service.PlayerResourceTransactionService;
import com.huazhenghai.readergame.service.PlayerStaminaProjectionService;
import com.huazhenghai.readergame.service.QuestService;
import com.huazhenghai.readergame.service.RecoveryService;
import com.huazhenghai.readergame.service.SkillService;
import com.huazhenghai.readergame.mapper.PlayerFactionMapper;
import com.huazhenghai.readergame.mapper.PlayerFactionSkillMapper;
import com.huazhenghai.readergame.mapper.FactionSkillMapper;
import com.huazhenghai.readergame.vo.PlayerTitleVO;
import com.huazhenghai.readergame.vo.TitleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
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

    @Test
    void titleVoExposesNumericEffects() {
        PlayerTitle owned = new PlayerTitle();
        owned.setTitleKey("omniscient_reader");
        owned.setEquipped(1);
        Title definition = new Title();
        definition.setTitleKey("omniscient_reader");
        definition.setEffectsJson("{\"attack\":20,\"critRate\":0.1,\"speed\":\"fast\",\"note\":\"unknown\"}");
        when(playerTitleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(owned));
        when(titleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(definition));
        when(playerTitleMapper.selectOne(any(QueryWrapper.class))).thenReturn(owned);
        when(titleMapper.selectOne(any(QueryWrapper.class))).thenReturn(definition);

        PlayerTitleVO fromList = titleService.getPlayerTitles(1L).get(0);
        PlayerTitleVO equipped = titleService.getEquippedTitle(1L);
        for (PlayerTitleVO vo : List.of(fromList, equipped)) {
            assertEquals(20, vo.getEffects().get("attack").intValue());
            assertEquals(0.1, vo.getEffects().get("critRate").doubleValue());
            assertTrue(!vo.getEffects().containsKey("note"));
            assertEquals(List.of("speed", "note"), vo.getUncertainEffects());
            assertEquals("equipped", vo.getEffectScope());
        }
    }

    @Test
    void inactiveEffectKeysMatchAudit() {
        Map<String, Object> effects = new LinkedHashMap<>();
        for (String key : List.of("attack", "defense", "speed", "critRate", "critDamage",
                "channelHeatGainRate", "maxStamina", "maxHp", "channelHeat",
                "channelHeatBonus", "insight", "insightBonus", "narrativePressureBonus",
                "willpower")) {
            effects.put(key, 1);
        }
        PlayerTitleVO vo = new PlayerTitleVO();
        TitleEffectAudit.populate(vo, effects);

        assertEquals(List.of("channelHeat", "channelHeatBonus", "insight",
                "insightBonus", "narrativePressureBonus", "willpower"),
                vo.getInactiveEffects());
        // CombatStatsVO receives maxHp, but combat HP still comes from the saved player snapshot.
        assertEquals(List.of("maxHp"), vo.getUncertainEffects());
        assertEquals(14, vo.getEffects().size());
    }

    @Test
    void titleAttackBonusKeyNowApplies() throws Exception {
        equipSeededTitle("boss_trace_hunter");
        Player player = new Player();
        player.setId(1L);
        player.setStatsJson("{\"level\":1,\"maxHp\":100,\"hp\":100,\"attack\":10}");
        when(playerMapper.selectById(1L)).thenReturn(player);
        EquipmentService equipment = mock(EquipmentService.class);
        SkillService skills = mock(SkillService.class);
        FactionService faction = mock(FactionService.class);
        when(equipment.calculateEquipmentBonus(1L)).thenReturn(Map.of());
        when(skills.calculateSkillBonus(1L)).thenReturn(Map.of());
        when(faction.getFactionBuff(1L)).thenReturn(Map.of());
        CombatServiceImpl combat = new CombatServiceImpl(
                mock(MonsterService.class), playerMapper,
                new PlayerMaxHpProjectionService(mock(AvatarRankConfigMapper.class), new ObjectMapper()),
                mock(BattleLogMapper.class), mock(LocationMapper.class), mock(RecoveryService.class),
                mock(PlayerLogService.class), titleService, equipment, skills,
                mock(InventoryService.class), mock(BroadcastService.class), faction,
                mock(QuestService.class), new ObjectMapper(), mock(PlayerEffectService.class),
                mock(PlayerResourceTransactionService.class));

        assertEquals(3, ((Number) titleService.calculateTitleEffects(1L).get("attack")).intValue());
        assertEquals(13, combat.calculateCombatPower(1L).getAttack());
    }

    @Test
    void titleStaminaKeyNowApplies() throws Exception {
        equipSeededTitle("first_explorer");
        SkillService skills = mock(SkillService.class);
        when(skills.calculateSkillBonus(1L)).thenReturn(Map.of());
        PlayerStaminaProjectionService stamina = new PlayerStaminaProjectionService(
                skills, titleService, mock(PlayerFactionMapper.class),
                mock(PlayerFactionSkillMapper.class), mock(FactionSkillMapper.class), new ObjectMapper());

        assertEquals(5, ((Number) titleService.calculateTitleEffects(1L).get("maxStamina")).intValue());
        assertEquals(55, stamina.projectMaxStamina(1L, 50));
    }

    private void equipSeededTitle(String key) throws Exception {
        String seed;
        try (var stream = getClass().getResourceAsStream("/db/seed_growth.sql")) {
            assertTrue(stream != null, "seed_growth.sql must be on the test classpath");
            seed = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher row = Pattern.compile("(?s)\\('" + key
                + "'.*?'\\{[^']*\\}',\\s*'(\\{[^']*\\})'").matcher(seed);
        assertTrue(row.find(), "Missing seeded effects for " + key);
        PlayerTitle equipped = new PlayerTitle();
        equipped.setTitleKey(key);
        equipped.setEquipped(1);
        Title definition = new Title();
        definition.setTitleKey(key);
        definition.setEffectsJson(row.group(1));
        when(playerTitleMapper.selectOne(any(QueryWrapper.class))).thenReturn(equipped);
        when(titleMapper.selectOne(any(QueryWrapper.class))).thenReturn(definition);
    }

    @Test
    void allTitlesExposeEffectAudit() {
        Title mixed = new Title();
        mixed.setTitleKey("mixed");
        mixed.setEffectsJson("{\"attack\":20,\"insight\":5,\"maxHp\":10,\"note\":\"manual\"}");
        Title empty = new Title();
        empty.setTitleKey("empty");
        empty.setEffectsJson("{}");
        when(titleMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(mixed, empty));

        List<TitleVO> titles = titleService.getAllTitles();

        assertEquals(2, titles.size());
        TitleVO first = titles.get(0);
        assertEquals(20, ((Number) first.getEffects().get("attack")).intValue());
        assertEquals("manual", first.getEffects().get("note"));
        assertEquals(List.of("insight"), first.getInactiveEffects());
        assertEquals(List.of("maxHp", "note"), first.getUncertainEffects());
        assertEquals("equipped", first.getEffectScope());
        TitleVO second = titles.get(1);
        assertTrue(second.getEffects().isEmpty());
        assertEquals(List.of(), second.getInactiveEffects());
        assertEquals(List.of(), second.getUncertainEffects());
        assertEquals("equipped", second.getEffectScope());
    }
}
