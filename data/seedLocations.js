// 全知读者视角 探索地图种子数据 (Round 5: exploration event system)

function seedLocations(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO locations (location_key, name, description, unlock_conditions_json, monster_pool_json, event_pool_json, min_level, danger_level, drop_rate_modifier, event_probabilities_json)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const locations = [
    ['ruined_station', '废弃车站',
      '地铁三号线的废墟，倒塌的天花板下散落着使用过的票根与干涸的血迹。低阶化身在此徘徊，是新晋角色最安全的起点。',
      '{}',
      '["rogue_avatar","subway_wanderer","hungry_follower"]',
      '["垃圾堆中发现旧地铁票","墙上有前人刻下的攻略提示","管道中传来微弱的哭声","地面突然震动，天花板的碎屑落下"]',
      1, 2, 0.8,
      '{"story":0.20,"side_story":0.05,"battle":0.35,"elite_battle":0.02,"boss_clue":0.03,"opportunity":0.10,"resource":0.15,"hidden":0.03,"nothing":0.07}'],
    ['broken_market', '断裂商场',
      '曾经的购物天堂，如今只剩下被推倒的货架和争夺物资的幸存者。商场深处的广播仍在循环播放一首无人记得的歌。',
      '{"required_flags":{"entered_castle":true}}',
      '["hungry_follower","market_raider","scenario_shade"]',
      '["货架后面发现未开封的罐头","突然触发了商场的防盗铃","一个幸存商人提出以物易物","天花板上的霓虹灯管突然炸裂"]',
      3, 4, 0.9,
      '{"story":0.15,"side_story":0.08,"battle":0.35,"elite_battle":0.05,"boss_clue":0.05,"opportunity":0.15,"resource":0.10,"hidden":0.02,"nothing":0.05}'],
    ['silent_library', '静默图书馆',
      '一座被遗忘的城市图书馆，书本散落一地，但所有文字都在持续消失。传说这里埋藏着关于剧本本质的秘密。',
      '{"required_titles":["title_watcher"]}',
      '["scenario_shade","dark_mist_parasite","channel_corruption"]',
      '["一本翻开的书页突然浮现文字","书架间传来沙沙的低语声","发现一本记载着隐藏路线的笔记","盲眼管理员出现在阅览室深处","天花板滴落的液体腐蚀了地板"]',
      5, 5, 1.0,
      '{"story":0.12,"side_story":0.10,"battle":0.18,"elite_battle":0.03,"boss_clue":0.06,"opportunity":0.12,"resource":0.10,"hidden":0.20,"nothing":0.09}'],
    ['collapsed_bridge', '坍塌大桥',
      '横跨汉江的巨型桥梁从中间折断，钢筋裸露如断裂的骨骼。桥下的黑水中据说什么都能映照出来。',
      '{"required_flags":{"stopped_yjh":true}}',
      '["channel_corruption","old_rule_remnant","dark_mist_parasite","low_executor"]',
      '["桥面突然坍塌，必须迅速跳开","黑水中浮现出一件闪亮的遗物","对岸远远出现一个人影向你招手","狂风骤起，世界线偏移度+2"]',
      8, 7, 1.1,
      '{"story":0.10,"side_story":0.08,"battle":0.35,"elite_battle":0.10,"boss_clue":0.10,"opportunity":0.10,"resource":0.08,"hidden":0.05,"nothing":0.04}'],
    ['black_channel_zone', '黑色频道区',
      '废弃的电视台大楼，所有屏幕都亮着雪花点。头顶的星座们通过屏幕注视着这里，赞助者们在此投下它们的目光。',
      '{"required_flags":{"investigating_dream":true}}',
      '["low_executor","disconnected_avatar","stardust_infected"]',
      '["频道中传来赞助者的低语","屏幕显示出一段关于你死亡的预言","星座赞助弹出提示框","导播台突然启动，切换到一个未知场景"]',
      12, 8, 1.3,
      '{"story":0.08,"side_story":0.10,"battle":0.30,"elite_battle":0.08,"boss_clue":0.10,"opportunity":0.15,"resource":0.08,"hidden":0.10,"nothing":0.01}'],
    ['final_scenario_gate', '终章之门',
      '一道由文字本身构筑的巨大门扉悬浮在城市上空，门面刻满了所有角色的结局。唯有集齐终章钥匙者方可进入。',
      '{"required_flags":{"entered_castle":true},"required_level":20}',
      '["throne_guard","fallen_sponsor","disconnected_avatar"]',
      '["门上的文字开始发光并重新排列","所有赞助者的目光集中在这里","门后传来世界线的召唤","终章守门人睁开眼睛"]',
      20, 10, 1.5,
      '{"story":0.08,"side_story":0.05,"battle":0.25,"elite_battle":0.12,"boss_clue":0.15,"opportunity":0.12,"resource":0.08,"hidden":0.10,"nothing":0.05}'],
    // ── Phase 7: 四大秘境 ──
    ['underground_city', '地下都市',
      '人类最后的地下庇护所。墙壁上的涂鸦记录着"场景"降临那天的恐慌。空气潮湿而沉闷，每一盏故障灯管的闪烁都像是一次无声的求救。',
      '{"min_level":3}',
      '[]',
      '[]',
      3, 3, 0.95,
      '{"story":0.10,"side_story":0.05,"battle":0.28,"elite_battle":0.04,"boss_clue":0.03,"opportunity":0.10,"resource":0.20,"hidden":0.05,"nothing":0.15}'],
    ['sky_castle', '天空城堡',
      '高耸入云的废弃古堡。走廊里每一面镜子映出的都不是你的倒影，而是平行世界线中另一个你的选择。被遗忘的贵族画像在墙上低声争论着早已无人关心的王位继承权。',
      '{"min_level":8}',
      '[]',
      '[]',
      8, 6, 1.10,
      '{"story":0.08,"side_story":0.04,"battle":0.30,"elite_battle":0.06,"boss_clue":0.04,"opportunity":0.12,"resource":0.12,"hidden":0.12,"nothing":0.12}'],
    ['creature_forest', '生物森林',
      '密林深处的古树上刻着韩文："独子啊，妈妈在这个世界线终于找到你了。"森林有它自己的记忆——每一片叶子都在复述着曾经发生在这里的冒险。星座投射出的神话生物在阴影中徘徊。',
      '{"min_level":10}',
      '[]',
      '[]',
      10, 7, 1.15,
      '{"story":0.12,"side_story":0.06,"battle":0.25,"elite_battle":0.06,"boss_clue":0.05,"opportunity":0.10,"resource":0.18,"hidden":0.08,"nothing":0.10}'],
    ['abyss_rift', '深渊裂隙',
      '时间的流速在此处变得不稳定——你手腕上的表在向前走，而墙上的钟在向后退。深渊深处传来打字机的声音，一下一下敲在你的心跳上。这里是故事的墓地：每一个被遗忘的结局都被葬在这里。',
      '{"min_level":15}',
      '[]',
      '[]',
      15, 9, 1.25,
      '{"story":0.15,"side_story":0.08,"battle":0.22,"elite_battle":0.08,"boss_clue":0.04,"opportunity":0.08,"resource":0.12,"hidden":0.10,"abyss_whisper":0.13,"nothing":0.00}'],
  ];

  for (const l of locations) {
    insert.run(...l);
  }
  console.log('Location seed data inserted.');
}

module.exports = { seedLocations };
