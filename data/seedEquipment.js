// 全知读者视角 装备种子数据

function seedEquipment(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO equipment (equipment_key, name, description, slot, rarity, stats_json, effects_json, required_level, title_synergy_json)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const equipment = [
    // ===== Weapons =====
    ['rusty_dagger', '生锈短刀', '一把从废弃车站的储物柜中发现的小刀，刀刃上密布锈迹，但握在手中时仍能感受到一种莫名的心安。', 'weapon', 'common',
      '{"attack":8}', '{}', 1, '{}'],
    ['script_execution_blade', '剧本执行刃', '执行者们的标准装备，刃身闪烁着淡淡的蓝色字符光芒，击中目标时会在对方身上短暂浮现被删除的文本。', 'weapon', 'rare',
      '{"attack":20,"speed":3}', '{"bonus_vs_executor":{"damage_multiplier":1.2}}', 8, '{}'],
    ['nameless_crown', '无名王冠', '一顶没有刻写任何名字的王冠，戴上的瞬间会感到万千故事的重量，但同时也获得对任何低于你的存在施加不可抗拒的压制力。', 'weapon', 'legendary',
      '{"attack":35,"defense":10,"hp":100}', '{"king_pressure":"对等级低于自身的敌人伤害增加30%"}', 22, '{"title_absolute_throne":{"attack_bonus":20}}'],

    // ===== Armor =====
    ['station_guard_coat', '车站守卫外套', '一件厚重的深蓝色工作外套，背后印有褪色的地铁标志。虽然款式老旧，但厚实的面料能挡下不少伤害。', 'armor', 'common',
      '{"defense":8,"hp":20}', '{}', 1, '{}'],
    ['observer_longcoat', '观测者长衣', '一件灰色长外套，内衬缝满了无数个口袋，每个口袋里都有前代主人留下的手写便条。穿上时会感觉有人在背后低声指点。', 'armor', 'rare',
      '{"defense":18,"hp":50}', '{"dodge_chance":0.08}', 10, '{}'],
    ['black_gloves', '黑色手套', '一双由黑色频道主持者残留物提炼而成的紧致手套，指尖触及之处会短暂出现频道切换的残像。', 'armor', 'epic',
      '{"defense":25,"attack":5,"speed":5}', '{"counter_damage_pct":0.10}', 16, '{}'],

    // ===== Accessories =====
    ['old_reader_badge', '旧读者徽章', '一个刻着翻开书本图案的铜色徽章，佩戴时会隐约听到书页翻动的声音，仿佛有人在和你一起阅读这段故事。', 'accessory', 'common',
      '{"hp":30,"attack":3}', '{}', 1, '{}'],
    ['channel_headset', '频道耳机', '一只被改造过的耳机，戴上后能同时收听到多个废弃频道的广播。赞助者们的声音偶尔会从噪声中清晰起来。', 'accessory', 'rare',
      '{"speed":8,"attack":5}', '{"coin_bonus_percent":0.15}', 10, '{}'],
    ['fallen_star_badge', '失落星徽', '一枚由星屑粉末凝炼而成的胸针，徽面雕刻着已失落的星座符号。当你的名字被赞助者提及时，它会发出微弱的共鸣。', 'accessory', 'epic',
      '{"attack":12,"defense":8,"speed":5}', '{"constellation_resonance":"获得赞助时额外+8%全属性2回合"}', 15, '{}'],

    // ===== Relics =====
    ['worldline_compass', '世界线罗盘', '一个古旧的青铜罗盘，指针不会指向南北，而是指向当前世界线偏移最小的方向。在探索时有助于避开最糟糕的事件。', 'relic', 'rare',
      '{"speed":6}', '{"exploration_safety":"降低遭遇灾难性随机事件的概率20%"}', 8, '{}'],
    ['final_chapter_page', '终章残页', '写着终章结局片段的一页纸，纸面呈现不自然的金色光泽，字迹在每次阅读后都会微微改变顺序。', 'relic', 'epic',
      '{"attack":10,"defense":10,"hp":80}', '{"boss_preview":"战斗开始时揭示首领的弱点属性","boss_damage_bonus":0.15}', 16, '{}'],
    ['throne_fragment', '王座碎片', '无名王座破碎后留下的一块金属碎片，表面刻着已无法辨认的铭文。握住时会感到一股无形的力量将你向上托举。', 'relic', 'legendary',
      '{"attack":18,"defense":15,"hp":150,"speed":5}', '{"throne_authority":"对等级低于你的敌人伤害额外+25%"}', 20, '{"title_absolute_throne":{"all_stats_boost":12}}'],
  ];

  for (const eq of equipment) {
    insert.run(...eq);
  }
  console.log('Equipment seed data inserted.');
}

module.exports = { seedEquipment };
