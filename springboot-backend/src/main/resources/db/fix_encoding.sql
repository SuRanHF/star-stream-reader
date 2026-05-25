-- 修复乱码：删除并重新插入地点数据
-- 使用 MySQL 客户端前先执行: SET NAMES utf8mb4;

DELETE FROM `locations`;

INSERT INTO `locations` (`location_key`, `name`, `description`, `unlock_conditions_json`, `event_rates_json`, `min_level`, `danger_level`, `recommended_rank`, `is_default`) VALUES
('ruined_station', '废弃车站', '地铁三号线的废墟，倒塌的天花板下散落着使用过的票根与干涸的血迹。新晋角色最安全的起点。', '{}', '{"story":20,"resource":30,"opportunity":15,"boss_clue":10,"empty":25}', 1, 2, 'F', 1),
('broken_mall', '断裂商场', '资源和遭遇更多的危险区域，断裂的混凝土之间偶尔能看到发光的物品。', '{"minLevel":1}', '{"story":15,"resource":40,"opportunity":15,"boss_clue":5,"empty":25}', 1, 4, 'F', 0),
('underground_city', '地下城市', '人类最后的庇护所。墙壁上的涂鸦记录着场景降临那天的恐慌。有人写下了希望，有人写下了遗言。', '{"minLevel":3}', '{"story":20,"resource":25,"opportunity":20,"boss_clue":10,"empty":25}', 3, 5, 'E', 0),
('sky_castle', '天空城堡', '漂浮在云端的古堡，星座的观测者们在此聚会。穿过这里的化身将获得背后的星座青睐。', '{"minLevel":5}', '{"story":30,"resource":15,"opportunity":25,"boss_clue":15,"empty":15}', 5, 6, 'D', 0),
('creature_forest', '魔物森林', '密林深处隐藏着强大的魔物与星流辐射产生的异变体。每一棵树都在注视着闯入者。', '{"minLevel":4}', '{"story":15,"resource":35,"opportunity":15,"boss_clue":10,"empty":25}', 4, 5, 'D', 0),
('abyss_rift', '深渊裂隙', '世界线的裂缝，从这里能窥见其他时间线。深渊的凝视者在黑暗中低语。', '{"minLevel":7}', '{"story":40,"resource":15,"opportunity":20,"boss_clue":15,"empty":10}', 7, 8, 'C', 0),
('broken_market', '断裂集市', '比断裂商场更危险的废墟区，资源虽多但风险也更高。断裂的混凝土之间偶尔能看到发光的物品。', '{"minLevel":2}', '{"story":10,"resource":45,"opportunity":15,"boss_clue":5,"empty":25}', 2, 4, 'F', 0),
('silent_library', '沉默图书馆', '无尽的书架延伸到看不到尽头的黑暗中。这里的每一本书都记录着某条世界线上发生过的故事。', '{"minLevel":6}', '{"story":50,"resource":10,"opportunity":20,"boss_clue":5,"empty":15}', 6, 7, 'C', 0),
('collapsed_bridge', '断裂之桥', '连接两个区域的桥梁已经断裂，桥下的黑水中漂浮着过往的碎片。', '{"minLevel":5}', '{"story":25,"resource":20,"opportunity":20,"boss_clue":10,"empty":25}', 5, 6, 'C', 0),
('black_channel_zone', '黑频道地带', '废弃的星座频道区域，这里曾是鬼怪们播出故事的地方。如今只有静电噪音和破碎的信号。', '{"minLevel":8}', '{"story":35,"resource":20,"opportunity":25,"boss_clue":15,"empty":5}', 8, 9, 'B', 0),
('final_scenario_gate', '终章之门', '所有世界线在此交汇。门上刻着古老的文字：「选择吧——每一道门后面都是一个结局。」', '{"minLevel":10}', '{"story":50,"resource":5,"opportunity":25,"boss_clue":20,"empty":0}', 10, 10, 'A', 0);
