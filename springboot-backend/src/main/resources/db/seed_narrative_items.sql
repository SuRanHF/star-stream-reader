-- NPC 残影对话奖励；这些条目过去只写入 player_inventory，因 items 表缺少定义而不会显示在背包里。
-- 所有奖励按叙事收藏品处理，不可使用/出售；单角色仅保留一件，避免重复遭遇刷取同一遗物。
INSERT IGNORE INTO `items`
(`item_key`, `name`, `item_type`, `rarity`, `description`, `effects_json`, `consume_on_use`, `sell_price`, `max_stack`, `enabled`)
VALUES
('dream_shard', '梦境碎片', 'narrative', 'uncommon', '白发读者递来的梦境残片，触碰时浮现出尚未发生的章节。', NULL, 0, 0, 1, 1),
('final_key_fragment', '终章钥匙碎片', 'narrative', 'rare', '从未完结的书页间脱落的钥匙碎片，似乎指向某个故事的终点。', NULL, 0, 0, 1, 1),
('dragon_blood_potion', '龙血药剂', 'narrative', 'rare', '钢铁指挥官赠予的黑红色药剂，瓶中回荡着古龙的低鸣。', NULL, 0, 0, 1, 1),
('black_flame_gauntlet', '黑焰手套', 'narrative', 'epic', '黑焰龙将留下的手套，掌心仍残存守卫地下城千年的温度。', NULL, 0, 0, 1, 1),
('dragon_crest', '黑龙徽章', 'narrative', 'epic', '刻着黑龙的徽章，提醒持有者：活下来就是胜利。', NULL, 0, 0, 1, 1),
('abyss_core_shard', '深渊之核碎片', 'narrative', 'epic', '来自深渊之核的黑色结晶，内部仿佛封存着一个未说出口的愿望。', NULL, 0, 0, 1, 1),
('story_ember', '故事余烬', 'narrative', 'uncommon', '火之审判者留下的一点余烬，映出一段仍在燃烧的故事。', NULL, 0, 0, 1, 1),
('dead_lotus_flower', '沉睡黑莲', 'narrative', 'rare', '在冥界沉睡千年的黑莲，花瓣深处藏着微弱的银色光芒。', NULL, 0, 0, 1, 1),
('readers_last_word', '读者遗言', 'narrative', 'epic', '一位先行读者留下的最后一句话，黑色文字仍带着温度。', NULL, 0, 0, 1, 1),
('silver_lotus_lantern', '银莲灯', 'narrative', 'epic', '由枯萎黑莲化成的银色灯火，在意义消失时仍会为故事照亮一页。', NULL, 0, 0, 1, 1),
('small_golden_staff', '小金箍棒', 'narrative', 'epic', '齐天大圣交给你的缩小版金箍棒，金光中留着一声爽朗的笑。', NULL, 0, 0, 1, 1),
('nimbus_ring', '觔斗云契约戒指', 'narrative', 'epic', '金色指环里封存着一份不太稳定的觔斗云契约。', NULL, 0, 0, 1, 1),
('golden_monkey_hair', '金色猴毛', 'narrative', 'rare', '齐天大圣留下的一根金色猴毛，触碰后化为永不褪色的枫叶。', NULL, 0, 0, 1, 1),
('broken_guitar_string', '断裂琴弦', 'narrative', 'common', '流浪吟游诗人旧琴上的断弦，仍残留着一段未唱完的旋律。', NULL, 0, 0, 1, 1),
('silver_bard_string', '银色琴弦', 'narrative', 'rare', '被故事祝福过的银色琴弦，轻轻拨动便响起远行者的歌。', NULL, 0, 0, 1, 1),
('dying_star_vial', '垂星药瓶', 'narrative', 'epic', '最古之梦交给你的药瓶，瓶底封存着一颗正在熄灭的星辰。', NULL, 0, 0, 1, 1),
('regression_blade_fragment', '回归之刃碎片', 'narrative', 'rare', '刘众赫记忆中的刀刃碎片，边缘映出无数次重来的轨迹。', NULL, 0, 0, 1, 1),
('hsy_draft_page', '韩秀英手稿页', 'narrative', 'rare', '韩秀英写下的新一页手稿，末尾留着一句：故事从未结束。', NULL, 0, 0, 1, 1),
('empty_memory_vial', '空记忆瓶', 'narrative', 'common', '收藏家交给你的空瓶子，据说能装下一段尚未发生的记忆。', NULL, 0, 0, 1, 1),
('last_train_ticket', '末班车车票', 'narrative', 'rare', '最后一班列车的车票，背面印着一个尚未抵达的站名。', NULL, 0, 0, 1, 1),
('narrative_forged_weapon', '叙事锻造武器', 'narrative', 'epic', '烙印铁匠锻造的武器，刃面上刻着属于持有者的故事。', NULL, 0, 0, 1, 1);
