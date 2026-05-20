// 全知读者视角 道具种子数据

function seedItems(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO items (item_key, name, description, type, rarity, effects_json, stackable, sell_price)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const items = [
    // Consumables
    ['small_hp_potion', '小型恢复剂', '剧本商店里最基础的恢复道具，喝下后会有一阵暖流经过全身，小伤口在几秒内愈合。', 'consumable', 'common', '{"heal_hp":30}', 1, 15],
    ['broken_coin_bag', '破损硬币袋', '一个破旧的布袋，里面还剩下几枚沾灰的硬币，勉强能听到硬币碰撞的声音。', 'consumable', 'common', '{"coins_random":{"min":50,"max":100}}', 1, 30],
    ['channel_jammer', '频道干扰器', '一个巴掌大的黑色装置，启动后会发出刺耳的频率，暂时阻断频道对当前位置的监视。', 'consumable', 'rare', '{"escape_battle":true}', 1, 80],
    ['low_sponsor_token', '低阶赞助凭证', '一张印有星座纹样的金属卡片，使用后下次战斗获得的硬币会增加两成。', 'consumable', 'uncommon', '{"coin_boost_percent":20,"duration_battles":1}', 0, 60],
    ['worldline_stabilizer', '世界线稳定片', '一颗半透明的胶囊，内含着微光流转的液体。服用后能够让你的世界线偏移值下降五个点。', 'consumable', 'rare', '{"world_line_shift":-5}', 1, 100],
    ['stamina_pill', '体力恢复药', '一粒散发出薄荷气息的绿色药丸，能在片刻之间驱散身体深处的疲惫感。', 'consumable', 'common', '{"restore_stamina":15}', 1, 20],
    ['temporary_talisman', '临时护符', '一个画着简易符文的小木牌，佩戴后会在体表形成一层薄弱的防护场，持续三次战斗。', 'consumable', 'uncommon', '{"defense_bonus":10,"duration_battles":3}', 0, 50],
    ['emergency_escape', '紧急撤离券', '一张印有出口标志的纸条，撕碎后立刻被传送回安全区域。', 'consumable', 'rare', '{"instant_leave_exploration":true}', 1, 120],

    // Materials
    ['story_scrap', '故事残页', '一片从剧本中撕下的残页，上面的文字在缓慢地流动和重组。', 'material', 'common', '{}', 1, 25],
    ['observer_notes', '观测者笔记', '一本前任观测者留下的手写笔记，记录了关于剧本运作机制的珍贵推测。', 'material', 'uncommon', '{}', 1, 45],
    ['stardust_powder', '星屑粉末', '从星屑感染体身上收集的微光粉尘，在黑暗中会发出与星座相同频率的冷光。', 'material', 'rare', '{}', 1, 120],
    ['broken_relic', '破损遗物', '一件旧世界遗留下来的破碎物品，虽然已经失去了原本的用途，但其中的材质仍然蕴含待发掘的力量。', 'material', 'uncommon', '{}', 1, 55],

    // Story items
    ['old_subway_ticket', '旧地铁票', '一张发黄的地铁单程票，票面上印着一个不存在的站名。', 'story_item', 'uncommon', '{"unlock_event":"hidden_subway_event"}', 0, 0],
    ['memory_fragment', '记忆残片', '一块透明的晶体，内部封存着一段不属于你的记忆。', 'story_item', 'rare', '{"reveal_lore":"hidden_memory_scene"}', 0, 0],
    ['final_key_fragment', '终章钥匙碎片', '进入终章之门的关键之物。所有铸成这把钥匙的人最终都会面临同一个选择。', 'story_item', 'legendary', '{"unlock_true_ending":true}', 0, 0],

    // Skill fragments
    ['script_permit_fragment', '剧本许可碎片', '一个由剧本系统签发的晶莹碎片，内部封存着解锁一项技能的权限。', 'skill_fragment', 'epic', '{"skill_unlock_fragment":1}', 1, 200],
    // ── Phase 7: 新消耗品 & 材料 ──
    ['medium_hp_potion', '中级恢复剂', '中级HP恢复药剂，恢复80HP', 'consumable', 'uncommon', '{"heal_hp":80}', 1, 40],
    ['stamina_elixir', '体力药剂', '恢复30点体力', 'consumable', 'uncommon', '{"restore_stamina":30}', 1, 35],
    ['constellation_lure', '星座诱饵', '使用后下次探索必触发 opportunity 事件', 'consumable', 'rare', '{"force_event":"opportunity"}', 1, 100],
    ['abyss_resistance_pill', '深渊抗性药', '使用后2小时内深渊裂隙探索worldLineShift累积减半', 'consumable', 'rare', '{"worldline_shift_reduction":0.5,"duration_minutes":120}', 1, 80],
    ['narrative_ward', '叙事护符', '使用后抵挡一次叙事污染事件（自动触发）', 'consumable', 'rare', '{"block_narrative_event":1}', 1, 65],
    ['crystallized_scenario', '剧本结晶', 'L10+ 怪物掉落的剧本凝结物，用于装备突破。在光下透视时可以看到细密的文字脉络。', 'material', 'rare', '{}', 1, 50],
    ['sponsor_blessing_dust', '赞助祝福粉尘', '精英怪物掉落的星座祝福残留。触碰时指尖会泛起微弱的金色光芒——那是星座曾经注视过的证明。', 'material', 'rare', '{}', 1, 60],
    ['abyss_silk', '深渊丝线', '深渊裂隙专属掉落的暗色丝线。它摸起来像丝，但实际上是凝固的"被删除的文字"——如果你仔细看，还能辨认出几个模糊的韩文字符。', 'material', 'epic', '{}', 1, 100],
    ['star_fragment', '星辰碎片', '星座相关Boss掉落的碎片。碎片内部有一个微缩的星云在缓慢旋转——那是星座的"本质"，它陨落后唯一留存的东西。', 'material', 'epic', '{}', 1, 130],
    ['dream_shard', '梦之碎片', '隐藏事件极低概率产出的神秘碎片。握住它时，你会做从未属于自己的梦——那是"最古老的梦"散落在世界线中的残响。', 'material', 'legendary', '{}', 1, 350],
  ];

  for (const item of items) {
    insert.run(...item);
  }
  console.log('Item seed data inserted.');
}

module.exports = { seedItems };
