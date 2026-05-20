// 全知读者视角 改编游戏称号种子数据
// 基于 sing N song 原作改编

function seedTitles(db) {
  const insert = db.prepare(`
    INSERT OR IGNORE INTO titles (title_key, name, description, rarity, conditions_json, effects_json, exclusive_with_json)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `);

  const titles = [
    // ── Common 称号 ──
    ['title_first_reader', '最初的读者', '在地铁车厢陷入混乱的那一刻，只有你保持了冷静。因为你早就知道即将发生的一切。你是唯一的读者——这个事实本身，就是你最大的武器。', 'common',
      '{"flags":{"ch1_observed_crowd":true}}',
      '{"stat_modifier":{"insight":1},"narrative_tags":["reader"]}',
      '[]'],
    ['title_first_helper', '第一个伸出援手的人', '你选择了先救人。在全车乘客陷入恐慌时，你朝那个即将被昆虫攻击的中年男人伸出了手。不是因为善良——而是因为你知道剧情的走向。', 'common',
      '{"flags":{"ch1_helped_kim":true}}',
      '{"stat_modifier":{"bond":1},"narrative_tags":["reader"]}',
      '["title_watcher"]'],
    ['title_insect_slayer', '第一只猎物', '你亲手杀死了那只昆虫。动作干净利落，就像你曾在小说中读过的那样。鬼怪在频道里吹了一声口哨——「这位化身有没有考虑过以狩猎为生？」', 'common',
      '{"flags":{"ch1_dealt_insect":true}}',
      '{"stat_modifier":{"combat":1},"combat_bonus":{"attack_pct":0.05},"narrative_tags":["reader"]}',
      '[]'],
    ['title_info_broker', '情报贩子', '你没有独吞信息。你大声告诉了周围的人剧本的规则。「15分钟，一个生命，否则处决。」——你的声音盖过了尖叫声。有些人因此活了下来。', 'common',
      '{"flags":{"ch1_calmed_passengers":true}}',
      '{"stat_modifier":{"bond":1},"exploration_bonus":{"luck":0.03},"narrative_tags":["reader"]}',
      '[]'],

    // ── Rare 称号 ──
    ['title_regressor_shadow', '回归者的影子', '你选择跟在刘众赫身后。你知道他是谁——他是那个经历了无数次轮回的回归者。在他的影子里，你比任何人都安全。但你也知道：他不信任任何人。', 'rare',
      '{"flags":{"ch2_approached_yjh":true}}',
      '{"stat_modifier":{"combat":1,"bond":1},"combat_bonus":{"defense_pct":0.05},"narrative_tags":["regressor"]}',
      '["title_independent_walker"]'],
    ['title_independent_walker', '独行者', '你拒绝了依附刘众赫的路线。他不是你的主角，你不是他的配角。你要走自己的路——哪怕这条路更加危险，更加孤独。星座们对你的选择窃窃私语。', 'rare',
      '{"flags":{"independent":true},"stats":{"insight":{"min":4}}}',
      '{"stat_modifier":{"insight":2,"bond":-1},"combat_bonus":{"attack_pct":0.08},"exploration_bonus":{"stamina_reduction":1},"event_prob_modifiers":{"story":0.05,"boss_clue":0.08},"narrative_tags":["reader","anti_constellation"]}',
      '["title_regressor_shadow"]'],
    ['title_reader_revealed', '身份暴露的读者', '你告诉了刘众赫真相——你知道他的回归，他的过去，他即将做出的每一个选择。他的表情没有任何变化。但你知道他在想什么：「这个人是谁？」从这一刻起，你对主角来说不再是无名之辈。', 'rare',
      '{"flags":{"ch2_revealed_info":true}}',
      '{"stat_modifier":{"insight":2},"unlock_choices":["choice_ch2_04_reveal_info"],"combat_bonus":{"crit_rate":0.05},"narrative_tags":["reader"]}',
      '[]'],
    ['title_watcher', '星之流的观察者', '你选择观察而非鲁莽行动。星座注意到了你的冷静——在所有人都惊慌失措的时候，你的目光像书页一样翻过每一个细节，计算着每一种可能性。某个星座在你的频道上留下了弹幕：「有趣的人类。」', 'rare',
      '{"flags":{"ch1_observed_crowd":true},"stats":{"insight":{"min":5}}}',
      '{"stat_modifier":{"insight":2,"bond":-1},"unlock_choices":["choice_ch3_07_negotiate_deal"],"exploration_bonus":{"drop_rate":0.05},"event_prob_modifiers":{"story":0.1,"hidden":0.05,"battle":-0.05},"narrative_tags":["observer"]}',
      '["title_first_helper"]'],
    ['title_sponsored', '被选中的人', '你接受了星座的赞助。一个古老的存在将目光投向你——「神秘的监视者」。你感到一股力量通过频道流入你的身体。但这种力量从来不是免费的。', 'rare',
      '{"flags":{"ch3_accepted_mild":true}}',
      '{"coin_multiplier":1.3,"stat_modifier":{"insight":1},"event_prob_modifiers":{"opportunity":0.1},"narrative_tags":["constellation_agent"]}',
      '["title_constellation_hater"]'],
    ['title_constellation_hater', '星座厌恶者', '你拒绝了所有星座的赞助。那些高高在上的存在可以用它们的硬币去买别人的表演——但不包括你的。你的命运由你自己书写。', 'rare',
      '{"permanent_flags":{"星座厌恶者_progress":{"min":1}}}',
      '{"block_sponsors":true,"stat_modifier":{"insight":2,"bond":1},"combat_bonus":{"damage_reduction":0.05},"narrative_tags":["anti_constellation"]}',
      '["title_sponsored","title_constellation_ally"]'],
    ['title_throne_witness', '王座的目睹者', '你站在人群中，看着刘众赫坐上了绝对王座。力量的光芒吞没了他——你知道这意味着什么。王座上的人会死。不是现在，但在不远的未来。你目睹了这一切，却没有出手阻止。', 'rare',
      '{"permanent_flags":{"王座目睹者":true}}',
      '{"stat_modifier":{"insight":1,"cruelty":1},"narrative_tags":["observer"]}',
      '[]'],
    ['title_underworld_walker', '冥界行者', '你死了。然后你重生了。在冥界的黑暗中，你曾与死者对话，在死亡的另一端找到了力量。从此以后，冥界的门对你来说不再是一道屏障——只是一道门槛。', 'rare',
      '{"permanent_flags":{"冥界行者":true}}',
      '{"stat_modifier":{"insight":2,"combat":1},"combat_bonus":{"damage_reduction":0.03},"narrative_tags":["reader"]}',
      '[]'],
    ['title_death_knower', '死亡知识之人', '你与冥界之王谈判过。你知道他的秘密，而他也知道你知道。这种互相制衡的认知让你在冥界与人间的边界地带拥有了他人都没有的特权。', 'rare',
      '{"permanent_flags":{"冥界知识":true}}',
      '{"stat_modifier":{"insight":2,"intelligence":1},"exploration_bonus":{"safety":0.05},"narrative_tags":["reader"]}',
      '[]'],
    ['title_dream_seeker', '梦之追寻者', '你开始追寻「最古老的梦」的真相。每一条线索都指向同一个答案——一个你不敢面对的答案。但你没有停下脚步。真相，不管多么残酷，都必须被揭开。', 'rare',
      '{"permanent_flags":{"梦之知识":{"min":1}}}',
      '{"stat_modifier":{"insight":2},"exploration_bonus":{"safety":0.05},"narrative_tags":["reader"]}',
      '[]'],
    ['title_lonely_reader', '孤独的读者', '你选择了独自面对黑暗。不需要队伍，不需要同伴，不需要任何人的同情。你读过3149章，你知道有些路只能一个人走。而这条路，正属于你。', 'rare',
      '{"stats":{"insight":{"min":12},"bond":{"max":3}}}',
      '{"stat_modifier":{"insight":3,"bond":-2},"unlock_choices":["choice_ch4_08_reflect_journey"],"combat_bonus":{"attack_pct":0.1},"narrative_tags":["lonely_one","reader"]}',
      '["title_story_bearer","title_team_leader"]'],

    // ── Epic 称号 ──
    ['title_negotiator', '与星辰谈判的人', '你没有直接接受或拒绝星座的赞助——你与它们谈判了。用你知道的剧情信息换取更有利的条件。「黑暗中的观察者」同意了你提出的交易。你是第一个让星座让步的化身。', 'epic',
      '{"flags":{"ch3_negotiated_deal":true}}',
      '{"coin_multiplier":1.5,"stat_modifier":{"intelligence":2},"narrative_tags":["constellation_agent"]}',
      '[]'],
    ['title_team_leader', '队伍的头脑', '你不再满足于跟随——你提出了自己的战略。队伍开始按照你的计划行动，刘众赫的目光在你身上多停留了一秒——那可能是认可，也可能是警惕。无论是哪种，你都证明了自己的价值。', 'epic',
      '{"flags":{"ch4_public_claim":true},"stats":{"leadership":{"min":4}}}',
      '{"stat_modifier":{"leadership":2},"unlock_choices":["choice_ch4_02_public_claim"],"narrative_tags":["king"]}',
      '["title_lonely_reader"]'],
    ['title_demon_candidate', '魔王候选人', '你亲自参与了魔王选拔。在化身互相残杀的战场上，你站到了最后。你的名字出现在魔王候选的名单上——星之流所有的频道都沸腾了。星座们疯狂地投下硬币。但这只是开始——成为魔王意味着失去更多。', 'epic',
      '{"permanent_flags":{"魔王候选":true}}',
      '{"stat_modifier":{"combat":3,"cruelty":2,"bond":-2},"combat_bonus":{"attack_pct":0.15},"narrative_tags":["demon_king"]}',
      '[]'],
    ['title_truth_spreader', '真相的宣告者', '你向所有化身揭露了星之流的真相。鬼怪们愤怒地关闭了一个又一个频道，但太迟了——种子已经播下。每一个存活下来的化身现在都知道了：他们不只是演员，他们有能力改写剧本。', 'epic',
      '{"permanent_flags":{"真相传播者":true}}',
      '{"stat_modifier":{"insight":2,"leadership":2},"narrative_tags":["anti_constellation"]}',
      '["title_constellation_ally"]'],
    ['title_constellation_ally', '星座的盟友', '你与星座建立了同盟。「葡萄酒之神」和「黑色火焰的魔王」成为了你的赞助者。两个强大的存在同时站在你这一边——这在星之流的历史上极其罕见。但你也知道，在星座之间保持平衡就像走在刀刃上。', 'epic',
      '{"flags":{"allied_constellations":true},"sponsors_count":{"min":2}}',
      '{"coin_multiplier":2,"stat_modifier":{"intelligence":2,"bond":-1},"narrative_tags":["constellation_agent"]}',
      '["title_constellation_hater","title_truth_spreader"]'],

    // ── Legendary 称号 ──
    ['title_absolute_throne', '绝对王座之主', '你坐上了绝对王座。不是刘众赫，是你。力量穿透你的每一根骨头，星座们的目光变成了愤怒和恐惧。你成为了这片废墟的王——但你也成为了所有星座的公敌。王座很冷。但你已经不在乎了。', 'legendary',
      '{"permanent_flags":{"绝对王座拥有者":true}}',
      '{"stat_modifier":{"leadership":3,"cruelty":2},"coin_multiplier":2,"block_endings":["end_savior","end_companion"],"combat_bonus":{"attack_pct":0.15,"defense_pct":0.1},"pk_bonus":{"attack_pct":0.15,"rating":20},"event_prob_modifiers":{"boss_clue":0.1,"story":0.05},"narrative_tags":["king"]}',
      '["title_demon_king"]'],
    ['title_finale_candidate', '终章候选人', '你的行动和选择让你获得了星之流的认可。你不仅有资格阅读结局——你有资格书写它。通往终章的门在你面前打开。走进去，你必须承担一个作者的责任。', 'legendary',
      '{"titles_count":{"min":3}}',
      '{"unlock_choices":["choice_ch4_01_analyze_prophecy","choice_ch4_07_accept_new_reality"],"narrative_tags":["reader"]}',
      '[]'],
    ['title_story_bearer', '背负故事之人', '你的肩上承载了太多人的希望和故事。刘尚雅的信任、李智慧的勇气、李贤诚的忠诚——这些都是你背负的重量。但你不是被这些故事压垮的——你是被它们支撑着的。', 'legendary',
      '{"stats":{"bond":{"min":8}}}',
      '{"stat_modifier":{"bond":3,"leadership":2},"unlock_choices":["choice_ch4_07_accept_new_reality"],"combat_bonus":{"defense_pct":0.1},"narrative_tags":["reader","sacrifice"]}',
      '["title_lonely_reader","title_demon_king"]'],
    ['title_demon_king', '魔王级化身', '你拥抱了魔王的力量而没有失去自我。你是魔王中的异类——不是为了毁灭，而是为了守护。你用黑暗的力量保护了那些无法保护自己的人。星座频道为你的故事发狂——一个变成魔王的读者？这是他们从未看过的剧本。', 'legendary',
      '{"permanent_flags":{"魔王候选":true},"stats":{"combat":{"min":7}}}',
      '{"stat_modifier":{"combat":5,"cruelty":2,"bond":-2},"unlock_choices":["choice_ch4_06_take_throne"],"combat_bonus":{"attack_pct":0.25},"pk_bonus":{"attack_pct":0.2},"event_prob_modifiers":{"battle":0.15,"hidden":0.1},"narrative_tags":["demon_king"]}',
      '["title_story_bearer","title_absolute_throne"]'],

    // ── Hidden 称号 ──
    ['title_final_chapter_reader', '终章读者', '你阅读了第3150章。那是你从未读到过的章节。那一章的内容不是文字——而是关于你。关于一个读者如何走进故事，如何改变了一切。你的手指在纸页上颤抖——不是因为恐惧，而是因为理解。', 'hidden',
      '{"permanent_flags":{"终章读者":true}}',
      '{"stat_modifier":{"insight":5},"unlock_choices":["choice_ch4_01_analyze_prophecy"],"combat_bonus":{"crit_rate":0.1,"crit_damage":0.2},"exploration_bonus":{"safety":0.1},"event_prob_modifiers":{"hidden":0.1,"finalPage_bonus":0.05},"narrative_tags":["reader","salvation"]}',
      '[]'],
    ['title_true_reader', '全知读者', '你理解了。你不是金独子，你不是任何角色。你是一个读者。而读者的力量不在于书中的魔法或剑技——而在于理解故事的能力。全知读者视角不是关于知道一切，而是关于愿意去理解一切。当你在最后的那面墙中看到镜中的自己时，你终于笑了。', 'hidden',
      '{"titles_count":{"min":5},"stats":{"insight":{"min":12}},"permanent_flags":{"终章读者":true}}',
      '{"stat_modifier":{"insight":5,"intelligence":3,"bond":2},"unlock_choices":["choice_ch3_03_readers_way"],"combat_bonus":{"attack_pct":0.1,"defense_pct":0.1,"crit_rate":0.05},"pk_bonus":{"attack_pct":0.1,"defense_pct":0.1},"event_prob_modifiers":{"hidden":0.1,"story":0.05},"narrative_tags":["reader","salvation"]}',
      '[]'],
  ];

  for (const t of titles) {
    insert.run(...t);
  }
  console.log('Title seed data (ORV adaptation) inserted.');
}

module.exports = { seedTitles };
