-- First story tree growth: branch the ch1 choice into two independent ch2-ch3 routes.
-- Scope: 5 new static events, guarded edits to existing event definitions and Chungmuro unlock.
-- Player rows, resources, decisions and awards are never updated.
-- Repeat execution: old-value guards and INSERT IGNORE make a second run a no-op.
-- Custom static values that differ from the previous seed are skipped; review them manually.
-- Before execution: verify this database has the previous seed/20261031 repair state and back it up.
-- After execution: compare the five new events and the guarded keys with seed_explore.json;
-- inspect affected-row counts and investigate zero-row updates on the first execution.

UPDATE `exploration_events`
SET `choices_json` = JSON_SET(
    `choices_json`,
    '$[1].consequence_text',
    REPLACE(
        JSON_UNQUOTE(JSON_EXTRACT(`choices_json`, '$[1].consequence_text')),
        '手稿上的文字描述的是你的死亡。日期是明天。',
        '手稿上的文字只写到你的名字，后面的字迹一次次淡去，像被什么从未来里抹掉了。'
    )
)
WHERE `event_key` = 'chungmuro_shadow_king_story'
  AND JSON_UNQUOTE(JSON_EXTRACT(`choices_json`, '$[1].consequence_text'))
      LIKE '%手稿上的文字描述的是你的死亡。日期是明天。';

-- First story tree growth: authoritative final state for the new-library SQL seed.
UPDATE `locations`
SET `unlock_conditions_json` = '{"required_level":5,"prerequisite_events_any":["geumho_shelter_first","geumho_shelter_story","dongmyo_whisperer_story"]}'
WHERE `location_key` = 'chungmuro'
  AND JSON_CONTAINS(`unlock_conditions_json`, CAST('{"required_level":5,"prerequisite_events_any":["geumho_shelter_first","geumho_shelter_story"]}' AS JSON)) AND JSON_CONTAINS(CAST('{"required_level":5,"prerequisite_events_any":["geumho_shelter_first","geumho_shelter_story"]}' AS JSON), `unlock_conditions_json`);

UPDATE `exploration_events`
SET `choices_json` = '[{"label":"\\"是的。我知道你是谁——刘众赫。\\"","consequence_text":"刘众赫的表情变化了一瞬——那不是惊讶，而是确认。他盯着你看了很久。\\"你读过那本书。\\"这不是一个疑问句。\\"那就跟上来。别拖后腿。\\"他转身走入了黑暗。","title_bias":{"title_regression_witness":1},"unlock_events":["dongmyo_market_story"],"set_story_flags":{"story_route_companion":true},"unlock_locations":["dongmyo"]},{"label":"\\"我只是一个普通的幸存者。\\"","consequence_text":"刘众赫的嘴角微微上翘。\\"没有人在这个场景里是''普通''的。\\"他转身前说了一句：\\"你会想起来的。到了金湖站来找我。\\"他的身影消失在黑暗中。","unlock_locations":["geumho_station"],"title_bias":{"title_humble_reader":1},"unlock_events":["geumho_shelter_story"],"set_story_flags":{"story_route_nameless":true}}]'
WHERE `event_key` = 'station_first_trace'
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"\\"是的。我知道你是谁——刘众赫。\\"","consequence_text":"刘众赫的表情变化了一瞬——那不是惊讶，而是确认。他盯着你看了很久。\\"你读过那本书。\\"这不是一个疑问句。\\"那就跟上来。别拖后腿。\\"他转身走入了黑暗。","title_bias":{"title_regression_witness":1}},{"label":"\\"我只是一个普通的幸存者。\\"","consequence_text":"刘众赫的嘴角微微上翘。\\"没有人在这个场景里是''普通''的。\\"他转身前说了一句：\\"你会想起来的。到了金湖站来找我。\\"他的身影消失在黑暗中。","unlock_locations":["geumho_station"],"title_bias":{"title_humble_reader":1}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"\\"是的。我知道你是谁——刘众赫。\\"","consequence_text":"刘众赫的表情变化了一瞬——那不是惊讶，而是确认。他盯着你看了很久。\\"你读过那本书。\\"这不是一个疑问句。\\"那就跟上来。别拖后腿。\\"他转身走入了黑暗。","title_bias":{"title_regression_witness":1}},{"label":"\\"我只是一个普通的幸存者。\\"","consequence_text":"刘众赫的嘴角微微上翘。\\"没有人在这个场景里是''普通''的。\\"他转身前说了一句：\\"你会想起来的。到了金湖站来找我。\\"他的身影消失在黑暗中。","unlock_locations":["geumho_station"],"title_bias":{"title_humble_reader":1}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"station_first_trace","choice_indices":[1]}],"personal_contexts":[{"story_flag":"story_route_nameless","text":"你说自己是普通人。在金湖站，这句话让你轻松混进了人群——可你总觉得，暗处有人记住了它。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'geumho_shelter_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"geumho_shelter_story","choice_indices":[0,1]}],"personal_contexts":[{"story_flag":"story_route_nameless","text":"这里没人知道你读过那本书。你打算让它一直这样。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
    `choices_json` = '[{"label":"用故事碎片交换情报 (消耗1碎片)","consequence_text":"男人接过碎片，在蜡烛下仔细端详。\\"好东西。\\"他递给你一张地铁路线图，上面标注了忠武路和东庙的位置。\\"忠武路——那边有''十王''，不好惹。东庙——我哥们在那里开了个市场，报我的名字有折扣。\\"","rewards_override":{"storyFragments":-1,"coins":20,"items":[{"itemKey":"subway_map_fragment","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongmyo"],"unlock_events":["chungmuro_unnamed_crossing"],"title_bias":{"title_information_broker":1}},{"label":"\\"我只是看看。\\"","consequence_text":"\\"随便看。\\"男人耸了耸肩。\\"但别告诉别人这里——金湖站的规矩是：知道得越少，活得越久。\\"他在你转身时又说了一句：\\"对了——如果你去忠武路，小心那个叫''铁王''的。他的拳头比他的脑袋硬十倍。\\"","unlock_events":["chungmuro_unnamed_crossing"],"title_bias":{"title_cautious":1}}]'
WHERE `event_key` = 'geumho_underground_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"geumho_shelter_story","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"geumho_shelter_story","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`)
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"用故事碎片交换情报 (消耗1碎片)","consequence_text":"男人接过碎片，在蜡烛下仔细端详。\\"好东西。\\"他递给你一张地铁路线图，上面标注了忠武路和东庙的位置。\\"忠武路——那边有''十王''，不好惹。东庙——我哥们在那里开了个市场，报我的名字有折扣。\\"","rewards_override":{"storyFragments":-1,"coins":20,"items":[{"itemKey":"subway_map_fragment","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongmyo"],"unlock_events":["chungmuro_iron_king_story"],"title_bias":{"title_information_broker":1}},{"label":"\\"我只是看看。\\"","consequence_text":"\\"随便看。\\"男人耸了耸肩。\\"但别告诉别人这里——金湖站的规矩是：知道得越少，活得越久。\\"他在你转身时又说了一句：\\"对了——如果你去忠武路，小心那个叫''铁王''的。他的拳头比他的脑袋硬十倍。\\"","unlock_events":["chungmuro_iron_king_story"],"title_bias":{"title_cautious":1}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"用故事碎片交换情报 (消耗1碎片)","consequence_text":"男人接过碎片，在蜡烛下仔细端详。\\"好东西。\\"他递给你一张地铁路线图，上面标注了忠武路和东庙的位置。\\"忠武路——那边有''十王''，不好惹。东庙——我哥们在那里开了个市场，报我的名字有折扣。\\"","rewards_override":{"storyFragments":-1,"coins":20,"items":[{"itemKey":"subway_map_fragment","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongmyo"],"unlock_events":["chungmuro_iron_king_story"],"title_bias":{"title_information_broker":1}},{"label":"\\"我只是看看。\\"","consequence_text":"\\"随便看。\\"男人耸了耸肩。\\"但别告诉别人这里——金湖站的规矩是：知道得越少，活得越久。\\"他在你转身时又说了一句：\\"对了——如果你去忠武路，小心那个叫''铁王''的。他的拳头比他的脑袋硬十倍。\\"","unlock_events":["chungmuro_iron_king_story"],"title_bias":{"title_cautious":1}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"station_first_trace","choice_indices":[0]}],"personal_contexts":[{"story_flag":"story_route_companion","text":"刘众赫让你先来东庙。他说这里卖的东西，有一半是从未来偷来的。从你说出他名字的那一刻起，他就在考你。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}',
    `choices_json` = '[{"label":"交换记忆——用一段无关紧要的旧回忆换取新的视野","consequence_text":"老人将你的记忆吸入玻璃瓶，同时打开了另一个瓶子。一段不属于你的记忆涌入脑海——你看到了刘众赫的上一轮回归。他在废弃车站独自面对了什么。为什么他的眼神总是那么疲惫。\\n\\n\\"有趣。\\"老人低声说。\\"你的记忆里……有一本书。一本很厚的书。\\"","rewards_override":{"storyFragments":6,"channelHeat":30,"exp":100},"title_bias":{"title_memory_trader":2},"unlock_events":["dongmyo_whisperer_story"]},{"label":"\\"不，谢谢。我不想失去任何东西。\\"","consequence_text":"\\"明智的选择。不是每个人都能承受不属于自己的记忆。\\"老人收起了玻璃瓶，但递给你一枚铜币。\\"拿着——在东庙市场，这枚硬币能让你买到一些真正的好东西。\\"硬币上铸着一个闭着的眼睛——那只眼睛似乎在看着你。","rewards_override":{"coins":80,"items":[{"itemKey":"memory_coin","quantity":1,"dropRate":1.0}]},"title_bias":{"title_cautious":2},"unlock_events":["dongmyo_whisperer_story"]}]'
WHERE `event_key` = 'dongmyo_market_story'
  AND `required_conditions_json` IS NULL
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"交换记忆——用一段无关紧要的旧回忆换取新的视野","consequence_text":"老人将你的记忆吸入玻璃瓶，同时打开了另一个瓶子。一段不属于你的记忆涌入脑海——你看到了刘众赫的上一轮回归。他在废弃车站独自面对了什么。为什么他的眼神总是那么疲惫。\\n\\n\\"有趣。\\"老人低声说。\\"你的记忆里……有一本书。一本很厚的书。\\"","rewards_override":{"storyFragments":6,"channelHeat":30,"exp":100},"title_bias":{"title_memory_trader":2}},{"label":"\\"不，谢谢。我不想失去任何东西。\\"","consequence_text":"\\"明智的选择。不是每个人都能承受不属于自己的记忆。\\"老人收起了玻璃瓶，但递给你一枚铜币。\\"拿着——在东庙市场，这枚硬币能让你买到一些真正的好东西。\\"硬币上铸着一个闭着的眼睛——那只眼睛似乎在看着你。","rewards_override":{"coins":80,"items":[{"itemKey":"memory_coin","quantity":1,"dropRate":1.0}]},"title_bias":{"title_cautious":2}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"交换记忆——用一段无关紧要的旧回忆换取新的视野","consequence_text":"老人将你的记忆吸入玻璃瓶，同时打开了另一个瓶子。一段不属于你的记忆涌入脑海——你看到了刘众赫的上一轮回归。他在废弃车站独自面对了什么。为什么他的眼神总是那么疲惫。\\n\\n\\"有趣。\\"老人低声说。\\"你的记忆里……有一本书。一本很厚的书。\\"","rewards_override":{"storyFragments":6,"channelHeat":30,"exp":100},"title_bias":{"title_memory_trader":2}},{"label":"\\"不，谢谢。我不想失去任何东西。\\"","consequence_text":"\\"明智的选择。不是每个人都能承受不属于自己的记忆。\\"老人收起了玻璃瓶，但递给你一枚铜币。\\"拿着——在东庙市场，这枚硬币能让你买到一些真正的好东西。\\"硬币上铸着一个闭着的眼睛——那只眼睛似乎在看着你。","rewards_override":{"coins":80,"items":[{"itemKey":"memory_coin","quantity":1,"dropRate":1.0}]},"title_bias":{"title_cautious":2}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"dongmyo_market_story","choice_indices":[0,1]}],"personal_contexts":[{"story_flag":"story_route_companion","text":"刘众赫在市场另一头等你，没有跟过来——他想看你自己能问出什么。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}',
    `choices_json` = '[{"label":"\\"告诉我关于''十王''的一切。\\"","consequence_text":"情报贩子翻了翻笔记本。\\"十王——忠武路的统治者。不是一个人，是十个。最强的叫''铁王''——一个金刚不坏的疯子。最聪明的叫''影王''——没人见过他的脸。他们的规则很简单：要么服从，要么消失。\\"她撕下一页纸递给你。\\"拿去——这是忠武路的入口地图。祝你好运。\\"","rewards_override":{"items":[{"itemKey":"chungmuro_map","quantity":1,"dropRate":1.0}]},"unlock_locations":["chungmuro"],"title_bias":{"title_information_broker":2},"unlock_events":["chungmuro_iron_king_story"]},{"label":"\\"关于星座——它们到底想要什么？\\"","consequence_text":"\\"好问题。\\"情报贩子合上了笔记本。\\"星座不是神——它们只是读者。比我们更古老的读者。它们付钱看故事——用星币。你的每一次选择，每一次战斗，每一次痛苦和胜利——都是它们消费的内容。\\"她指了指你胸口的位置。\\"你在频道里的观众数——决定了一切。\\"","rewards_override":{"channelHeat":40,"storyFragments":3},"title_bias":{"title_starstream_observed":3},"unlock_events":["chungmuro_iron_king_story"]},{"label":"\\"你怎么知道那本书的事？\\"","consequence_text":"情报贩子沉默了一会儿。然后她把镜子转向你——镜子里映出的不是你的脸，而是一本书的封面：《SSSSS级无限回归者》。\\"因为我也读过。\\"她说。\\"每一个情报贩子都是一名读者——只是在不同的章节里。\\"她的声音变得很轻：\\"别告诉任何人我说过这个。有些秘密……最好不要被''作者''发现。\\"","rewards_override":{"storyFragments":8,"channelHeat":25,"exp":120},"title_bias":{"title_omniscient_reader":3},"unlock_events":["chungmuro_iron_king_story"]}]'
WHERE `event_key` = 'dongmyo_whisperer_story'
  AND `required_conditions_json` IS NULL
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"\\"告诉我关于''十王''的一切。\\"","consequence_text":"情报贩子翻了翻笔记本。\\"十王——忠武路的统治者。不是一个人，是十个。最强的叫''铁王''——一个金刚不坏的疯子。最聪明的叫''影王''——没人见过他的脸。他们的规则很简单：要么服从，要么消失。\\"她撕下一页纸递给你。\\"拿去——这是忠武路的入口地图。祝你好运。\\"","rewards_override":{"items":[{"itemKey":"chungmuro_map","quantity":1,"dropRate":1.0}]},"unlock_locations":["chungmuro"],"title_bias":{"title_information_broker":2}},{"label":"\\"关于星座——它们到底想要什么？\\"","consequence_text":"\\"好问题。\\"情报贩子合上了笔记本。\\"星座不是神——它们只是读者。比我们更古老的读者。它们付钱看故事——用星币。你的每一次选择，每一次战斗，每一次痛苦和胜利——都是它们消费的内容。\\"她指了指你胸口的位置。\\"你在频道里的观众数——决定了一切。\\"","rewards_override":{"channelHeat":40,"storyFragments":3},"title_bias":{"title_starstream_observed":3}},{"label":"\\"你怎么知道那本书的事？\\"","consequence_text":"情报贩子沉默了一会儿。然后她把镜子转向你——镜子里映出的不是你的脸，而是一本书的封面：《SSSSS级无限回归者》。\\"因为我也读过。\\"她说。\\"每一个情报贩子都是一名读者——只是在不同的章节里。\\"她的声音变得很轻：\\"别告诉任何人我说过这个。有些秘密……最好不要被''作者''发现。\\"","rewards_override":{"storyFragments":8,"channelHeat":25,"exp":120},"title_bias":{"title_omniscient_reader":3}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"\\"告诉我关于''十王''的一切。\\"","consequence_text":"情报贩子翻了翻笔记本。\\"十王——忠武路的统治者。不是一个人，是十个。最强的叫''铁王''——一个金刚不坏的疯子。最聪明的叫''影王''——没人见过他的脸。他们的规则很简单：要么服从，要么消失。\\"她撕下一页纸递给你。\\"拿去——这是忠武路的入口地图。祝你好运。\\"","rewards_override":{"items":[{"itemKey":"chungmuro_map","quantity":1,"dropRate":1.0}]},"unlock_locations":["chungmuro"],"title_bias":{"title_information_broker":2}},{"label":"\\"关于星座——它们到底想要什么？\\"","consequence_text":"\\"好问题。\\"情报贩子合上了笔记本。\\"星座不是神——它们只是读者。比我们更古老的读者。它们付钱看故事——用星币。你的每一次选择，每一次战斗，每一次痛苦和胜利——都是它们消费的内容。\\"她指了指你胸口的位置。\\"你在频道里的观众数——决定了一切。\\"","rewards_override":{"channelHeat":40,"storyFragments":3},"title_bias":{"title_starstream_observed":3}},{"label":"\\"你怎么知道那本书的事？\\"","consequence_text":"情报贩子沉默了一会儿。然后她把镜子转向你——镜子里映出的不是你的脸，而是一本书的封面：《SSSSS级无限回归者》。\\"因为我也读过。\\"她说。\\"每一个情报贩子都是一名读者——只是在不同的章节里。\\"她的声音变得很轻：\\"别告诉任何人我说过这个。有些秘密……最好不要被''作者''发现。\\"","rewards_override":{"storyFragments":8,"channelHeat":25,"exp":120},"title_bias":{"title_omniscient_reader":3}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"dongmyo_whisperer_story","choice_indices":[0,1,2]}],"personal_contexts":[{"story_flag":"story_route_companion","text":"刘众赫把你留在忠武路入口，自己往深处去了。走前只说了一句：\\"他会让你跪。你自己决定。\\""}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}',
    `choices_json` = '[{"label":"拒绝下跪——\\"我不会向任何人跪下。\\"","consequence_text":"铁王大笑了三声——然后一拳砸过来。你躲开了，但冲击波震得你踉跄后退。\\"有意思！\\"他的笑容很残忍。\\"很久没人敢在忠武路站着了。来吧——让我看看你有多少分量。\\"一场硬仗在所难免。","risks":{"damagePercent":15,"monsterKey":"iron_king_trial","battleName":"铁王的钢铁化试炼","battleDescription":"冲击波造成了约最大生命15%的伤害。铁王释放的钢铁化投影挡在前方；你可以迎战，也可以撤退。撤退不会撤销这次个人剧情选择。"},"rewards_override":{"storyFragments":6,"exp":200,"coins":80},"unlock_events":["chungmuro_shadow_king_story"],"title_bias":{"title_defiant":3},"set_story_flags":{"story_iron_king_defied":true}},{"label":"交出一点故事碎片作为\\"通行费\\"——暂且妥协","consequence_text":"铁王接过你的碎片，在手中掂了掂。\\"聪明。\\"他坐回王座。\\"忠武路有十位王——但只有聪明人活得久。\\"他的手下给你让开了一条路。你失去了碎片，但获得了一个机会——潜入这个王国的内部。","rewards_override":{"storyFragments":-1,"exp":80,"coins":20},"unlock_events":["chungmuro_underground_resistance"],"title_bias":{"title_calculating":2},"set_story_flags":{"story_iron_king_paid":true}}]'
WHERE `event_key` = 'chungmuro_iron_king_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"geumho_underground_story","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"geumho_underground_story","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`)
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"拒绝下跪——\\"我不会向任何人跪下。\\"","consequence_text":"铁王大笑了三声——然后一拳砸过来。你躲开了，但冲击波震得你踉跄后退。\\"有意思！\\"他的笑容很残忍。\\"很久没人敢在忠武路站着了。来吧——让我看看你有多少分量。\\"一场硬仗在所难免。","risks":{"damagePercent":15,"monsterKey":"iron_king_trial","battleName":"铁王的钢铁化试炼","battleDescription":"冲击波造成了约最大生命15%的伤害。铁王释放的钢铁化投影挡在前方；你可以迎战，也可以撤退。撤退不会撤销这次个人剧情选择。"},"rewards_override":{"storyFragments":6,"exp":200,"coins":80},"unlock_events":["chungmuro_shadow_king_story"],"title_bias":{"title_defiant":3}},{"label":"交出一点故事碎片作为\\"通行费\\"——暂且妥协","consequence_text":"铁王接过你的碎片，在手中掂了掂。\\"聪明。\\"他坐回王座。\\"忠武路有十位王——但只有聪明人活得久。影王会喜欢你的。\\"他的手下给你让开了一条路。你失去了碎片，但获得了一个机会——潜入这个王国的内部。","rewards_override":{"storyFragments":-1,"exp":80,"coins":20},"unlock_events":["chungmuro_shadow_king_story","chungmuro_underground_resistance"],"title_bias":{"title_calculating":2}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"拒绝下跪——\\"我不会向任何人跪下。\\"","consequence_text":"铁王大笑了三声——然后一拳砸过来。你躲开了，但冲击波震得你踉跄后退。\\"有意思！\\"他的笑容很残忍。\\"很久没人敢在忠武路站着了。来吧——让我看看你有多少分量。\\"一场硬仗在所难免。","risks":{"damagePercent":15,"monsterKey":"iron_king_trial","battleName":"铁王的钢铁化试炼","battleDescription":"冲击波造成了约最大生命15%的伤害。铁王释放的钢铁化投影挡在前方；你可以迎战，也可以撤退。撤退不会撤销这次个人剧情选择。"},"rewards_override":{"storyFragments":6,"exp":200,"coins":80},"unlock_events":["chungmuro_shadow_king_story"],"title_bias":{"title_defiant":3}},{"label":"交出一点故事碎片作为\\"通行费\\"——暂且妥协","consequence_text":"铁王接过你的碎片，在手中掂了掂。\\"聪明。\\"他坐回王座。\\"忠武路有十位王——但只有聪明人活得久。影王会喜欢你的。\\"他的手下给你让开了一条路。你失去了碎片，但获得了一个机会——潜入这个王国的内部。","rewards_override":{"storyFragments":-1,"exp":80,"coins":20},"unlock_events":["chungmuro_shadow_king_story","chungmuro_underground_resistance"],"title_bias":{"title_calculating":2}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}'
WHERE `event_key` = 'chungmuro_shadow_king_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}',
    `choices_json` = '[{"label":"\\"星流图书馆？在哪里？\\"","consequence_text":"\\"在明洞。\\"朴素英摊开一张旧地图，看了一眼，又把它折了回去。\\"但不是现在。铁王的人最近盯得紧，谁往外走都会被跟上。\\"她把地图塞回怀里，\\"等时机到了，我们会来找你。\\"","unlock_events":["chungmuro_compromise_ledger"],"title_bias":{"title_revolutionary":2}},{"label":"\\"我不是革命者。我只是想活下去。\\"","consequence_text":"朴素英看了你好一会儿。\\"每个人都想活下去。\\"她说。\\"但在忠武路——仅仅活下去是不够的。铁王迟早会来找你。到那时候——你打算怎么办？\\"她没有等你回答。\\"当你改变主意的时候——我们知道去哪里找你。\\"","title_bias":{"title_survivor":2},"unlock_events":["chungmuro_compromise_ledger"]}]'
WHERE `event_key` = 'chungmuro_underground_resistance'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"chungmuro_iron_king_story","choice_indices":[1]}]}' AS JSON), `required_conditions_json`)
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"\\"星流图书馆？在哪里？\\"","consequence_text":"\\"在明洞。\\"朴素英摊开一张旧地图。\\"明洞的地下商业街——曾经的购物天堂，现在的禁忌区域。图书馆就藏在那里。馆中有一本书——《故事之书》，据说记载了所有已发生和将要发生的剧本。\\"她将地图递给你。\\"把它带回来。整个抵抗组织——不，整个忠武路——都需要它。\\"","unlock_locations":["myeongdong"],"unlock_events":["myeongdong_library_story"],"title_bias":{"title_revolutionary":2}},{"label":"\\"我不是革命者。我只是想活下去。\\"","consequence_text":"朴素英看了你好一会儿。\\"每个人都想活下去。\\"她说。\\"但在忠武路——仅仅活下去是不够的。铁王迟早会来找你。到那时候——你打算怎么办？\\"她没有等你回答。\\"当你改变主意的时候——我们知道去哪里找你。\\"","title_bias":{"title_survivor":2}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"\\"星流图书馆？在哪里？\\"","consequence_text":"\\"在明洞。\\"朴素英摊开一张旧地图。\\"明洞的地下商业街——曾经的购物天堂，现在的禁忌区域。图书馆就藏在那里。馆中有一本书——《故事之书》，据说记载了所有已发生和将要发生的剧本。\\"她将地图递给你。\\"把它带回来。整个抵抗组织——不，整个忠武路——都需要它。\\"","unlock_locations":["myeongdong"],"unlock_events":["myeongdong_library_story"],"title_bias":{"title_revolutionary":2}},{"label":"\\"我不是革命者。我只是想活下去。\\"","consequence_text":"朴素英看了你好一会儿。\\"每个人都想活下去。\\"她说。\\"但在忠武路——仅仅活下去是不够的。铁王迟早会来找你。到那时候——你打算怎么办？\\"她没有等你回答。\\"当你改变主意的时候——我们知道去哪里找你。\\"","title_bias":{"title_survivor":2}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"chungmuro_shadow_king_story","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"chungmuro_iron_king_story","choice_indices":[0]}]}',
    `choices_json` = '[{"label":"站出来揭露真相——告诉他们\\"作者\\"的存在","consequence_text":"你站了起来。九位王的目光集中在你身上。\\"各位——你们不是王。你们是角色。在一本小说里。\\"会议室里一片死寂。纸王手中的纸停止改写——然后开始疯狂翻页。\\"把这个人赶出去！\\"有人喊道。但纸王举起手。\\"让他说完。\\"他说。\\"我有一种感觉——他说的可能是唯一的真相。\\"","rewards_override":{"storyFragments":10,"channelHeat":50,"exp":250},"title_bias":{"title_truth_bringer":5},"set_story_flags":{"story_council_spoke":true},"route_pending":true},{"label":"保持沉默，暗中观察会议的走向","consequence_text":"会议继续进行。纸王展示了一页被撕裂的剧本——上面描述了忠武路在一个月后的毁灭。\\"这不是预言——这是设定。但设定可以被改写。\\"你意识到,影王让你来这里不只是为了偷听——而是为了让你知道：这个世界不是真实的。它是被书写出来的。而书写它的笔——也许就在你手中。","rewards_override":{"storyFragments":6,"exp":120,"channelHeat":30},"title_bias":{"title_silent_watcher":3},"set_story_flags":{"story_council_silent":true},"route_pending":true}]'
WHERE `event_key` = 'chungmuro_conspiracy_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"chungmuro_shadow_king_story","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"chungmuro_shadow_king_story","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`)
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"站出来揭露真相——告诉他们\\"作者\\"的存在","consequence_text":"你站了起来。九位王的目光集中在你身上。\\"各位——你们不是王。你们是角色。在一本小说里。\\"会议室里一片死寂。纸王手中的纸停止改写——然后开始疯狂翻页。\\"把这个人赶出去！\\"有人喊道。但纸王举起手。\\"让他说完。\\"他说。\\"我有一种感觉——他说的可能是唯一的真相。\\"","rewards_override":{"storyFragments":10,"channelHeat":50,"exp":250},"unlock_events":["chungmuro_revelation_story"],"title_bias":{"title_truth_bringer":5}},{"label":"保持沉默，暗中观察会议的走向","consequence_text":"会议继续进行。纸王展示了一页被撕裂的剧本——上面描述了忠武路在一个月后的毁灭。\\"这不是预言——这是设定。但设定可以被改写。\\"你意识到,影王让你来这里不只是为了偷听——而是为了让你知道：这个世界不是真实的。它是被书写出来的。而书写它的笔——也许就在你手中。","rewards_override":{"storyFragments":6,"exp":120,"channelHeat":30},"unlock_events":["chungmuro_revelation_story"],"title_bias":{"title_silent_watcher":3}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"站出来揭露真相——告诉他们\\"作者\\"的存在","consequence_text":"你站了起来。九位王的目光集中在你身上。\\"各位——你们不是王。你们是角色。在一本小说里。\\"会议室里一片死寂。纸王手中的纸停止改写——然后开始疯狂翻页。\\"把这个人赶出去！\\"有人喊道。但纸王举起手。\\"让他说完。\\"他说。\\"我有一种感觉——他说的可能是唯一的真相。\\"","rewards_override":{"storyFragments":10,"channelHeat":50,"exp":250},"unlock_events":["chungmuro_revelation_story"],"title_bias":{"title_truth_bringer":5}},{"label":"保持沉默，暗中观察会议的走向","consequence_text":"会议继续进行。纸王展示了一页被撕裂的剧本——上面描述了忠武路在一个月后的毁灭。\\"这不是预言——这是设定。但设定可以被改写。\\"你意识到,影王让你来这里不只是为了偷听——而是为了让你知道：这个世界不是真实的。它是被书写出来的。而书写它的笔——也许就在你手中。","rewards_override":{"storyFragments":6,"exp":120,"channelHeat":30},"unlock_events":["chungmuro_revelation_story"],"title_bias":{"title_silent_watcher":3}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"chungmuro_unnamed_crossing","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
    `choices_json` = '[{"label":"在剧本上写下——\\"忠武路的所有人联合起来，击败了地狱之火。\\"","consequence_text":"稿纸发出刺眼的光芒。纸王震惊地看着你。\\"你——你改写了它？\\"他反复检查那行新出现的文字。\\"这不是预测——这是新的剧本！\\"窗外，你看到十王们开始行动——不是彼此争斗，而是联合布防。读者的一笔——改变了忠武路的命运。\\n\\n但纸王注意到了一件事：那行文字的下面——在很远很远的段落里——写着你的名字，和一个日期。你看不清那是哪一天。","rewards_override":{"storyFragments":15,"exp":400,"channelHeat":80,"coins":150,"items":[{"itemKey":"剧本笔迹","quantity":1,"dropRate":1.0}]},"title_bias":{"title_script_rewriter":5},"unlock_events":["chungmuro_script_blank"]},{"label":"合上剧本——\\"有些未来最好不要知道。\\"","consequence_text":"纸王叹了口气。\\"也许你是对的。\\"他收起了稿纸。\\"不知为何——我觉得你还会再看到这页纸的。\\"他给你一张通行证——上面盖着十王的十枚印章。\\"拿着。不管忠武路未来如何——你都可以在这里自由通行。\\"\\n\\n但你心里清楚：你只是推迟了一个问题的答案。剧本上那段关于你的文字，迟早会写完。","rewards_override":{"storyFragments":5,"exp":150,"coins":100,"items":[{"itemKey":"ten_kings_pass","quantity":1,"dropRate":1.0}]},"title_bias":{"title_acceptance":3},"unlock_events":["chungmuro_script_blank"]}]'
WHERE `event_key` = 'chungmuro_revelation_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"chungmuro_conspiracy_story","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"chungmuro_conspiracy_story","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`)
  AND JSON_CONTAINS(`choices_json`, CAST('[{"label":"在剧本上写下——\\"忠武路的所有人联合起来，击败了地狱之火。\\"","consequence_text":"稿纸发出刺眼的光芒。纸王震惊地看着你。\\"你——你改写了它？\\"他反复检查那行新出现的文字。\\"这不是预测——这是新的剧本！\\"窗外，你看到十王们开始行动——不是彼此争斗，而是联合布防。读者的一笔——改变了忠武路的命运。\\n\\n但纸王注意到了一件事：那行文字的下面——在很远很远的段落里——写着你的名字。和你的死亡日期。","rewards_override":{"storyFragments":15,"exp":400,"channelHeat":80,"coins":150,"items":[{"itemKey":"剧本笔迹","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongdaemun"],"title_bias":{"title_script_rewriter":5}},{"label":"合上剧本——\\"有些未来最好不要知道。\\"","consequence_text":"纸王叹了口气。\\"也许你是对的。\\"他收起了稿纸。\\"不知为何——我觉得你还会再看到这页纸的。\\"他给你一张通行证——上面盖着十王的十枚印章。\\"拿着。不管忠武路未来如何——你都可以在这里自由通行。\\"\\n\\n但你心里清楚：你只是推迟了一个问题的答案。剧本上那段关于你的文字，迟早会写完。","rewards_override":{"storyFragments":5,"exp":150,"coins":100,"items":[{"itemKey":"ten_kings_pass","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongdaemun"],"title_bias":{"title_acceptance":3}}]' AS JSON)) AND JSON_CONTAINS(CAST('[{"label":"在剧本上写下——\\"忠武路的所有人联合起来，击败了地狱之火。\\"","consequence_text":"稿纸发出刺眼的光芒。纸王震惊地看着你。\\"你——你改写了它？\\"他反复检查那行新出现的文字。\\"这不是预测——这是新的剧本！\\"窗外，你看到十王们开始行动——不是彼此争斗，而是联合布防。读者的一笔——改变了忠武路的命运。\\n\\n但纸王注意到了一件事：那行文字的下面——在很远很远的段落里——写着你的名字。和你的死亡日期。","rewards_override":{"storyFragments":15,"exp":400,"channelHeat":80,"coins":150,"items":[{"itemKey":"剧本笔迹","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongdaemun"],"title_bias":{"title_script_rewriter":5}},{"label":"合上剧本——\\"有些未来最好不要知道。\\"","consequence_text":"纸王叹了口气。\\"也许你是对的。\\"他收起了稿纸。\\"不知为何——我觉得你还会再看到这页纸的。\\"他给你一张通行证——上面盖着十王的十枚印章。\\"拿着。不管忠武路未来如何——你都可以在这里自由通行。\\"\\n\\n但你心里清楚：你只是推迟了一个问题的答案。剧本上那段关于你的文字，迟早会写完。","rewards_override":{"storyFragments":5,"exp":150,"coins":100,"items":[{"itemKey":"ten_kings_pass","quantity":1,"dropRate":1.0}]},"unlock_locations":["dongdaemun"],"title_bias":{"title_acceptance":3}}]' AS JSON), `choices_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'dongdaemun_flame_giant_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"dongdaemun_flame_giant_story","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'dongdaemun_missing_chapter_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"dongdaemun_flame_giant_story","choice_indices":[0]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"dongdaemun_flame_giant_story","choice_indices":[0]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'dongdaemun_decision_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"chungmuro_underground_resistance","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'myeongdong_library_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"chungmuro_underground_resistance","choice_indices":[0]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"chungmuro_underground_resistance","choice_indices":[0]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"myeongdong_library_story","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'myeongdong_forbidden_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"myeongdong_library_story","choice_indices":[0]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"myeongdong_library_story","choice_indices":[0]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'gwangjin_bridge_trap_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'gwangjin_bridge_guardian_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'gangnam_dungeon_gate_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"gangnam_dungeon_gate_story","choice_indices":[0]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'gangnam_deep_floor_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"gangnam_dungeon_gate_story","choice_indices":[0]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"gangnam_dungeon_gate_story","choice_indices":[0]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"gangnam_deep_floor_story","choice_indices":[1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'gangnam_memory_wall_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"gangnam_deep_floor_story","choice_indices":[1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"gangnam_deep_floor_story","choice_indices":[1]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'seoul_forest_world_tree_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'demon_gate_first_step_story'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_events":["demon_gate_first_step_story"],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'ending_old_dream_promise'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"required_events":["demon_gate_first_step_story"]}' AS JSON)) AND JSON_CONTAINS(CAST('{"required_events":["demon_gate_first_step_story"]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_events":["ending_old_dream_promise"],"choice_unlockers":[{"event_key":"ending_old_dream_promise","choice_indices":[0]}],"personal_contexts":[{"story_flag":"oldest_dream_location","text":"你想起白发读者曾让你看见的病房与沉睡少年。眼前这个终点，因此不再只是一个抽象的故事结尾。"},{"story_flag":"oldest_dream_last_page","text":"你记得最古之梦说过：最后一页还没有写出来，而你正在用自己的选择写下它。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'ending_dream_continuation_choice'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"required_events":["ending_old_dream_promise"],"choice_unlockers":[{"event_key":"ending_old_dream_promise","choice_indices":[0]}],"personal_contexts":[{"story_flag":"oldest_dream_location","text":"你想起白发读者曾让你看见的病房与沉睡少年。眼前这个终点，因此不再只是一个抽象的故事结尾。"},{"story_flag":"oldest_dream_last_page","text":"你记得最古之梦说过：最后一页还没有写出来，而你正在用自己的选择写下它。"}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"required_events":["ending_old_dream_promise"],"choice_unlockers":[{"event_key":"ending_old_dream_promise","choice_indices":[0]}],"personal_contexts":[{"story_flag":"oldest_dream_location","text":"你想起白发读者曾让你看见的病房与沉睡少年。眼前这个终点，因此不再只是一个抽象的故事结尾。"},{"story_flag":"oldest_dream_last_page","text":"你记得最古之梦说过：最后一页还没有写出来，而你正在用自己的选择写下它。"}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_events":["ending_dream_continuation_choice"],"personal_contexts":[{"story_flag":"ending_new_oldest_dream","text":"你选择接续梦境。终墙的光影没有把你的选择改写成原著人物的经历，只为你的时间线留下另一段回声。"},{"story_flag":"ending_world_frozen","text":"你选择让世界停在此刻。这里不是世界恢复运转，而是终局留下的一段个人回声；它不会撤销你的结局选择。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'last_wall_echo_personal_story'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"required_events":["ending_dream_continuation_choice"],"personal_contexts":[{"story_flag":"ending_new_oldest_dream","text":"你选择接续梦境。终墙的光影没有把你的选择改写成原著人物的经历，只为你的时间线留下另一段回声。"},{"story_flag":"ending_world_frozen","text":"你选择让世界停在此刻。这里不是世界恢复运转，而是终局留下的一段个人回声；它不会撤销你的结局选择。"}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"required_events":["ending_dream_continuation_choice"],"personal_contexts":[{"story_flag":"ending_new_oldest_dream","text":"你选择接续梦境。终墙的光影没有把你的选择改写成原著人物的经历，只为你的时间线留下另一段回声。"},{"story_flag":"ending_world_frozen","text":"你选择让世界停在此刻。这里不是世界恢复运转，而是终局留下的一段个人回声；它不会撤销你的结局选择。"}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'darkest_spring_first_omen'
  AND `required_conditions_json` IS NULL;

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"darkest_spring_first_omen","choice_indices":[1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'darkest_spring_forest_vision'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"darkest_spring_first_omen","choice_indices":[1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"darkest_spring_first_omen","choice_indices":[1]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"required_events":["darkest_spring_first_omen","darkest_spring_forest_vision"],"required_side_events":["geumho_echo_whisper"],"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'darkest_spring_story_stitch'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"required_events":["darkest_spring_first_omen","darkest_spring_forest_vision"],"required_side_events":["geumho_echo_whisper"],"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"required_events":["darkest_spring_first_omen","darkest_spring_forest_vision"],"required_side_events":["geumho_echo_whisper"],"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}],"personal_contexts":[{"story_flag":"story_dark_spring_interwoven","text":"你记得那次把日记末页与钟声警告并置的选择；它们在你的记录中接成了一条线。"},{"story_flag":"story_dark_spring_separated","text":"你曾选择把日记与钟声分开保存；此刻你仍能分清哪些是读到的，哪些是推想。"}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'darkest_spring_abyss_gate'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}],"personal_contexts":[{"story_flag":"story_dark_spring_interwoven","text":"你记得那次把日记末页与钟声警告并置的选择；它们在你的记录中接成了一条线。"},{"story_flag":"story_dark_spring_separated","text":"你曾选择把日记与钟声分开保存；此刻你仍能分清哪些是读到的，哪些是推想。"}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"darkest_spring_forest_vision","choice_indices":[0,1]}],"personal_contexts":[{"story_flag":"story_dark_spring_interwoven","text":"你记得那次把日记末页与钟声警告并置的选择；它们在你的记录中接成了一条线。"},{"story_flag":"story_dark_spring_separated","text":"你曾选择把日记与钟声分开保存；此刻你仍能分清哪些是读到的，哪些是推想。"}]}' AS JSON), `required_conditions_json`);

UPDATE `exploration_events`
SET `required_conditions_json` = '{"choice_unlockers":[{"event_key":"darkest_spring_abyss_gate","choice_indices":[1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]},{"event_key":"station_first_trace","choice_indices":[1]}]}'
WHERE `event_key` = 'darkest_spring_seed_of_hope'
  AND JSON_CONTAINS(`required_conditions_json`, CAST('{"choice_unlockers":[{"event_key":"darkest_spring_abyss_gate","choice_indices":[1]}]}' AS JSON)) AND JSON_CONTAINS(CAST('{"choice_unlockers":[{"event_key":"darkest_spring_abyss_gate","choice_indices":[1]}]}' AS JSON), `required_conditions_json`);

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`, `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('chungmuro_unnamed_crossing',
 'story',
 'vol1_ch3',
 'chungmuro',
 '被认出',
 '忠武路的入口没有守卫，只有一张折叠桌。一个戴圆框眼镜的瘦小男人坐在桌后，面前摊着一本厚厚的名册——十王的录事。每个进站的人都要报上名字，他就在名册上写一行。\n\n轮到你时，他的笔停住了。他抬头看了你很久，然后把名册转过来：你的名字已经在上面了，笔迹不是他的。旁边一行小字——「读过那本书的人」。\n\n"我没见过你。"录事压低声音，"可名册认识你。"',
 12,
 7,
 0,
 '{"choice_unlockers":[{"event_key":"geumho_underground_story","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
 '{}',
 '{"storyPityReset":true}',
 '[{"label":"是我。我读过。","consequence_text":"录事的手抖了一下。他合上名册，四下看了一眼，才凑近说：\\"这一页不是我写的，是它自己长出来的。\\"他撕下一角纸条塞给你，上面只有一个地址。\\"纸王想见你。他找这一行字，找了很久。\\"","rewards_override":{"storyFragments":5,"exp":120,"channelHeat":30},"unlock_events":["chungmuro_revelation_story"],"set_story_flags":{"story_nameless_admitted":true},"title_bias":{"title_omniscient_reader":2}},{"label":"你认错人了。我叫……","consequence_text":"你报出一个随口编的名字。录事看看你，又看看名册，最终在那一行旁边画了个问号。\\"进去吧。\\"走过闸机时，你听见身后笔尖划纸的声音——他把那一行字，划掉了一半。","rewards_override":{"exp":100,"coins":60,"channelHeat":15},"unlock_events":["chungmuro_nameless_drift"],"set_story_flags":{"story_nameless_hidden":true},"title_bias":{"title_survivor":2}}]',
 NULL);

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`, `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('chungmuro_nameless_drift',
 'story',
 'vol1_ch3',
 'chungmuro',
 '名单上的红圈',
 '你在忠武路待了三天。白天跟着搬运队往站台上扛物资，夜里睡在通风口旁边。没人多看你一眼——这正是你想要的。\n\n第四天早上，搬运队的工头点名。点到最后，他看了看手里的纸，又看了看你。"你，"他说，"上面要见你。"\n\n那张名单上，你的名字被人用红笔单独圈了出来，旁边是半截问号。',
 12,
 7,
 0,
 '{"choice_unlockers":[{"event_key":"chungmuro_unnamed_crossing","choice_indices":[1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
 '{}',
 '{"storyPityReset":true}',
 '[{"label":"是谁要见我？","consequence_text":"工头耸耸肩。\\"不知道。名单是从地下送上来的。\\"他压低声音：\\"劝你别去。去过的人，回来以后都不太说话了。\\"他把名单塞回口袋，走前多给了你一个罐头。","rewards_override":{"exp":90,"coins":50},"unlock_events":["chungmuro_hidden_reader_wall"],"title_bias":{"title_cautious":2}},{"label":"今天先不去了。","consequence_text":"你又扛了一整天箱子。可到了晚上，铺盖上多了一张纸，画着一条往地下去的路线，终点是一面墙。纸的背面写着：\\"你迟早会来的。我们都是。\\"","rewards_override":{"storyFragments":4,"channelHeat":25},"unlock_events":["chungmuro_hidden_reader_wall"],"title_bias":{"title_silent_watcher":2}}]',
 NULL);

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`, `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('chungmuro_hidden_reader_wall',
 'story',
 'vol1_ch3',
 'chungmuro',
 '读者之墙',
 '地下通道尽头，是一面写满粉笔字的墙。字迹各不相同：有的工整，有的潦草，有的被水渍泡得只剩一半。\n\n你很快看出来，他们都在写同一件事——他们读过一本书，他们藏了起来。\n\n最新的一行，粉笔灰还没落干净："下一个来的人：你可以继续藏，也可以写下你的名字。不论哪种，你都不再是一个人。"',
 12,
 7,
 0,
 '{"choice_unlockers":[{"event_key":"chungmuro_nameless_drift","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
 '{}',
 '{"storyPityReset":true}',
 '[{"label":"拿起粉笔，写下自己的名字。","consequence_text":"你写得很慢。最后一笔落下时，墙上有几行旧字微微亮了一下，像是有人在很远的地方点了点头。你知道，从今天起，会有人来找你。","rewards_override":{"storyFragments":8,"exp":180,"channelHeat":40},"set_story_flags":{"story_nameless_signed_wall":true},"route_pending":true,"title_bias":{"title_first_reader":2}},{"label":"什么也不写，把每一行都读完。","consequence_text":"你读到深夜，把每个名字、每句话都记在心里。离开时，你在墙角画了一个很小的记号，只有读过这面墙的人才认得出。你还没准备好被找到——但你已经知道，他们在哪里。","rewards_override":{"storyFragments":6,"exp":150,"coins":80},"set_story_flags":{"story_nameless_read_wall":true},"route_pending":true,"title_bias":{"title_wall_listener":2}}]',
 NULL);

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`, `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('chungmuro_script_blank',
 'story',
 'vol1_ch3',
 'chungmuro',
 '空白的一页',
 '纸王带你走进他的书房。四面墙都是稿纸，每一张都在轻轻翻动，像在呼吸。他从最底下抽出一页，铺在你面前。\n\n那一页最上方写着你的名字，下面是你从车厢里醒来之后做过的每一件事，一行不差。再往下，本该写着日子的那一栏，是一大片空白。\n\n"所有人的那一页，我都能看到结尾。"纸王说，"只有你的，是空的。"他第一次露出了一点不安，"要么你很快会被删掉——要么，你的结局，还没有人敢写。"',
 12,
 7,
 0,
 '{"choice_unlockers":[{"event_key":"chungmuro_revelation_story","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[1]}]}',
 '{}',
 '{"storyPityReset":true}',
 '[{"label":"那就由我自己来写。","consequence_text":"你拿起他桌上的笔。笔尖碰到纸面的瞬间，空白处浮出一行很淡的字，又迅速退了回去，像是什么东西在犹豫。纸王屏住呼吸：\\"它……在等你。\\"你把笔放下。还不是时候——但你已经知道，这一页会听你的。","rewards_override":{"storyFragments":10,"exp":200,"channelHeat":50},"set_story_flags":{"story_script_self_written":true},"route_pending":true,"title_bias":{"title_script_rewriter":3}},{"label":"先空着吧。空白也是一种答案。","consequence_text":"纸王愣了很久，然后笑了——你第一次见他笑。\\"你是我见过的第一个，不急着知道自己结局的读者。\\"他把那一页小心地夹回最底下，\\"我替你守着。等你想好了，再来。\\"","rewards_override":{"storyFragments":8,"exp":160,"coins":100},"set_story_flags":{"story_script_kept_blank":true},"route_pending":true,"title_bias":{"title_acceptance":3}}]',
 NULL);

INSERT IGNORE INTO `exploration_events`
(`event_key`, `event_type`, `stage_key`, `location_key`, `name`, `description`, `weight`, `stamina_cost`, `repeatable`, `required_conditions_json`, `rewards_json`, `progress_effects_json`, `choices_json`, `log_template`)
VALUES
('chungmuro_compromise_ledger',
 'story',
 'vol1_ch3',
 'chungmuro',
 '另一本账',
 '交了通行费之后，铁王的人把你当成了自己人。第三天，他们派你去仓库清点物资。\n\n你在最底层的抽屉里翻出另一本账。上面记的不是罐头，是人——每一个进过忠武路的「读者」：名字，来的日子，交了多少碎片。\n\n最新一页上是你的名字。铁王用粗重的笔迹在旁边批了一句："交了钱的那个。黑衣服那人带来的。留着。"',
 12,
 7,
 0,
 '{"choice_unlockers":[{"event_key":"chungmuro_underground_resistance","choice_indices":[0,1]}],"required_choices":[{"event_key":"station_first_trace","choice_indices":[0]}]}',
 '{}',
 '{"storyPityReset":true}',
 '[{"label":"把这一页撕下来带走。","consequence_text":"纸撕开的声音在仓库里格外响。你把它折好，贴身放着。那天夜里，朴素英出现在你的铺位旁——她说过，她们知道去哪里找你。她看完那一页，沉默了很久：\\"他留着你，是因为你还有用。\\"她把纸还给你，\\"那就让他看看，你到底有什么用。\\"","rewards_override":{"storyFragments":8,"exp":180,"channelHeat":35},"set_story_flags":{"story_ledger_taken":true},"route_pending":true,"title_bias":{"title_revolutionary":2}},{"label":"把账本放回原处，假装没看见。","consequence_text":"你把抽屉推回去，连灰尘的印子都抹平了。在铁王眼里，你还是那个交了钱、低着头的人——在忠武路，这就是最好的掩护。走出仓库时，你想起车厢里那个黑衣男人看你的眼神：被人小看，有时是一种保护。","rewards_override":{"storyFragments":6,"exp":150,"coins":90},"set_story_flags":{"story_ledger_hidden":true},"route_pending":true,"title_bias":{"title_calculating":2}}]',
 NULL);
