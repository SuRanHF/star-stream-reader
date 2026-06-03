package com.huazhenghai.readergame.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.*;

import com.huazhenghai.readergame.entity.*;
import com.huazhenghai.readergame.mapper.*;
import com.huazhenghai.readergame.service.*;
import com.huazhenghai.readergame.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 战斗系统单元测试：位格压制 + 叙事压制.
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceImplTest {

    @Mock private MonsterService monsterService;
    @Mock private PlayerMapper playerMapper;
    @Mock private BattleLogMapper battleLogMapper;
    @Mock private LocationMapper locationMapper;
    @Mock private TitleMapper titleMapper;
    @Mock private RecoveryService recoveryService;
    @Mock private PlayerLogService playerLogService;
    @Mock private TitleService titleService;
    @Mock private EquipmentService equipmentService;
    @Mock private SkillService skillService;
    @Mock private InventoryService inventoryService;
    @Mock private BroadcastService broadcastService;
    @Mock private FactionService factionService;
    @Mock private QuestService questService;

    private CombatServiceImpl combatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        combatService = new CombatServiceImpl(
                monsterService, playerMapper, battleLogMapper,
                locationMapper, titleMapper,
                recoveryService, playerLogService,
                titleService, equipmentService, skillService,
                inventoryService, broadcastService,
                factionService, questService,
                objectMapper);
    }

    // ═══════════════════════════════════════════════════════════════
    // 位格压制
    // ═══════════════════════════════════════════════════════════════

    @Test
    void rankToValue_Fis1() throws Exception {
        assertEquals(1, invokeRankToValue("F"));
    }

    @Test
    void rankToValue_Dis3() throws Exception {
        assertEquals(3, invokeRankToValue("D"));
    }

    @Test
    void rankToValue_SSSis9() throws Exception {
        assertEquals(9, invokeRankToValue("SSS"));
    }

    @Test
    void rankToValue_nullDefaultsTo1() throws Exception {
        assertEquals(1, invokeRankToValue(null));
    }

    @Test
    void rankToValue_unknownDefaultsTo1() throws Exception {
        assertEquals(1, invokeRankToValue("UNKNOWN"));
    }

    @Test
    void rankSuppressionAdvantage() throws Exception {
        // F(1) vs F(1) → diff 0 → mult 1.0
        double mult = invokeRankSuppression("F", "F");
        assertEquals(1.0, mult, 0.001);

        // D(3) vs F(1) → diff 2 → 1.0 + 2*0.15 = 1.3
        mult = invokeRankSuppression("D", "F");
        assertEquals(1.3, mult, 0.001);

        // S(7) vs F(1) → diff 6 → 1.0 + 6*0.15 = 1.9
        mult = invokeRankSuppression("S", "F");
        assertEquals(1.9, mult, 0.001);
    }

    @Test
    void rankSuppressionDisadvantage() throws Exception {
        // F(1) vs D(3) → diff -2 → 1.0 + (-2)*0.15 = 0.7
        double mult = invokeRankSuppression("F", "D");
        assertEquals(0.7, mult, 0.001);

        // F(1) vs S(7) → diff -6 → 1.0 + (-6)*0.15 = 0.1, clamped to 0.5
        mult = invokeRankSuppression("F", "S");
        assertEquals(0.5, mult, 0.001);
    }

    @Test
    void rankSuppressionCappedAt3() throws Exception {
        // F(1) vs SSS(9) → diff -8 → 1.0 + (-8)*0.15 = -0.2, clamped to 0.5
        double mult = invokeRankSuppression("F", "SSS");
        assertEquals(0.5, mult, 0.001);

        // SSS(9) vs F(1) → diff 8 → 1.0 + 8*0.15 = 2.2 (under 3.0)
        mult = invokeRankSuppression("SSS", "F");
        assertEquals(2.2, mult, 0.001);
    }

    @Test
    void rankSuppressionCappedAt05() throws Exception {
        // Worst case: F vs SSS = clamp(-0.2, 0.5, 3.0) = 0.5
        double mult = invokeRankSuppression("F", "SSS");
        assertEquals(0.5, mult, 0.001);
    }

    // ═══════════════════════════════════════════════════════════════
    // 叙事压制
    // ═══════════════════════════════════════════════════════════════

    @Test
    void narrativeSuppressionStrongAgainst() throws Exception {
        // attacker strong = ["abyss"], defender tags = ["abyss"] → 1.4x
        double mult = invokeNarrativeSuppression(
                List.of("abyss"), List.of(), List.of("abyss"));
        assertEquals(1.4, mult, 0.001);
    }

    @Test
    void narrativeSuppressionWeakAgainst() throws Exception {
        // attacker weak = ["abyss"], defender tags = ["abyss"] → 0.6x
        double mult = invokeNarrativeSuppression(
                List.of(), List.of("abyss"), List.of("abyss"));
        assertEquals(0.6, mult, 0.001);
    }

    @Test
    void narrativeSuppressionNeutral() throws Exception {
        // No match → 1.0x
        double mult = invokeNarrativeSuppression(
                List.of("reader"), List.of("combat"), List.of("abyss"));
        assertEquals(1.0, mult, 0.001);
    }

    @Test
    void narrativeSuppressionEmptyDefender() throws Exception {
        double mult = invokeNarrativeSuppression(
                List.of("abyss"), List.of(), Collections.emptyList());
        assertEquals(1.0, mult, 0.001);
    }

    @Test
    void narrativeSuppressionReaderCountersAbyss() throws Exception {
        // reader title strong_against = ["abyss","king","starstream"]
        // monster tags = ["abyss","corrupted"]
        // → strong match on "abyss" → 1.4x
        double mult = invokeNarrativeSuppression(
                List.of("abyss", "king", "starstream"),
                List.of(),
                List.of("abyss", "corrupted"));
        assertEquals(1.4, mult, 0.001);
    }

    @Test
    void narrativeSuppressionKingWeakToReader() throws Exception {
        // king title weak_against = ["reader"]
        // monster tags = ["reader"]
        // → weak match on "reader" → 0.6x
        double mult = invokeNarrativeSuppression(
                List.of("king", "starstream"),
                List.of("reader"),
                List.of("reader"));
        assertEquals(0.6, mult, 0.001);
    }

    // ═══════════════════════════════════════════════════════════════
    // 伤害计算（含压制倍率）
    // ═══════════════════════════════════════════════════════════════

    @Test
    void calcDamageBaseFormula() throws Exception {
        // atk=10, def=5 → baseDmg = max(1, round(10 - 5*0.5)) = 8
        // no crit, mult=1.0
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "");
        assertEquals(8.0, ((Number) result.get("damage")).doubleValue(), 0.001);
        assertEquals(false, result.get("critical"));
    }

    @Test
    void calcDamageWithSuppressionMult() throws Exception {
        // atk=10, def=5 → base=8, mult=1.4 → 11
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.4, "");
        assertEquals(11.0, ((Number) result.get("damage")).doubleValue(), 0.001);
    }

    @Test
    void calcDamageWithSuppressionNote() throws Exception {
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "位格压制");
        assertTrue(result.containsKey("suppressionNote"));
        assertEquals("位格压制", result.get("suppressionNote"));
    }

    @Test
    void calcDamageSuppressionNoteEmptyWhenEmptyString() throws Exception {
        Map<String, Object> result = invokeCalcDamage(10, 5, 0.0, 1.5, 1.0, "");
        assertFalse(result.containsKey("suppressionNote"));
    }

    @Test
    void calcDamageMinimum1() throws Exception {
        // atk=1, def=100 → base = max(1, 1 - 50) = 1, mult=0.6 → 0.6, max(1, ...) = 1
        Map<String, Object> result = invokeCalcDamage(1, 100, 0.0, 1.5, 0.6, "");
        double dmg = ((Number) result.get("damage")).doubleValue();
        assertTrue(dmg >= 1.0, "damage should never be below 1, got " + dmg);
    }

    @Test
    void calcDamageCriticalHit() throws Exception {
        // atk=20, def=0 → base=20, critRate=1.0 (always), critDamage=2.0 → 40*1.0=40
        Map<String, Object> result = invokeCalcDamage(20, 0, 1.0, 2.0, 1.0, "");
        assertEquals(true, result.get("critical"));
        assertEquals(40.0, ((Number) result.get("damage")).doubleValue(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════
    // JSON 解析辅助
    // ═══════════════════════════════════════════════════════════════

    @Test
    void parseStringListValidJson() throws Exception {
        List<String> result = invokeParseStringList("[\"abyss\",\"corrupted\"]");
        assertEquals(2, result.size());
        assertEquals("abyss", result.get(0));
        assertEquals("corrupted", result.get(1));
    }

    @Test
    void parseStringListNullReturnsEmpty() throws Exception {
        List<String> result = invokeParseStringList(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void parseStringListEmptyReturnsEmpty() throws Exception {
        List<String> result = invokeParseStringList("");
        assertTrue(result.isEmpty());
        result = invokeParseStringList("null");
        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Reflection helpers
    // ═══════════════════════════════════════════════════════════════

    private int invokeRankToValue(String rank) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod("rankToValue", String.class);
        m.setAccessible(true);
        return (int) m.invoke(combatService, rank);
    }

    private double invokeRankSuppression(String playerRank, String monsterRank) throws Exception {
        int playerVal = invokeRankToValue(playerRank);
        int monsterVal = invokeRankToValue(monsterRank);
        int diff = playerVal - monsterVal;
        return Math.max(0.5, Math.min(3.0, 1.0 + diff * 0.15));
    }

    private double invokeNarrativeSuppression(
            List<String> strongAgainst,
            List<String> weakAgainst,
            List<String> defenderTags) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod(
                "narrativeSuppressionMultiplier",
                List.class, List.class, List.class);
        m.setAccessible(true);
        return (double) m.invoke(combatService, strongAgainst, weakAgainst, defenderTags);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeCalcDamage(
            int atk, int def, double critRate, double critDamage,
            double suppressionMult, String suppressionNote) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod(
                "calcDamage", int.class, int.class, double.class, double.class,
                double.class, String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(
                combatService, atk, def, critRate, critDamage,
                suppressionMult, suppressionNote);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeParseStringList(String json) throws Exception {
        Method m = CombatServiceImpl.class.getDeclaredMethod("parseStringList", String.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(combatService, json);
    }
}
