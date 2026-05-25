-- ============================================================
-- 合成系统所需的物品/装备定义
-- 这些 item_key 被合成配方引用但之前没有在 items/equipment 表中定义
-- ============================================================

-- 消耗品：生命药剂系列
INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('small_hp_potion', '小瓶生命药剂', 'consumable', 'common',
 '廉价的再生药剂。瓶身上贴着"星流制药"的标签。使用后恢复少量生命值。',
 '{"heal_hp":50}',
 1, 10, 30, 1),
('medium_hp_potion', '中瓶生命药剂', 'consumable', 'common',
 '较为精制的生命药剂，瓶内液体微微发光。使用后恢复一定生命值。',
 '{"heal_hp":100}',
 1, 15, 20, 1),
('large_hp_potion', '大瓶生命药剂', 'consumable', 'uncommon',
 '闪耀星流光芒的大瓶药剂，瓶口处萦绕着淡金色雾气。使用后恢复大量生命值。',
 '{"heal_hp":200}',
 1, 25, 10, 1);

-- 消耗品：活力丹
INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('stamina_pill', '活力丹', 'consumable', 'uncommon',
 '由圣水与魔物骨骸炼制而成的丹药，服用后能大幅恢复行动力。',
 '{"restore_stamina":50}',
 1, 20, 15, 1);

-- 消耗品：故事卷轴
INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('story_scroll', '故事卷轴', 'consumable', 'rare',
 '由故事碎片凝聚而成的卷轴，展开后能看到未知世界线的剧情片段。使用后获得故事碎片。',
 '{"story_fragments":25}',
 1, 40, 5, 1);

-- 消耗品：深渊秘药
INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('abyss_elixir', '深渊秘药', 'consumable', 'epic',
 '深渊碎片与故事卷轴炼化的秘药，漆黑如墨却散发着不可名状的吸引力。使用后获得故事碎片与深渊印记。',
 '{"story_fragments":10,"abyss_mark":25}',
 1, 60, 3, 1);

-- 材料：合成所需材料
INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('monster_bone', '魔物骨骸', 'material', 'common',
 '魔物体内的骨骸碎片，表面有微弱的星流纹路。合成丹药的原料。',
 NULL,
 0, 8, 50, 1),
('purification_water', '圣水', 'material', 'uncommon',
 '来自地下城市深处的地下水，据说经过了多条世界线的净化。合成丹药的原料。',
 NULL,
 0, 12, 30, 1),
('story_fragment', '故事碎片', 'material', 'common',
 '星流频道中逸散的故事片段，收集到足够多才能凝聚为卷轴。',
 NULL,
 0, 5, 99, 1),
('abyss_shard', '深渊碎片', 'material', 'rare',
 '深渊中掉落的黑色晶体碎片，触碰时会听到低沉的耳语。炼化秘药的原料。',
 NULL,
 0, 25, 20, 1);

-- 装备：合成装备结果
INSERT IGNORE INTO `equipment` (`equipment_key`, `name`, `slot`, `rarity`, `description`, `base_stats_json`, `special_effects_json`, `max_durability`, `repair_cost`, `sell_price`, `enabled`)
VALUES
('hunter_blade', '猎人短刃', 'weapon', 'uncommon',
 '由生锈短刀与猎人的戒指融合锻造而成的短刃，锋刃上流淌着猎手的意志。',
 '{"attack":8,"speed":3}',
 NULL,
 60, 25, 50, 1),
('rusty_dagger', '生锈短刀', 'weapon', 'common',
 '一把锈迹斑斑的短刀，但刃口依然勉强能用。合成猎人短刃的材料。',
 '{"attack":3}',
 NULL,
 40, 15, 20, 1),
('hunters_ring', '猎人的戒指', 'accessory', 'common',
 '一枚朴实无华的戒指，内圈刻着"追上它"三个字。合成猎人短刃的材料。',
 '{"attack":5,"speed":2}',
 NULL,
 50, 20, 30, 1);
