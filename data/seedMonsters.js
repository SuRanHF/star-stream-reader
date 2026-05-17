// 全知读者视角 怪物种子数据

function seedMonsters(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO monsters (monster_key, name, description, level, hp, attack, defense, speed, skills_json, rewards_json, drop_table_json, location_keys_json, is_boss)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const monsters = [
    // ===== 普通怪物 (10) =====
    ['rogue_avatar', '失控化身', '因世界线崩溃而从角色面板中逃逸的灰色人影，五官模糊不清，动作僵硬而诡异地反复。', 1, 18, 7, 2, 4,
      '["扭曲冲撞"]', '{"exp":15,"coins":10}',
      '[{"item_key":"small_hp_potion","rate":0.20},{"item_key":"broken_coin_bag","rate":0.10}]',
      '["ruined_station"]', 0],
    ['hungry_follower', '饥饿追随者', '因饥饿失去理智的幸存者，眼神空洞，手中攥着发霉的食物残渣，会扑向任何靠近的生物。', 3, 30, 10, 3, 5,
      '["疯狂撕咬"]', '{"exp":25,"coins":15}',
      '[{"item_key":"small_hp_potion","rate":0.15},{"item_key":"story_scrap","rate":0.12}]',
      '["ruined_station","broken_market"]', 0],
    ['scenario_shade', '剧本残影', '被剧本删除的角色的残留影像，半透明的身体只在光线折射下可见，反复念着被删除前的最后一句台词。', 4, 40, 13, 4, 6,
      '["残响共鸣","文本侵蚀"]', '{"exp":35,"coins":20}',
      '[{"item_key":"story_scrap","rate":0.20},{"item_key":"script_permit_fragment","rate":0.08}]',
      '["broken_market","silent_library"]', 0],
    ['channel_corruption', '频道污染体', '被废弃频道信号污染的机械与血肉的混合体，身体表面嵌满闪烁的屏幕碎片。', 5, 55, 16, 5, 6,
      '["信号干扰","频道污染波"]', '{"exp":45,"coins":25}',
      '[{"item_key":"channel_jammer","rate":0.12},{"item_key":"observer_notes","rate":0.10}]',
      '["silent_library","collapsed_bridge"]', 0],
    ['old_rule_remnant', '旧规则残渣', '旧版本剧本规则被覆盖后残留的具象化碎片，外形如同扭曲的金属符文。', 6, 70, 19, 7, 5,
      '["规则反噬","旧版指令冲击"]', '{"exp":55,"coins":30}',
      '[{"item_key":"worldline_stabilizer","rate":0.10},{"item_key":"broken_relic","rate":0.10}]',
      '["collapsed_bridge"]', 0],
    ['subway_wanderer', '地铁游荡者', '永远留在地下铁通道中的乘客灵魂，提着不存在的公文包，准时准点地在站台之间穿行。', 2, 22, 8, 2, 7,
      '["无声突进"]', '{"exp":18,"coins":12}',
      '[{"item_key":"old_subway_ticket","rate":0.25},{"item_key":"small_hp_potion","rate":0.18}]',
      '["ruined_station"]', 0],
    ['market_raider', '商场掠夺者', '盘踞在断裂商场中的武装团伙成员，身穿从店铺中搜刮来的防护装备，对任何闯入者毫不留情。', 4, 45, 14, 6, 5,
      '["缴械攻击","团伙围攻"]', '{"exp":32,"coins":22}',
      '[{"item_key":"broken_coin_bag","rate":0.20},{"item_key":"temporary_talisman","rate":0.10}]',
      '["broken_market"]', 0],
    ['dark_mist_parasite', '黑雾寄生者', '一团不断蠕动的人形黑烟，内部隐约可见被包裹的骸骨，寄生在阴暗角落并缓慢吞噬周围的光线和声音。', 6, 65, 18, 7, 8,
      '["黑雾缠绕","寄生侵蚀"]', '{"exp":50,"coins":28}',
      '[{"item_key":"stardust_powder","rate":0.12},{"item_key":"temporary_talisman","rate":0.10}]',
      '["silent_library","collapsed_bridge"]', 0],
    ['low_executor', '低阶执行者', '剧本执行系统派出的基层清理者，身披统一的灰色制服，面部被一个闪烁的任务编号取代。', 7, 85, 22, 9, 7,
      '["执行抹消","冷却结界"]', '{"exp":65,"coins":35}',
      '[{"item_key":"low_sponsor_token","rate":0.12},{"item_key":"story_scrap","rate":0.15}]',
      '["collapsed_bridge","black_channel_zone"]', 0],
    ['disconnected_avatar', '断线化身', '与主角色连接被切断的分身，在废弃的城市中漫无目的地游走，偶尔会做出与本体完全相反的行为。', 8, 100, 26, 10, 9,
      '["身份错乱","逆向投影"]', '{"exp":80,"coins":40}',
      '[{"item_key":"memory_fragment","rate":0.15},{"item_key":"worldline_stabilizer","rate":0.10}]',
      '["black_channel_zone","final_scenario_gate"]', 0],

    // ===== 精英怪物 (5) =====
    ['blood_text_executor', '血字任务执行官', '被指派执行高危血字任务的高级执行者，全身覆盖着红色发光的任务铭文，每一个铭文都是一次处决记录。', 11, 180, 40, 18, 10,
      '["血字审判","任务强制","处决标记"]', '{"exp":250,"coins":120}',
      '[{"item_key":"script_permit_fragment","rate":0.15},{"item_key":"broken_relic","rate":0.20}]',
      '["black_channel_zone"]', 0],
    ['silent_hunter', '沉默猎杀者', '在静默图书馆深处成长的掠食者，利用完全沉默的环境捕猎，任何声音都会触发其致命的追击反应。', 13, 240, 50, 22, 14,
      '["无声猎杀","寂静领域","暗影一击"]', '{"exp":350,"coins":160}',
      '[{"item_key":"stardust_powder","rate":0.18},{"item_key":"temporary_talisman","rate":0.15}]',
      '["silent_library","collapsed_bridge"]', 0],
    ['stardust_infected', '星屑感染体', '吸收了星座碎片力量的变异生物，全身闪烁着星光般的粒子，但每一个光点都在扭曲周边的现实。', 15, 320, 62, 28, 12,
      '["星屑风暴","现实扭曲","星座共鸣"]', '{"exp":480,"coins":200}',
      '[{"item_key":"stardust_powder","rate":0.30}]',
      '["black_channel_zone","collapsed_bridge"]', 0],
    ['throne_guard', '王座守卫', '守护终章之门周边的无名王座之卫兵，身披由文字织成的铠甲，每一次挥舞武器都伴随着一段被遗忘的故事。', 17, 420, 75, 35, 11,
      '["王之裁决","文字铠甲","故事咏唱"]', '{"exp":620,"coins":280}',
      '[{"item_key":"final_key_fragment","rate":0.03}]',
      '["final_scenario_gate"]', 0],
    ['fallen_sponsor', '失落赞助者', '一位被星座系统除名的前赞助者残影，失去了大部分力量但仍保留着渴望占有新叙事的本能。', 19, 520, 88, 42, 13,
      '["赞助枷锁","星权剥夺","叙事侵占","频道封闭"]', '{"exp":800,"coins":350}',
      '[{"item_key":"final_key_fragment","rate":0.05},{"item_key":"low_sponsor_token","rate":0.25}]',
      '["final_scenario_gate"]', 0],

    // ===== Boss (5) =====
    ['ruined_station_keeper', '废站看门人', '废弃车站的统治级怪物，由数百张撕碎的车票和铁轨碎片组成的巨大人形，每踏出一步都会引发站台的结构性倒塌。', 5, 180, 30, 15, 4,
      '["铁轨碾压","车票风暴","站台崩塌"]', '{"exp":150,"coins":100}',
      '[{"item_key":"broken_relic","rate":0.30},{"item_key":"old_subway_ticket","rate":0.40}]',
      '["ruined_station"]', 1],
    ['market_tyrant', '商场暴君', '控制了断裂商场的狂人，用掠夺来的物资装备到了牙齿，坐在由报废扶梯搭成的王座上审判一切闯入者。', 10, 450, 52, 25, 7,
      '["暴君裁决","商品投掷","扶梯陷阱","掠夺冲锋"]', '{"exp":400,"coins":250}',
      '[{"item_key":"broken_coin_bag","rate":0.50}]',
      '["broken_market"]', 1],
    ['blind_librarian', '图书馆盲眼管理员', '静默图书馆的守护者，眼眶中空无一物却仿佛能看透一切。手持一本空白的巨书，翻开即是你的故事，合上即是你的结局。', 15, 700, 68, 35, 9,
      '["空白之书","故事抹除","寂静审判","知识汲取","文本重写"]', '{"exp":750,"coins":400}',
      '[{"item_key":"observer_notes","rate":0.50},{"item_key":"script_permit_fragment","rate":0.30}]',
      '["silent_library"]', 1],
    ['black_channel_host', '黑色频道主持者', '黑色频道区的统治者，人类躯干上连接着十余个显示屏，每个屏幕上都有一张不同的脸在同时说话。星座们通过它直播游戏的进程。', 20, 1200, 95, 50, 12,
      '["频道切换","赞助抽成","收视霸权","星座广播","实时转播"]', '{"exp":1500,"coins":800}',
      '[{"item_key":"low_sponsor_token","rate":0.40}]',
      '["black_channel_zone"]', 1],
    ['final_gatekeeper', '终章守门人', '终章之门的永恒守护者，身高三丈，手持由世界线编织的双刃戟。它的存在本身就是一道选择题：放弃进入终章，或者以生命为代价换取通往结局的权利。', 25, 2500, 130, 70, 14,
      '["终章裁决","世界线斩击","结局预览","存在抹消","门扉闭合"]', '{"exp":3000,"coins":2000}',
      '[{"item_key":"final_key_fragment","rate":1.00}]',
      '["final_scenario_gate"]', 1],
  ];

  for (const m of monsters) {
    insert.run(...m);
  }
  console.log('Monster seed data inserted.');
}

module.exports = { seedMonsters };
