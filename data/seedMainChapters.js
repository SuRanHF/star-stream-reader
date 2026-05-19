// 全知读者视角 主线章节突破系统种子数据 (Round 9: 第一卷完整重写)
// 4个大章，每个大章包含8个剧情节点，严格按第一卷结构

function seedMainChapters(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO main_chapters (chapter_key, chapter_name, description, order_index,
      is_unlocked_by_default, story_chapter_keys_json, last_story_node_key,
      unlock_conditions_json, breakthrough_cost_json, rewards_json,
      first_story_node_key, breakthrough_text, stage_objectives_json)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const chapters = [
    // ── 第1章: 付费服务开始 (默认解锁) ──
    ['main_ch01_paid_service', '付费服务开始',
      '地铁上的日常崩塌了。鬼怪降临，第一个剧本开始。你是唯一知道剧情走向的人——但在所有人都在尖叫的时候，知道规则和遵守规则是两回事。',
      1, 1,
      '["ch1_01_last_train","ch1_02_dokkaebi_appears","ch1_03_first_scenario","ch1_04_insect_kill","ch1_05_aftermath","ch1_06_second_scenario","ch1_07_broken_path","ch1_08_stage_final"]',
      'ch1_08_stage_final',
      '{}',
      '{}',
      '{"coins":50,"story_fragments":5}',
      'ch1_01_last_train',
      '',
      '{"objectives":[{"type":"visited_nodes_min","count":4,"label":"至少体验4个剧情节点"},{"type":"any_flag","keys":["ch1_dealt_insect","ch1_helped_kim","ch1_saved_self"],"label":"完成第一个剧本"}]}'],

    // ── 第2章: 主角与旗帜 ──
    ['main_ch02_meeting_protagonist', '主角与旗帜',
      '站台营地建立，幸存者分化。刘众赫以压倒性的姿态出现——他是你在小说中读过无数次的主角。但认识他和让他认识你是两回事。旗帜争夺战将所有人卷入一场无法逃避的试炼。',
      2, 0,
      '["ch2_01_station_camp","ch2_02_survivor_tension","ch2_03_yjh_enters","ch2_04_knowledge_test","ch2_05_flag_scenario","ch2_06_alliance_web","ch2_07_flag_climax","ch2_08_stage_final"]',
      'ch2_08_stage_final',
      '{"required_previous_chapter":"main_ch01_paid_service","required_resources":{"storyFragments":20},"required_level":2,"required_flags":["first_scenario_cleared"]}',
      '{"storyFragments":20}',
      '{"coins":100,"story_fragments":8,"unlock_skills":["skill_basic_combat"]}',
      'ch2_01_station_camp',
      '第一阶段的结算完成了。你站在站台上，感受到远方星座的目光——它们开始对这个新开放的频道产生兴趣。鬼怪的手在空中划过，打开了通往更大舞台的门。付费服务已到期——真正的剧本现在开始。你需要更多的碎片来拼出下一个故事的轮廓。',
      '{"objectives":[{"type":"visited_nodes_min","count":4,"label":"至少体验4个剧情节点"},{"type":"any_flag","keys":["ch2_proved_knowledge","ch2_revealed_info","ch2_kept_secret"],"label":"与刘众赫建立联系"},{"type":"any_flag","keys":["ch2_allied_kim","ch2_allied_independent"],"label":"在旗帜之战中建立联盟"}]}'],

    // ── 第3章: 星座的注视 ──
    ['main_ch03_constellation_sponsor', '星座的注视',
      '星流正式向地球开放频道。星座们的目光集中在少数几个化身身上——而你正是其中之一。赞助邀请纷至沓来，每一个都附带条件。但你知道：在《灭亡法》中，接受赞助意味着交出自由。你需要决定走什么样的路。是接受星座的力量，还是走那条没有先例的读者之道？',
      3, 0,
      '["ch3_01_star_stream_open","ch3_02_sponsor_offers","ch3_03_readers_path","ch3_04_companion_bonds","ch3_05_hidden_scenario","ch3_06_theater_of_gods","ch3_07_sponsor_choice","ch3_08_stage_final"]',
      'ch3_08_stage_final',
      '{"required_previous_chapter":"main_ch02_meeting_protagonist","required_resources":{"storyFragments":45,"constellationFavor":1},"required_level":4,"required_flags":["ch2_settled_flags"]}',
      '{"storyFragments":45,"constellationFavor":1}',
      '{"coins":200,"story_fragments":15,"permanent_flags":{"星流焦点":true}}',
      'ch3_01_star_stream_open',
      '旗帜之战的尘埃落定。你的名字开始出现在星流的频道中。星座们注意到了你——不是因为你的力量，而是因为你的不可预测性。你站在星流的十字路口，感受到无数目光的重量。每一个星座都想成为你的赞助者。但你是读者——你比它们更了解故事的走向。选择你的路，然后坚定地走下去。',
      '{"objectives":[{"type":"visited_nodes_min","count":4,"label":"至少体验4个剧情节点"},{"type":"any_flag","keys":["ch3_accepted_mild","ch3_declined_all"],"label":"面对第一个赞助选择"},{"type":"any_flag","keys":["ch3_accepted_strong","ch3_refused_all","ch3_negotiated_deal"],"label":"在抉择时刻做出最终决定"}]}'],

    // ── 第4章: 王座战争 ──
    ['main_ch04_throne_war', '王座战争',
      '关于绝对王座的预言在幸存者中传开。不管是谁，只要能坐上那个王座，就能获得足以改变世界的力量。每一个化身都开始向王座进发——刘众赫、韩明武、以及更多你在小说中认识和不认识的人。但你知道王座的真相：它不是奖赏，而是考验。坐上王座的人不会死在这个剧本中——但会在更远的未来付出代价。你需要决定：阻止刘众赫，支持他，还是自己坐上去？第一卷的故事将在这场王座之争中抵达终点。',
      4, 0,
      '["ch4_01_throne_prophecy","ch4_02_kings_gather","ch4_03_path_to_throne","ch4_04_rival_kings","ch4_05_throne_battle","ch4_06_absolute_choice","ch4_07_throne_resolved","ch4_08_volume1_end","ch4_ending_first_step"]',
      'ch4_08_volume1_end',
      '{"required_previous_chapter":"main_ch03_constellation_sponsor","required_resources":{"storyFragments":70,"constellationFavor":2,"abyssMark":1},"required_level":6}',
      '{"storyFragments":70,"constellationFavor":2,"abyssMark":1}',
      '{"coins":500,"story_fragments":25,"permanent_flags":{"第一卷完成者":true},"unlock_skills":["skill_defensive_stance"]}',
      'ch4_01_throne_prophecy',
      '契约之刻已经过去。不管你签了什么契约——或者什么都没签——你已经不再是那个地铁上的普通读者了。星之流的所有频道都在关注着即将到来的事件。绝对王座的预言像野火般蔓延。诸王正在集结。通向王座的道路上铺满了试炼和选择。第一卷的终章已经近在眼前。以你积累的所有故事碎片、星座垂青和王者印记为钥匙——去写下这一卷的结局吧。',
      '{"objectives":[{"type":"visited_nodes_min","count":5,"label":"至少体验5个剧情节点"},{"type":"any_flag","keys":["ch4_stopped_yjh","ch4_supported_yjh","ch4_took_throne"],"label":"在绝对选择中做出决定"},{"type":"any_flag","keys":["ch4_settled_volume1"],"label":"完成第一卷结算"}]}'],
  ];

  for (const ch of chapters) {
    insert.run(...ch);
  }
  console.log('Main chapter breakthrough seed data (Volume 1 — 4 chapters) inserted.');
}

module.exports = { seedMainChapters };
