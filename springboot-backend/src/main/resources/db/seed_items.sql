-- ============================================================
-- Phase 4 物品系统种子数据
-- 6 个初始物品
-- ============================================================

INSERT IGNORE INTO `items` (`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES

-- 1. 体力面包 (消耗品)
('stamina_bread', '体力面包', 'consumable', 'common',
 '一片保存尚可的面包，上面刻着微弱的星流符文。食用后恢复体力。',
 '{"restore_stamina":30}',
 1, 5, 20, 1),

-- 2. 急救绷带 (消耗品)
('first_aid_bandage', '急救绷带', 'consumable', 'common',
 '简易急救用品，可以止血镇痛。使用后恢复生命值。',
 '{"heal_hp":50}',
 1, 8, 15, 1),

-- 3. 故事碎片核心 (消耗品)
('story_fragment_core', '故事碎片核心', 'consumable', 'uncommon',
 '从频道深处凝聚的故事碎片结晶，触碰时能看到被遗忘的剧情片段。',
 '{"story_fragments":15}',
 1, 20, 10, 1),

-- 4. 频道代币 (消耗品)
('channel_token', '频道代币', 'consumable', 'uncommon',
 '一枚闪着微光的代币，上面有频道号码的刻印。使用后获得频道热度。',
 '{"channel_heat":8}',
 1, 15, 10, 1),

-- 5. 看门人的痕迹 (消耗品)
('station_keeper_trace', '看门人的痕迹', 'consumable', 'rare',
 '废弃车站看门人留下的痕迹，蕴含着故事碎片与频道热度。',
 '{"story_fragments":10,"channel_heat":10}',
 1, 30, 5, 1),

-- 6. 深渊残留物 (消耗品)
('abyss_residue', '深渊残留物', 'consumable', 'epic',
 '深渊中残留的黑色晶体碎片，触碰时会听到低沉的耳语。使用后获得故事碎片与深渊印记。',
 '{"story_fragments":10,"abyss_mark":20}',
 1, 50, 3, 1);
