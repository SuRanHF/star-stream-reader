// 全知读者视角 探索事件种子数据 (Round 5: exploration event system)

function seedExplorationEvents(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO exploration_events (event_key, event_type, stage_key, location_key, name, description, weight, repeatable, required_conditions_json, rewards_json, risks_json, progress_effects_json)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const events = [
    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 01: 付费服务开始
    // ═══════════════════════════════════════════
    ['story_system_appears', 'story', 'main_ch01_paid_service', 'ruined_station',
      '剧本加载中：系统首次出现', '一只鬼怪突然出现在废弃的站台上。它的笑声尖锐刺耳——"频道已开启。星座们正在注视。第一个剧本将在15分钟后开始。"你知道这意味着什么，因为你在小说里读过它。但小说的文字和真实的鬼怪之间，间隔着某种无法言说的恐惧。',
      1.0, 0, '{}',
      '{"coins":20,"story_fragments":2,"flags":{"system_appeared":true}}',
      '{}',
      '{"storyEventsTriggered":"story_system_appears","worldLineShift":1}'],
    ['story_first_rule', 'story', 'main_ch01_paid_service', 'ruined_station',
      '剧本第一条规则', '鬼怪亮出了一行规则文字——"在15分钟内杀死至少一个生物，否则处决。"蓝色的文字在空气中燃烧。乘客开始尖叫。但你看到金代理的眼镜反射出字符的倒影——他在认真阅读。这是他活过三天剧情的原因：他比任何人都更认真地对待每一个规则。',
      1.0, 0, '{}',
      '{"coins":15,"story_fragments":1,"flags":{"first_rule_read":true},"stats":{"insight":1}}',
      '{}',
      '{"storyEventsTriggered":"story_first_rule"}'],
    ['story_survivor_panic', 'story', 'main_ch01_paid_service', 'ruined_station',
      '幸存者的恐慌', '车厢里几名乘客崩溃了。他们冲向车窗、敲打车门、用拳头砸一切他们觉得能砸开的东西。鬼怪在他们的慌乱中吹了一声口哨——"这就是星座们最喜欢的节目：由恐惧驱动的即兴表演。"你需要在混乱散播开来之前稳住局面。',
      1.0, 0, '{}',
      '{"coins":10,"story_fragments":2,"flags":{"witnessed_panic":true},"stats":{"leadership":1}}',
      '{}',
      '{"storyEventsTriggered":"story_survivor_panic"}'],
    ['story_channel_opens', 'story', 'main_ch01_paid_service', 'ruined_station',
      '频道开启：观众入场', '蓝光扫过所有人的头顶。鬼怪对着虚空里看不见的观众鞠了一躬。"来自星之流的各位星座、赞助者和兴趣观众——晚上好！今晚的主角是一群在废弃车站中濒死的可怜化身——"它指向你，微笑。"——以及一位看起来并不怎么害怕的特殊读者。"',
      1.0, 0, '{}',
      '{"coins":25,"story_fragments":3,"flags":{"channel_opened":true},"stats":{"insight":2}}',
      '{"channelHeat":2}',
      '{"storyEventsTriggered":"story_channel_opens"}'],
    // Stage 01 Final Story Event — 章节结算
    ['story_stage_01_settlement', 'story', 'main_ch01_paid_service', 'ruined_station',
      '最终结算：付费服务', '鬼怪重新出现在车厢中央。计时器清零。它的脸上是熟悉的笑容——"各位观众的硬币统计已完成。星座的赞助协议已锁定。"它看着你，蓝光在你头顶停顿。"这位化身——你的付费服务已经确认。欢迎来到灭亡的世界。你的生存之旅才刚刚开始。"',
      0, 0, '{}', // weight=0: not random, triggered only when objectives met
      '{"coins":40,"story_fragments":5,"flags":{"stage_01_final_triggered":true},"scenarioProof":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_01_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 02: 契约与赞助
    // ═══════════════════════════════════════════
    ['story_new_scene', 'story', 'main_ch02_meeting_protagonist', 'ruined_station',
      '新的舞台', '鬼怪宣布了第二个剧本。地铁站的墙壁在蓝光中溶解，城市的废墟展开在你面前——更大的笼子。但现在你有了自由行动的空间，也有了选择的权利。废弃车站外的街道上，依稀可以看到其他幸存者的身影。',
      1.0, 0, '{}',
      '{"coins":30,"story_fragments":3,"flags":{"second_scenario_started":true}}',
      '{}',
      '{"storyEventsTriggered":"story_new_scene"}'],
    ['story_protagonist_nearby', 'story', 'main_ch02_meeting_protagonist', 'ruined_station',
      '远处的人影', '你看到了他。在坍塌的便利店前，一个黑色风衣的人影正在冷静地检查一具昆虫尸体。他的动作精准而迅速，没有一丝多余。刘众赫。小说中的主角就在那里，距离你不到两百米。他还没注意到你。但你知道他的每一个习惯——因为那3149章描写了他的全部。',
      1.0, 0, '{}',
      '{"coins":20,"story_fragments":2,"flags":{"protagonist_spotted":true},"stats":{"insight":1}}',
      '{"channelHeat":1}',
      '{"storyEventsTriggered":"story_protagonist_nearby"}'],
    ['story_constellation_notice', 'story', 'main_ch02_meeting_protagonist', 'broken_market',
      '星座的注视', '视野边缘出现了一行文字：『一个星座好奇地注视着你。』然后又是一行：『另一个星座开始阅读你的频道。』你没有等待太久——第一次赞助请求弹出在你的视线中。某个古老的存在想加入你的故事。接受？还是拒绝？',
      1.0, 0, '{}',
      '{"coins":25,"story_fragments":3,"flags":{"constellation_noticed":true},"constellationFavor":1}',
      '{"channelHeat":3}',
      '{"storyEventsTriggered":"story_constellation_notice"}'],
    // Stage 02 Final Story Event
    ['story_stage_02_settlement', 'story', 'main_ch02_meeting_protagonist', 'broken_market',
      '契约成立：星座的赌注', '星座的回应一个接一个出现了。你的频道观众数突破了一个又一个记录。但在繁荣背后，某个最古老的存在始终沉默——它还没开口。你觉得它在等待着什么。等待你做出第一个足够愚蠢的错误？或者等待你证明自己足够特别？你关掉了赞助界面，望向废弃商场尽头那扇通往下一章节的门。',
      0, 0, '{}',
      '{"coins":60,"story_fragments":8,"flags":{"stage_02_final_triggered":true},"scenarioProof":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_02_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 03: 诸王冲突
    // ═══════════════════════════════════════════
    ['story_team_forming', 'story', 'main_ch03_constellation_sponsor', 'broken_market',
      '队伍初成', '刘尚雅站在废墟边缘，手里拿着一本翻烂的日志。李智慧在她的身后，警惕地扫视街道。李贤诚不知从哪里找到了一根撬棍。一个队伍正在成型——不是出于信任，而是出于必要。你需要在其中找到自己的位置。',
      1.0, 0, '{}',
      '{"coins":30,"story_fragments":3,"flags":{"team_forming":true},"stats":{"leadership":1}}',
      '{}',
      '{"storyEventsTriggered":"story_team_forming"}'],
    ['story_throne_appears', 'story', 'main_ch03_constellation_sponsor', 'collapsed_bridge',
      '绝对王座降临', '天空中裂开一道金色的缝隙。一座巨大的王座从裂缝中缓缓降落——不是实体，而是纯粹的力量凝结而成的漩涡。每一个化身都感到它的召唤。星座们在频道中发出了你从未听过的尖叫——那是对力量的渴望。绝对王座。坐上它，你就是众王之王。坐上它，你将无法回头。',
      1.0, 0, '{}',
      '{"coins":50,"story_fragments":5,"flags":{"throne_appeared":true},"kingToken":1}',
      '{"channelHeat":5}',
      '{"storyEventsTriggered":"story_throne_appears"}'],
    // Stage 03 Final
    ['story_stage_03_settlement', 'story', 'main_ch03_constellation_sponsor', 'collapsed_bridge',
      '王座之争的终局', '王座的光开始消退。这场争夺已经落幕——无论胜者是谁，王座的存在已经永远改变了星之流的所有势力。你站在桥的断口处，脚下的黑水在空洞地翻涌。下一幕的舞台已经确定。王者们消耗了彼此的精力，而真正的威胁还没有开始行动。',
      0, 0, '{}',
      '{"coins":80,"story_fragments":10,"flags":{"stage_03_final_triggered":true},"scenarioProof":1,"kingToken":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_03_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 04: 没有王的世界
    // ═══════════════════════════════════════════
    ['story_demon_selection_begin', 'story', 'main_ch04_throne_war', 'collapsed_bridge',
      '魔王选拔开始', '鬼怪带来了新的剧本——魔王选拔。化身必须在此互相残杀，最后活下来的人将获得魔王的力量。但这剧本的真正目的从来不是为了选出魔王。某个藏在幕后的存在正在利用选拔制造足够的混乱，好让自己从深渊中爬出来。',
      1.0, 0, '{}',
      '{"coins":40,"story_fragments":4,"flags":{"demon_selection_started":true},"abyssMark":1}',
      '{"channelHeat":4}',
      '{"storyEventsTriggered":"story_demon_selection_begin"}'],
    ['story_underworld_entrance', 'story', 'main_ch04_throne_war', 'silent_library',
      '冥界的入口', '图书馆地下室的墙上出现了一道裂缝——裂缝的另一侧不是泥土，而是一道向下延伸的阶梯。冥界在召唤。不是死神，而是某种更古老的力量。它在邀请每一个愿意用死亡交换重生的化身。你是否也配得上这份邀请？',
      1.0, 0, '{}',
      '{"coins":30,"story_fragments":3,"flags":{"underworld_entrance_found":true},"abyssMark":1}',
      '{"channelHeat":2}',
      '{"storyEventsTriggered":"story_underworld_entrance"}'],
    // Stage 04 Final
    ['story_stage_04_settlement', 'story', 'main_ch04_throne_war', 'silent_library',
      '没有王的世界的终局', '没有一个人坐上王座。但魔王候选的名单已经成形。冥界的通道在你身后悄然关闭——或者并未完全关闭。不管怎样，你在这个没有王的世界中找到了一条属于自己的路。现在，那个最古老的梦正在等待你。',
      0, 0, '{}',
      '{"coins":100,"story_fragments":15,"flags":{"stage_04_final_triggered":true},"scenarioProof":1,"abyssMark":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_04_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 05: 星座盛宴
    // ═══════════════════════════════════════════
    ['story_star_stream_secret', 'story', 'main_ch04_throne_war', 'black_channel_zone',
      '星之流的秘密', '在黑色频道区的导播台前，你发现了一份古老的日志。上面写着星之流最初的十三位星座——以及它们的真实身份。它们也曾是化身。它们也曾是人类。它们只是走得太远、坐得太久，以至于忘记了自己曾经是何等弱小。最古老的梦是第一个星座——也是唯一一个从未被替代的。',
      1.0, 0, '{}',
      '{"coins":50,"story_fragments":5,"flags":{"star_stream_secret_found":true},"constellationFavor":2,"stats":{"insight":3}}',
      '{"channelHeat":8}',
      '{"storyEventsTriggered":"story_star_stream_secret"}'],
    // Stage 05 Final
    ['story_stage_05_settlement', 'story', 'main_ch04_throne_war', 'black_channel_zone',
      '星座盛宴的终局', '所有的频道同时静默了一瞬。然后，一个低沉的声音从每一个屏幕、每一个扬声器、每一个鬼怪的频道中响起——那是最古老的梦在说话。它没有威胁，也没有愤怒。它只是在陈述一个事实：「读者，你比我想象得要久。」',
      0, 0, '{}',
      '{"coins":120,"story_fragments":20,"flags":{"stage_05_final_triggered":true},"scenarioProof":1,"constellationFavor":2}',
      '{}',
      '{"storyEventsTriggered":"story_stage_05_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 06: 暗城入口
    // ═══════════════════════════════════════════
    ['story_dark_castle_rises', 'story', 'main_ch04_throne_war', 'black_channel_zone',
      '暗城升起', '地平线上，一座黑色的城堡从虚空中凝聚成形。不是石头，不是钢铁——那是纯粹的文字凝结成的实体。暗城的每一扇窗户都像一只眼睛。你感觉到被注视的不适感。最古老的梦就在那里，就在那些字句砌成的高墙之后。',
      1.0, 0, '{}',
      '{"coins":60,"story_fragments":8,"flags":{"dark_castle_risen":true},"stats":{"insight":2}}',
      '{"channelHeat":6}',
      '{"storyEventsTriggered":"story_dark_castle_rises"}'],
    // Stage 06 Final
    ['story_stage_06_settlement', 'story', 'main_ch04_throne_war', 'final_scenario_gate',
      '暗城之门的终局', '暗城的大门彻底敞开。黑色的频道主持者消失了，只留下门口的两道符文——左边写着「进入」，右边写着「终章」。你手上的钥匙碎片在发光。它的温度在告诉你：你可以进去了。如果你还没准备好——也许就再也没机会了。',
      0, 0, '{}',
      '{"coins":150,"story_fragments":25,"flags":{"stage_06_final_triggered":true},"scenarioProof":1,"finalPage":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_06_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 主线剧情事件 — Stage 07: 魔王路线
    // ═══════════════════════════════════════════
    ['story_final_wall_visible', 'story', 'main_ch04_throne_war', 'final_scenario_gate',
      '最后的墙', '你站在了最后的墙面前。它不是物理的墙——它是所有故事和所有可能性的集合体。墙面上写满了3149章的内容。每一个化身的故事、每一次轮回的细节、每一个被遗忘的选择。而第3150章只有一片空白。那一章是关于你的——但还需要你亲自去书写。',
      1.0, 0, '{}',
      '{"coins":80,"story_fragments":10,"flags":{"final_wall_visible":true},"finalPage":1}',
      '{"channelHeat":10}',
      '{"storyEventsTriggered":"story_final_wall_visible"}'],
    // Stage 07 Final
    ['story_stage_07_settlement', 'story', 'main_ch04_throne_war', 'final_scenario_gate',
      '魔王路线的终局', '魔王不再是你的称号——而是你走过的路。你理解了王者的孤独、魔王的重量、冥界的黑暗、以及读者视角的珍贵。第3150章的文字开始在你面前缓慢浮现，等待你的最后一笔。是时候去翻开那一页了——不是作为化身，不是作为角色，而是作为你一直以来应该是的那个人：读者。',
      0, 0, '{}',
      '{"coins":200,"story_fragments":30,"flags":{"stage_07_final_triggered":true},"scenarioProof":1,"finalPage":1}',
      '{}',
      '{"storyEventsTriggered":"story_stage_07_settlement","completesStageEvents":true}'],

    // ═══════════════════════════════════════════
    // 支线剧情事件
    // ═══════════════════════════════════════════
    ['side_station_child', 'side_story', null, 'ruined_station',
      '站台上的孩子', '一个大约九岁的孩子在废墟中翻找着食物。他的父亲没有活过第一个剧本。他还不理解死亡意味着什么——但他知道饥饿的滋味。你包里有一块压缩饼干。你需要它，但他更需要。',
      1.0, 0, '{}',
      '{"coins":5,"story_fragments":1,"stats":{"bond":1}}',
      '{}',
      '{}'],
    ['side_market_survivor', 'side_story', null, 'broken_market',
      '商场的最后一位店员', '商场四楼的一家店铺还亮着灯。一位老妇人坐在柜台后面，仿佛还在等待顾客。事实上她早已死去——她只是这个场景中尚未消失的故事碎片。她手中的账本上写满了顾客的名字——其中最后一个名字是你。',
      1.0, 0, '{}',
      '{"coins":15,"story_fragments":2}',
      '{"channelHeat":1}',
      '{}'],
    ['side_library_whisper', 'side_story', null, 'silent_library',
      '书本的低语', '一本没有封面的书从书架上滑落，在你脚边翻开。书页上的文字不是固定的——它们在变化。每一行字都是对自己命运的讲述。一段写着某个化身的故事。另一段写着你的近期选择。最下面的那一段写着：如果你现在离开图书馆，你将在三小时后遇到一个改变你人生方向的陌生人。',
      1.0, 0, '{}',
      '{"story_fragments":3,"stats":{"insight":2}}',
      '{}',
      '{}'],
    ['side_bridge_ghost', 'side_story', null, 'collapsed_bridge',
      '桥下的吟唱', '黑水深处传来模糊的歌声。那是几千年前某个文明的葬礼歌谣。歌谣的内容是一个预言——必须有一个存在被遗忘，才能让另一个被记住。桥面在你脚下轻微震动，仿佛每句歌词都在牵动钢筋的共鸣。',
      1.0, 0, '{}',
      '{"story_fragments":2,"stats":{"insight":1},"abyssMark":0}',
      '{}',
      '{}'],
    ['side_channel_memory', 'side_story', null, 'black_channel_zone',
      '频道中的记忆', '一个废弃的屏幕突然亮起，播放着两天前某个化身的死亡画面。你认识那张脸——他是你在车站见过的人。鬼怪在他的尸体旁边宣布结算。然后屏幕闪烁，开始重播。一遍又一遍。仿佛失去了关闭的能力。',
      1.0, 0, '{}',
      '{"story_fragments":3,"stats":{"cruelty":1}}',
      '{"channelHeat":2}',
      '{}'],

    // ═══════════════════════════════════════════
    // 机遇事件
    // ═══════════════════════════════════════════
    ['opp_resource_stash', 'opportunity', null, 'ruined_station',
      '资源机遇：车厢暗格', '你在地铁车厢底部的夹层中发现了一个未被搜刮过的暗格。里面有几袋压缩饼干、几卷绷带，以及一小袋看起来像是某人私藏的硬币。',
      1.0, 0, '{}',
      '{"coins":50,"story_fragments":3}',
      '{}',
      '{}'],
    ['opp_title_insight', 'opportunity', null, 'silent_library',
      '称号机遇：阅读者的眼', '在一个被遗忘的阅览室深处，一本名为《全知读者视角》的书在等待着你。翻开第一章，你立刻意识到——这不是你熟悉的那个版本。这本书的结局与你读过的不同。你在文字的裂隙中窥见了所有的可能性——获得称号进度：「星之流的观察者」。',
      1.0, 0, '{}',
      '{"story_fragments":5,"title_progress":"title_watcher","stats":{"insight":3}}',
      '{}',
      '{}'],
    ['opp_skill_fragment', 'opportunity', null, 'silent_library',
      '技能机遇：书中兵法', '书架之间夹着一页古老的战术笔记。上面的字迹已经模糊，但原理仍然清晰——如何利用地形、如何预判敌人的移动路线、如何用最少的力量获得最大的效果。你将这一页小心折叠放入口袋——获得技能碎片。',
      1.0, 0, '{}',
      '{"coins":20,"story_fragments":4,"stats":{"intelligence":2}}',
      '{}',
      '{}'],
    ['opp_equipment_find', 'opportunity', null, 'broken_market',
      '装备机遇：遗落的护甲', '商场的保安室里挂着一件轻型防弹背心——上面覆盖着一层薄灰，但依然完好。也许是某个保安在世时最后的遗物。它可能救不了世界，但至少能救你一次。',
      1.0, 0, '{}',
      '{"coins":10,"equipment":"station_guard_coat","items":["small_hp_potion"]}',
      '{}',
      '{}'],
    ['opp_hidden_story_clue', 'opportunity', null, 'black_channel_zone',
      '隐藏剧情机遇：频道干扰', '所有屏幕突然同时闪烁着同一段画面——某个不被记住的化身在最古老的梦面前说话。音频被切断了，但唇形仍然可读：「我知道你是谁。你害怕读者。你害怕被他理解。」然后画面消失。星座频道在短暂静默后恢复了噪音。但你收到的这段干扰——绝非偶然。',
      1.0, 0, '{}',
      '{"story_fragments":8,"flags":{"hidden_clue_witnessed":true},"stats":{"insight":3}}',
      '{"channelHeat":10,"worldLineShift":2}',
      '{}'],
    ['opp_high_risk_gamble', 'opportunity', null, 'collapsed_bridge',
      '高风险机遇：黑水之约', '桥下的黑水突然停止了流动，一个声音从水面之下传来——不是鬼怪，不是星座。那是某种更古老的存在。它提出一个提议：一次性地给你巨大的力量，代价是在你最脆弱的时刻，它将在你耳边说一句话——你不能拒绝听这句话的权利。接受吗？',
      1.0, 0, '{}',
      '{"coins":200,"story_fragments":15,"kingToken":2,"abyssMark":2,"stats":{"combat":5,"cruelty":3}}',
      '{"worldLineShift":5,"channelHeat":15,"hp_loss":30}',
      '{}'],
    ['opp_sponsor_attention', 'opportunity', null, 'black_channel_zone',
      '赞助机遇：黑暗中的观察者', '频道中出现了一段不同寻常的赞助请求——不是普通的星座，而是「黑暗中的观察者」。它的出价是：无条件给予你星座祝福，并解锁一次与任何星座正面对话的机会。条件只有一个：你必须愿意承受它的全部关注——包括那些令人不快的后果。',
      1.0, 0, '{}',
      '{"coins":100,"constellationFavor":3,"sponsors_add":["黑暗中的观察者"],"story_fragments":5}',
      '{"channelHeat":8}',
      '{}'],
    ['opp_rare_resource', 'opportunity', null, 'final_scenario_gate',
      '终极资源机遇：文字晶化', '在终章之门的基石缝隙中，一些结晶化的文字碎片正在微微发光。这些不是普通的物品——它们是此前每一个故事的凝结。收集到足够多的碎片，你就将拼出第3150章的第一段文字。',
      1.0, 0, '{}',
      '{"coins":150,"story_fragments":12,"finalPage":1,"scenarioProof":2}',
      '{"channelHeat":3}',
      '{}'],

    // ═══════════════════════════════════════════
    // 隐藏事件
    // ═══════════════════════════════════════════
    ['hidden_dream_fragment', 'hidden', null, 'silent_library',
      '隐藏事件：梦的碎片', '你在图书馆的最深处发现了一张书桌——上面有一张正在流逝的便条，写着：「致正在阅读这段话的你：我知道你是谁。我知道你在干什么。你最古老的梦已经醒了。在读者意识到自己的存在之前，尽快抵达暗城。他是最后一个——也是唯一一个——能够结束这个故事的人。」便条在你读完最后一个字后化为了灰烬。',
      1.0, 0, '{}',
      '{"story_fragments":10,"stats":{"insight":4},"flags":{"dream_fragment_found":true}}',
      '{"worldLineShift":3}',
      '{}'],
    ['hidden_true_ending_clue', 'hidden', null, 'final_scenario_gate',
      '隐藏事件：真结局线索', '你注意到终章之门上的文字不是固定的——它们在缓慢移动，重新排列。如果你仔细看，你能看到文字排列的模式。那是一段从未在任何频道中出现过的隐藏剧本：在第3150章，你必须拒绝所有结局选项。你必须坚持阅读——哪怕整本书都在试图合上自己。那段文字的最后一行写道：「全知读者视角：不仅知道故事如何结束，更知道故事为什么必须结束。」',
      1.0, 0, '{}',
      '{"story_fragments":15,"stats":{"insight":5},"flags":{"true_ending_clue_found":true},"finalPage":2}',
      '{"worldLineShift":8}',
      '{}'],
    ['hidden_oldest_dream', 'hidden', null, 'black_channel_zone',
      '隐藏事件：最古老的梦在注视', '所有屏幕同时黑了——整个黑色频道区陷入了几万年不曾有过的静默。然后一句话出现在每一块屏幕上，一个字母一个字母地浮现：「我知道你在读我。我已经等着这一刻——等了几千个人类的一生。你不是金独子。你甚至不需要是个化身。当你读到这一句话时，你已经成为了这个故事的一部分。」这句话只持续了不到五秒就消失了——但它会永远留在你的记忆中。',
      1.0, 0, '{}',
      '{"story_fragments":20,"stats":{"insight":5},"flags":{"oldest_dream_noticed":true}}',
      '{"worldLineShift":10}',
      '{}'],

    // ═══════════════════════════════════════════
    // Boss 线索事件
    // ═══════════════════════════════════════════
    ['clue_station_keeper', 'boss_clue', null, 'ruined_station',
      'Boss线索：站台的守护者', '在废弃车站的最深处——通往下一站的闸门后方，某种沉重的呼吸声提示着你这里有某种不该醒着的东西还活着。墙上的血迹还很新鲜。站台上散落着大量的昆虫壳——远超正常数量。站长室里偶尔传来金属摩擦声。小心——这是第一个剧本残留下来的守护者。',
      1.0, 0, '{}',
      '{"story_fragments":2,"flags":{"keeper_clue":true}}',
      '{}',
      '{"bossClues":"ruined_station_keeper"}'],
    ['clue_market_tyrant', 'boss_clue', null, 'broken_market',
      'Boss线索：商场霸主', '商场的广播在没有任何征兆的情况下开始播放一段录音：「四楼的最后一位店员昨天死了。现在四楼由我管理。想拿走任何东西，先来见我。」声音粗粝低沉——不是化身的声音，而是某个在剧本中获得了力量后选择用恐怖统治废墟的怪物。',
      1.0, 0, '{}',
      '{"story_fragments":3,"flags":{"tyrant_clue":true}}',
      '{}',
      '{"bossClues":"market_tyrant"}'],
    ['clue_blind_librarian', 'boss_clue', null, 'silent_library',
      'Boss线索：盲眼管理员的笔记', '一本盲文笔记从书架上掉落——这是盲眼管理员亲自书的。笔记记载了所有进入图书馆的人——以及他们的死因。最后一页写着一行新的盲文：「下一个读者阅读此页时，我将在你身后。」',
      1.0, 0, '{}',
      '{"story_fragments":4,"flags":{"librarian_clue":true}}',
      '{}',
      '{"bossClues":"blind_librarian"}'],
    ['clue_channel_host', 'boss_clue', null, 'black_channel_zone',
      'Boss线索：黑色频道的主持者', '在导播台的监视器矩阵中，所有屏幕播放的都是同一个影像——一个鬼怪站在燃烧的频道控制台前，对着成千上万个星座频道做出同一个手势：安静。它的控制力远超普通鬼怪。它不是在报道剧本——它是在选择哪些剧本可以成立、哪些可以消失。',
      1.0, 0, '{}',
      '{"story_fragments":5,"flags":{"channel_host_clue":true}}',
      '{}',
      '{"bossClues":"black_channel_host"}'],
    ['clue_final_gatekeeper', 'boss_clue', null, 'final_scenario_gate',
      'Boss线索：终章守门人的苏醒', '终章之门两侧的巨型雕像突然睁开了眼睛。它们一直在沉睡——为了等待一个真正值得考验的人。当它们看到你的终章钥匙碎片时，其中一座石像的下巴动了：「你带着三页钥匙。但你还需要明白：打开终章的工具不是钥匙，而是你全部的故事。」',
      1.0, 0, '{}',
      '{"story_fragments":4,"flags":{"gatekeeper_clue":true}}',
      '{"channelHeat":5}',
      '{"bossClues":"final_gatekeeper"}'],
  ];

  for (const e of events) {
    insert.run(...e);
  }
  console.log('Exploration event seed data inserted.');
}

module.exports = { seedExplorationEvents };
