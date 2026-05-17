// 全知读者视角 改编游戏剧情种子数据 (Round 9: 第一卷完整重写)
// 基于 sing N song 原作第一卷结构改编
// 4 个 main_chapter，32 个 story node + 1 卷终结局

function seedChapters(db) {
  const insertChapter = db.prepare(`
    INSERT OR IGNORE INTO chapters (chapter_key, title, summary, order_index, is_ending_chapter, main_chapter_key)
    VALUES (?, ?, ?, ?, ?, ?)
  `);

  const chapters = [
    // ══════════════════════════════════════════════════════════════
    // Main Chapter 1: 付费服务开始 (main_ch01_paid_service)
    // ══════════════════════════════════════════════════════════════

    ['ch1_01_last_train', '最后一班地铁',
      '地铁在昏暗的隧道中穿行。你靠在门边，手机屏幕的光映在脸上——这是《在灭亡的世界中存活的三种方法》的最终话。3149章，十年的连载，今天结束了。你翻到最后一页，文字在屏幕上淡去。就在这时，地铁猛地一震。灯光闪烁。周围乘客的手机同时失去信号。你抬起头，意识到有什么东西正在降临。',
      1, 0, 'main_ch01_paid_service'],

    ['ch1_02_dokkaebi_appears', '鬼怪降临',
      '一团蓝色的光雾在车厢中央凝聚。从光雾中走出了一个矮小的身影——它的皮肤灰白，头上长着两只角，身上披着破旧的黑袍。一只鬼怪。它发出尖锐得令人牙酸的笑声：「亲爱的乘客们，恭喜你们！你们被选中参与一场非常——特别的剧本。」蓝光从它的指尖扩散，扫过每一个人的头顶，留下一串串数字。倒计时浮现：15分钟。第一个剧本正式公布——「在15分钟内杀死一个或以上的生命体。失败者将被处决。」车厢内爆发出尖叫。但你没有叫。因为你知道规则。',
      2, 0, 'main_ch01_paid_service'],

    ['ch1_03_first_scenario', '第一个剧本：杀生',
      '鬼怪的话音刚落，空气中出现了一道透明的屏障，将整个车厢封锁。15分钟的倒计时开始跳动。周围的乘客从惊慌转为混乱——有人在哭泣，有人在砸窗，有人对着空气挥舞拳头。但你注意到角落里那个穿着西装的中年男人——金代理。在小说中，他将在几分钟后被一只从通风口钻出的巨型昆虫咬死。那只昆虫，就是第一个剧本的活路。它足够巨大，杀死它就能满足剧本条件。但它不是唯一的答案。',
      3, 0, 'main_ch01_paid_service'],

    ['ch1_04_insect_kill', '昆虫之死',
      '巨型昆虫从通风管道中挤出身体，黑色的甲壳在车厢灯光下折射出油亮的光泽。人群尖叫着后退。但你知道它的弱点——在小说中，这只昆虫的关节处有一道旧伤。你只需要利用身边的物品就能解决它。虫鸣和人的哭喊混合在一起，倒计时只剩下最后三分钟。',
      4, 0, 'main_ch01_paid_service'],

    ['ch1_05_aftermath', '第一波冲击之后',
      '昆虫的尸体化为一缕黑烟消散了。蓝光在你的头顶闪烁了一下——第一个剧本完成。你周围的人们瘫坐在地，有些人开始呕吐，有些人抱在一起哭泣。但不是所有人都活了下来。你看向那些空出来的座位。鬼怪还没有回来。在短暂的间隙中，你需要决定如何度过这片刻的安宁。幸存者们开始用不同的眼光看向彼此——恐慌正在转化为别的东西：怀疑、贪婪、以及求生的本能。',
      5, 0, 'main_ch01_paid_service'],

    ['ch1_06_second_scenario', '第二剧本：穿越隧道',
      '鬼怪重新出现了。它的笑容比之前更加灿烂：「做得好，亲爱的化身们！但如果你以为这就结束了，那你一定是这辆地铁上的新乘客。」它挥了挥手，车厢尽头的隧道墙壁裂开了，露出后面更深层的黑暗。「第二个剧本——穿越这条隧道，到达前方站台。当然，隧道里不只有你一个人。祝你好运！」前方的黑暗中传来低沉的嘶吼声。隧道里有什么东西在等着。你知道在小说中，这一段是第一次真正意义上的生存考验——不是杀一只虫子那么简单。',
      6, 0, 'main_ch01_paid_service'],

    ['ch1_07_broken_path', '断裂之路',
      '隧道比看起来更长、更暗、更危险。铁轨断裂，混凝土坠落，地面不时震动。黑暗中偶尔闪过红色的眼睛——那些被剧本投放到隧道中的低阶怪物。你不时听到身后传来惨叫声。但你继续前进。金代理喘着粗气跟在你身后——如果他还活着的话。前方隐约出现了站台的灯光。出口快到了。但最后一段路是最危险的——你必须穿过一片完全黑暗的区域，那里的怪物密度最高。',
      7, 0, 'main_ch01_paid_service'],

    ['ch1_08_stage_final', '站台之上',
      '你终于到达了站台。站台上的荧光灯发出惨白的光，照亮了几十个精疲力竭的幸存者——以及更多的空位。鬼怪悬在半空中，它的身后浮现出一面巨大的蓝色光幕，上面滚动着无数数字和符号。它拍了拍手：「恭喜各位活着的化身！现在是大家最喜欢的环节——结算时间。」光幕上的数字开始跳动。你能感觉到——星之流的频道正在打开。遥远的存在们开始将目光投向这里。这是你的第一次结算。你的每一个选择都将被计算，转化为你在星流中的初始位置。',
      8, 0, 'main_ch01_paid_service'],

    // ══════════════════════════════════════════════════════════════
    // Main Chapter 2: 主角与旗帜 (main_ch02_meeting_protagonist)
    // ══════════════════════════════════════════════════════════════

    ['ch2_01_station_camp', '站台营地',
      '第二个剧本的结算结束后，幸存者们开始在站台上建立临时营地。站台变成了一个小型社会——有人负责收集物资，有人负责警戒，也有人试图建立秩序。你注意到站台的不同角落已经出现了隐约的派系划分。空气中弥漫着紧张——食物和水都不够。更重要的是，下一个剧本随时可能公布。在小说中，站台营地是整个早期阶段的核心据点。你将在这里遇到许多关键人物——也会在这里做出许多决定你命运的选择。',
      9, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_02_survivor_tension', '幸存者之间的张力',
      '第二天，食物比预期的更少了。站台内的气氛急转直下。一个叫韩明武的中年男人开始召集人手，声称要"清理无用的人"以节省资源。他的声音很大，支持他的人越来越多——因为恐惧比饥饿更有效地操纵着人群。但你也看到了一些人选择站出来反对。刘尚雅——一个戴着眼镜的年轻女性——正在试图用自己的食物帮助别人。在这个时间点，你还不知道她会成为你在小说中最信任的同伴之一。但现在——你需要在混乱中决定自己的位置。',
      10, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_03_yjh_enters', '回归者降临',
      '站台的入口处传来沉重的脚步声。所有人转过头去——一个黑衣青年从隧道中走出来，他的黑色长风衣上沾满了灰尘和血迹，但身体没有一丝伤痕。他的眼神扫过整个站台，像是在审视一群蝼蚁。刘众赫。你在小说中读过他无数次轮回的故事，但亲眼见到他的冲击力完全不同。他身上散发出一种压倒性的存在感——那是经历了无数生死才能磨砺出的气场。站台上的所有人都安静了下来。刘众赫没有看任何人。他只是走到站台中央，坐了下来，闭上眼睛。仿佛这个站台上的一百多个幸存者，对他而言不过是路边的一堆石头。',
      11, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_04_knowledge_test', '知识即是力量',
      '鬼怪再次出现，宣布了第三个剧本：「旗帜争夺战」。规则很简单——每个化身会被分配一面旗帜，夺取别人的旗帜可以获得积分。积分最高者将获得通过剧本的权利。人群开始骚动。这是一个鼓励互相残杀的剧本。但你看到刘众赫睁开了眼睛。他站了起来。你知道他要做什么——在小说中，他在这个剧本中展现了他的真正实力。但更重要的是——你知道他的弱点。你知道他轮回中的每一道伤疤。问题是：你要不要用这些信息？用什么样的方式使用它们？',
      12, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_05_flag_scenario', '旗帜争夺战开始',
      '旗帜分发下来了。你手中是一面白色的旗帜——它没有颜色，意为"无阵营"。在小说中，白色旗帜既是最弱的，也是最自由的。鬼怪宣布了规则细节后消失在空气中，而站台周围的墙壁开始崩塌，露出了更大的战斗区域。刘众赫已经消失了——他在剧本开始的第一秒就开始了行动。枪声、喊叫声和怪物的嘶吼声从四面八方传来。旗帜之战正式开幕。你必须在混乱中找到自己的方向。',
      13, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_06_alliance_web', '联盟与背叛',
      '旗帜争夺战的战场不断扩大。废弃的地铁站变成了一个微型的战争世界——每个角落都有人在为旗帜而战。你看到了一些熟面孔：金代理正试图组织队伍，刘尚雅和几个年轻人组成了一支护旗小队。越来越多的势力开始浮出水面。但联盟是脆弱的——一面旗帜的归属就可能引发背叛。有人向你发出邀请，有人试图夺取你的旗帜。在这张复杂的网中，你需要决定自己的盟友和敌人。',
      14, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_07_flag_climax', '旗帜之战高潮',
      '战斗进入了白热化阶段。大部分弱者已经被淘汰，剩下的都是拥有一定实力的队伍和个体。刘众赫已经收集了超过二十面旗帜——他势不可挡。但你也注意到某个角落正在发生不寻常的事。一股不属于任何化身的力量正在战场上蔓延。鬼怪的蓝光频率越来越高，似乎在监视某个特定的变量。而那个变量——就是你。你已经走得比小说中更远了。旗帜之战的高潮不是最强者的胜利——而是最被关注者的转折。',
      15, 0, 'main_ch02_meeting_protagonist'],

    ['ch2_08_stage_final', '旗帜之下',
      '战争结束了。鬼怪再次出现在站台中央，开始清点旗帜。刘众赫不出意外地获得了最高积分，但他的眼神扫过你的时候多了一瞬的停顿。他注意到了什么——也许是你没有出手的那些行动，也许是你主动放弃的某个机会。你站在旗帜的残骸中，感受到了来自远方的目光。星之流中，有星座正在注视着你。鬼怪宣布了结算结果，光幕上跳出了每个幸存者的新等级、新资源和新状态。但你知道——这不过是暴风雨前的宁静。',
      16, 0, 'main_ch02_meeting_protagonist'],

    // ══════════════════════════════════════════════════════════════
    // Main Chapter 3: 星座的注视 (main_ch03_constellation_sponsor)
    // ══════════════════════════════════════════════════════════════

    ['ch3_01_star_stream_open', '星流开启',
      '旗帜之战的结算完成后，鬼怪做出了一个前所未有的举动。它打开了所有的频道——站台上方的黑暗中浮现出无数双眼睛的形状。有的如火焰般炽热，有的如深渊般冰冷。星座们。「各位化身们，你们的表现已经引起了上面的兴趣。」鬼怪的声音中带着一丝谄媚，「从现在开始，星之流将正式向地球开放频道。星座们可以看到你们的一举一动——如果你们足够有趣，它们会赐予你们赞助。」赞助意味着力量、资源和保护。但也意味着被束缚。你知道在小说中，接受赞助的化身最终都必须付出代价。',
      17, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_02_sponsor_offers', '赞助者的邀请',
      '星流开放后的第一个小时，你收到了三条赞助信息。第一条来自一个自称"葡萄酒之神"的星座——它提供的赞助条件是：每当你饮下血液，力量就会增加。第二条来自"黑色火焰的魔王"——它要求你在每一次战斗中至少杀死一名对手。第三条没有署名，只有一句话：「我在看着你。不需要任何条件。只是好奇。」最后这条信息让鬼怪的表情变了——但你已经隐约猜到了发信者是谁。关于那个"最古老的梦"的线索，在小说中就是从一条匿名的赞助信息开始的。',
      18, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_03_readers_path', '读者之道',
      '赞助信息越来越多。每个星座都试图用更好的条件吸引化身——硬币、技能、甚至是专属的成长路线。但你注意到一件奇怪的事：刘众赫没有接受任何赞助。即使在小说中，他也选择了独自战斗。你开始思考：为什么？赞助意味着力量的捷径，但也意味着方向的锁定。作为一个读过3149章结局的人——你知道每一个赞助条件最终都会成为星座控制化身的锁链。但拒绝赞助意味着在这条路上你将更加孤独、更加艰难、更加危险。你需要决定——走读者之道，还是接受星座之手？',
      19, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_04_companion_bonds', '同伴的羁绊',
      '在思考赞助问题的同时，你发现你的同伴们也在面临类似的选择。刘尚雅收到了来自一个医疗相关星座的邀请。李智慧被一个战争星座关注着。而刘众赫——他什么也没有做，但所有最强的星座都在试图接触他。你知道这些选择将塑造每一个同伴的未来。但更重要的是——在这个时间点上，你与他们之间的关系还很脆弱。小说中的故事线正在被你的每一个行动改写。同伴们可能会走上完全不同的路。你需要与某人建立更深的羁绊——不是为了利用他们，而是因为在这条路上，没有人可以独自走到终点。',
      20, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_05_hidden_scenario', '隐藏剧本浮现',
      '在审查赞助信息的过程中，你发现了一个不属于任何公开剧本的消息。它藏在星流频道的底层代码中，像是某种被遗忘的留言。内容只有几个字：「第一道墙在暗城之前——找到它。」在小说中，隐藏剧本是改变剧情走向的关键机制。它们不属于主线故事，但往往能提供决定性的信息或力量。这条消息来自谁？为什么会被埋藏在星流的数据深处？你知道小说中有几个角色拥有操纵星流系统的能力——但他们都不应该在这个时间点出现。这意味着你的行动已经显著改变了时间线。',
      21, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_06_theater_of_gods', '众神的剧场',
      '隐藏剧本引导你来到了一个不属于正常空间的地方——星座们称之为"剧场"。这是一个介于现实世界和星流之间的夹层空间。在这里，星座们以投影的形式出现——你可以看到它们模糊的轮廓坐在环形的观众席上。中央的舞台上有一个剧本：只有完成这个剧本，你才能获得隐藏信息的下一部分。「欢迎来到剧场，小化身。」一个慵懒的声音响起。「我们是观众。而你是表演者。如果你能让我们满意——也许我们会告诉你一些不该被知道的事。」这是一场危险的交易。但在这个世界里，信息本身就是最宝贵的资源。',
      22, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_07_sponsor_choice', '抉择时刻',
      '剧场的试炼完成后，你获得了更多的信息——但也引来了更多的关注。现在你的频道观众数量已经超过了大多数化身。星座们开始将赌注押在你的身上。这是你的抉择时刻：选择赞助，你将获得足以对抗刘众赫的力量；拒绝赞助，你将保留完全的自由但必须在更艰难的资源环境中生存。你回想起在小说中读到的那句话——"读者不是故事中最强的角色。但读者是唯一知道故事如何结束的人。"你手中的硬币在隐约发光。头顶的星辰在等待。',
      23, 0, 'main_ch03_constellation_sponsor'],

    ['ch3_08_stage_final', '契约之刻',
      '你做出了选择。在你的选择落定的瞬间，星之流的频道爆发出前所未有的热度。星座们有的在喝彩，有的在愤怒，有的陷入了沉默。鬼怪用复杂的神情看着你——它的表情中混杂着惊讶、恼怒和某种你不理解的东西。光幕降下，显示了你当前的结算数据。你的契约——或者说你不签契约的决定——成为了星之流中最热门的话题。一个新的标签出现在你的频道上：「不可预测的化身」。而这正是最吸引星座的东西。现在，你需要利用这份关注为接下来的考验做准备——因为更大的风暴正在地平线上酝酿。',
      24, 0, 'main_ch03_constellation_sponsor'],

    // ══════════════════════════════════════════════════════════════
    // Main Chapter 4: 王座战争 (main_ch04_throne_war)
    // ══════════════════════════════════════════════════════════════

    ['ch4_01_throne_prophecy', '王座预言',
      '星之流中出现了一个传言——关于"绝对王座"。据说不论是谁，只要坐上那个王座，就能获得足以改变世界走向的力量。传言传播得很快。每一个化身都在讨论它。但你比任何人都更清楚王座的真相——因为你在小说中读过这一段。绝对王座是真实的。但它不是奖赏，而是陷阱。坐上王座的人将成为所有星座的攻击目标。更糟的是——王座会腐蚀坐上它的人。刘众赫在小说中的多次轮回中都试图夺取王座，每一次的结果都不相同。但你的存在已经改变了剧情。也许这一次，结果会不同。',
      25, 0, 'main_ch04_throne_war'],

    ['ch4_02_kings_gather', '诸王集结',
      '通往绝对王座的通道在城市废墟的深处——一座半塌的摩天大楼顶端。当消息被确认后，所有有实力的化身都开始向那里移动。你看到了许多熟悉的面孔：带着队伍的刘众赫、独自一人的韩明武、以及几个你在小说中认识但还没亲眼见过的强者。每一个人都是潜在的对手。每一个人都相信自己是命运的宠儿。在你的视野边缘，星座们的注视变得更加密集。这场王座之争已经成为了整个星流频道中最热门的直播。你需要在进入王座区域之前确立自己的立场和策略。',
      26, 0, 'main_ch04_throne_war'],

    ['ch4_03_path_to_throne', '通往王座之路',
      '摩天大楼的内部比外面看起来更加险恶。每一层都被设置了不同的试炼——鬼怪们显然不打算让化身们轻松到达顶端。火焰之层、镜面之层、静默之层——每一层的规则都不同，但致命的程度是一样的。你和你的队伍——无论现在是谁在你身边——必须在这些试炼中找到生存下去的方法。同时，你也注意到其他候补者的进度。有人已经死于试炼，有人在半途中放弃。刘众赫在最快的一批中——但你没有落后太多。通往王座的道路本身就是一场淘汰赛。',
      27, 0, 'main_ch04_throne_war'],

    ['ch4_04_rival_kings', '对峙诸王',
      '摩天大楼的倒数第三层——一个巨大的圆形大厅。这里汇聚了所有成功穿过试炼的化身。大约二十个人，每一个都是在之前的剧本中活下来的强者。刘众赫站在最前方，双臂交叉。韩明武在另一边，身边围着几个追随者。还有一些独行者散落在大厅各处。空气紧绷如弦。没有人开第一个动作——因为每个人都知道，在这里犯错的代价是死亡。鬼怪出现在了空中：「很有趣！现在——我宣布一个新的临时规则。只有五个人可以继续向上。你们需要自己决定哪些人可以继续前进。方式不限。」大厅中的紧张气氛瞬间达到了顶点。',
      28, 0, 'main_ch04_throne_war'],

    ['ch4_05_throne_battle', '王座之战',
      '五个人站在了电梯前。你是其中之一。电梯缓缓上升，在摩天大楼的最高层停下。门打开——你看到它了。绝对王座。它不是黄金造的，也不是宝石装饰的。它由纯粹的星光凝聚而成，悬浮在半空中。王座上刻着无数文字——那是曾经坐过它的人留下的故事。每一个字都在发光。你感受到王座散发出的引力——不是物理的引力，而是一种对灵魂的牵引。它在邀请你。刘众赫已经在向前走。而其他三个人也在同时行动。王座之前的战斗——不是你死我活的搏斗，而是意志、知识和选择的较量——正式开始了。',
      29, 0, 'main_ch04_throne_war'],

    ['ch4_06_absolute_choice', '绝对选择',
      '战斗已经到达了最后一刻。只剩下你和刘众赫——以及王座。王座的光芒越来越亮，整个空间都被照亮了。刘众赫向前迈了一步，手触及了王座的扶手。就在这时——你看到他的身体僵住了。王座正在读取他。他的每一次轮回，每一次死亡，每一次失败和重生——都在王座的星光中被投射出来。你在小说中看到过这些画面。但亲眼目睹是另一回事——无数个世界的刘众赫在同一个空间中重叠。他咬着牙继续向前。你可以看出他在用自己的意志对抗王座的吞噬。这是你的最后机会——在小说中，这时候的读者必须做出选择。是阻止他，是帮助他，还是——取代他。',
      30, 0, 'main_ch04_throne_war'],

    ['ch4_07_throne_resolved', '王座归属',
      '王座的光芒渐渐黯淡下来。一切都尘埃落定。不管你做了什么选择——不管刘众赫是坐上了王座，还是被你阻止，还是你自己登了上去——这一刻都标志着第一卷故事的最终转折。鬼怪们收起了它们的笑容。星座们陷入了罕见的沉默。频道中数百万观众的弹幕同时停顿了一秒。因为在星之流的历史中，从来没有任何化身——不，应该说从来没有任何存在——在这个时间点上展现过这样的变数。王座之争的结果将重新定义整个地球剧本的走向。而接下来的每一章，都将不再是你读过的那本小说。',
      31, 0, 'main_ch04_throne_war'],

    ['ch4_08_volume1_end', '第一卷·终',
      '你站在摩天大楼的顶端，俯视着脚下的废墟城市。天空中是星座们闪烁的光芒——比平时更亮，更密集。鬼怪最后一次出现在你面前，用不同于以往的语调开口——没有嘲讽，没有笑声：「化身。恭喜你完成了第一阶段的剧本。」它顿了顿，像是在接收来自更高处的指示。「星之流已经将你的表现标记为——异常。但我不会告诉你那是好是坏。」它消失了，留下你和你的选择。第一卷结束了。但故事并没有结束。它只是刚刚开始。在远方——比暗城更远的地方——有什么东西正在醒来。',
      32, 0, 'main_ch04_throne_war'],

    // ══════════════════════════════════════════════════════════════
    // 第一卷结局
    // ══════════════════════════════════════════════════════════════

    ['ch4_ending_first_step', '第一步的终末',
      '第一卷就此落幕。在付費服务开始的第一个剧本中，你只是一个普通的读者，在地铁的灯光下翻到了小说的最后一页。而现在——你站在摩天大楼的顶端，看着星座们在星之流中注视你的光芒。你积累的每一个选择，每一次犹豫，每一次坚持，都在这一刻凝结成了独属于你的故事。刘众赫站在不远处。不管你们是敌是友——你们终于站在了同一个高度。星之流中传来低沉的钟声。那是第一卷结束的信号。也是第二卷——新篇章即将开始的宣告。你深吸一口气。第一卷的故事已经结束。但你的故事还没有。',
      33, 1, 'main_ch04_throne_war'],
  ];

  for (const ch of chapters) {
    insertChapter.run(...ch);
  }
}

function seedChoices(db) {
  const insertChoice = db.prepare(`
    INSERT OR IGNORE INTO choices (choice_key, chapter_key, text, warning, next_chapter_key,
      required_flags_json, blocked_flags_json, required_titles_json, blocked_titles_json,
      effects_json, is_irreversible, choice_type, decision_group, is_repeatable, completes_stage)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const choices = [
    // ══════════════════════════════════════════════════════════════
    // ch1_01_last_train: 最后一班地铁
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_01_check_phone', 'ch1_01_last_train', '【调查】再次打开手机，仔细查看小说的最终话', null, 'ch1_01_last_train',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_checked_phone":true},"stats":{"insight":1},"coins":3}', 0,
      'action', null, 1, 0],
    ['choice_ch1_01_observe_crowd', 'ch1_01_last_train', '【调查】观察周围乘客的反应', null, 'ch1_01_last_train',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_observed_crowd":true},"stats":{"insight":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch1_01_move_forward', 'ch1_01_last_train', '【剧情】走向车厢前方查看震动的来源', null, 'ch1_02_dokkaebi_appears',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_moved_forward":true},"stats":{"intelligence":1},"coins":5}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_02_dokkaebi_appears: 鬼怪降临
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_02_study_dokkaebi', 'ch1_02_dokkaebi_appears', '【调查】仔细观察鬼怪的举止和蓝光的规律', null, 'ch1_02_dokkaebi_appears',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_studied_dokkaebi":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch1_02_take_position', 'ch1_02_dokkaebi_appears', '【行动】抢占车厢角落的有利位置', null, 'ch1_02_dokkaebi_appears',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_took_position":true},"stats":{"combat":1},"coins":3}', 0,
      'action', null, 1, 0],
    ['choice_ch1_02_calm_passengers', 'ch1_02_dokkaebi_appears', '【剧情】安抚恐慌的乘客，让他们冷静下来听规则', null, 'ch1_03_first_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_calmed_passengers":true},"stats":{"bond":2,"leadership":1}}', 0,
      'progress', null, 0, 0],
    ['choice_ch1_02_stay_silent', 'ch1_02_dokkaebi_appears', '【分歧】保持沉默——不出手不引人注意，暗中观察一切',
      '你将选择独自面对这条路。在所有人都惊慌失措时保持冷静也许是最安全的选择——但也意味着你放弃了影响他人的机会。',
      'ch1_03_first_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_stayed_silent":true},"stats":{"insight":2,"bond":-1},"permanent_flags":{"独自路线":1}}', 1,
      'decision', 'ch1_approach', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_03_first_scenario: 第一个剧本：杀生
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_03_use_environment', 'ch1_03_first_scenario', '【行动】利用车厢内的金属管和玻璃碎片武装自己', null, 'ch1_03_first_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_used_environment":true},"stats":{"intelligence":1},"coins":5}', 0,
      'action', null, 1, 0],
    ['choice_ch1_03_deal_insect', 'ch1_03_first_scenario', '【剧情】找到从通风口出现的昆虫并解决它', null, 'ch1_04_insect_kill',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_dealt_insect":true},"stats":{"combat":2},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch1_03_help_agent_kim', 'ch1_03_first_scenario', '【分歧】在昆虫攻击前警告金代理并帮助他',
      '你的选择将定义你在第一个剧本中的角色。帮助金代理会加强你的人际羁绊，但也可能让你来不及处理昆虫。',
      'ch1_04_insect_kill',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_helped_kim":true},"stats":{"bond":3},"relationships":{"金代理":15}}', 1,
      'decision', 'ch1_help', 0, 0],
    ['choice_ch1_03_save_self', 'ch1_03_first_scenario', '【分歧】不管旁人——你只需要保护自己活下去',
      '冷酷，但有效。在这个世界里，独自行动的人往往活得更久——至少在开始阶段。',
      'ch1_04_insect_kill',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_saved_self":true},"stats":{"combat":2,"cruelty":1},"coins":15}', 1,
      'decision', 'ch1_help', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_04_insect_kill: 昆虫之死
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_04_examine_insect', 'ch1_04_insect_kill', '【调查】检查昆虫尸体——小说中提到过剧本怪物会掉落信息', null, 'ch1_04_insect_kill',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_examined_insect":true},"stats":{"insight":1},"story_fragments":1}', 0,
      'action', null, 1, 0],
    ['choice_ch1_04_gather_supplies', 'ch1_04_insect_kill', '【行动】趁其他人还在恐慌，收集车厢内的可用物资', null, 'ch1_04_insect_kill',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_gathered_supplies":true},"coins":8}', 0,
      'action', null, 1, 0],
    ['choice_ch1_04_face_survivors', 'ch1_04_insect_kill', '【剧情】面对其他幸存者的注视——你刚杀了那只昆虫，他们现在都在看你', null, 'ch1_05_aftermath',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_faced_survivors":true},"stats":{"leadership":1,"bond":1},"coins":5}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_05_aftermath: 第一波冲击之后
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_05_observe_reactions', 'ch1_05_aftermath', '【调查】观察不同幸存者的反应——谁崩溃了，谁冷静了，谁在算计', null, 'ch1_05_aftermath',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_observed_reactions":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch1_05_count_resources', 'ch1_05_aftermath', '【行动】统计车厢内可用的所有资源——水、食物、尖锐物', null, 'ch1_05_aftermath',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_counted_resources":true},"coins":5,"story_fragments":1}', 0,
      'action', null, 1, 0],
    ['choice_ch1_05_prepare_next', 'ch1_05_aftermath', '【剧情】为接下来的剧本做准备——你知道这不会是终点', null, 'ch1_06_second_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_prepared_next":true},"stats":{"combat":1},"coins":5}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_06_second_scenario: 第二剧本：穿越隧道
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_06_study_rules', 'ch1_06_second_scenario', '【调查】仔细分析第二剧本的规则措辞——鬼怪的规则总有漏洞', null, 'ch1_06_second_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_studied_rules":true},"stats":{"intelligence":1,"insight":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch1_06_observe_tunnel', 'ch1_06_second_scenario', '【调查】观察隧道入口的环境和怪物踪迹', null, 'ch1_06_second_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_observed_tunnel":true},"stats":{"insight":1},"coins":3}', 0,
      'action', null, 1, 0],
    ['choice_ch1_06_enter_tunnel', 'ch1_06_second_scenario', '【剧情】率先进入隧道——在别人还在犹豫时先走一步', null, 'ch1_07_broken_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_entered_tunnel_first":true},"stats":{"combat":1,"leadership":1},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch1_06_form_group', 'ch1_06_second_scenario', '【分歧】号召幸存者一起穿越——人多力量大但也更显眼',
      '集体行动会让你们更容易被怪物发现，但也能互相掩护。',
      'ch1_07_broken_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_formed_group":true},"stats":{"leadership":2,"bond":2}}', 1,
      'decision', 'ch1_tunnel', 0, 0],
    ['choice_ch1_06_go_alone', 'ch1_06_second_scenario', '【分歧】独自一人穿越——你的速度和隐秘性就是最好的武器',
      '一个人也许更危险，但也更容易悄无声息地通过。',
      'ch1_07_broken_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_went_alone":true},"stats":{"combat":2,"insight":1},"coins":10}', 1,
      'decision', 'ch1_tunnel', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_07_broken_path: 断裂之路
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_07_search_remains', 'ch1_07_broken_path', '【行动】搜索隧道中被遗弃的物资——之前有人死在了这里', null, 'ch1_07_broken_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_searched_remains":true},"coins":12,"story_fragments":2}', 0,
      'action', null, 1, 0],
    ['choice_ch1_07_help_struggler', 'ch1_07_broken_path', '【行动】帮助隧道中受困的其他幸存者', null, 'ch1_07_broken_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_helped_struggler":true},"stats":{"bond":2},"relationships":{"刘尚雅":5}}', 0,
      'action', null, 1, 0],
    ['choice_ch1_07_reach_platform', 'ch1_07_broken_path', '【剧情】冲向站台的灯光——不管身后有什么，先到达安全区域', null, 'ch1_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_reached_platform":true},"stats":{"combat":1},"coins":15}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch1_08_stage_final: 站台之上
    // ══════════════════════════════════════════════════════════════
    ['choice_ch1_08_settle_first', 'ch1_08_stage_final', '【阶段最终】结算第一个剧本——将你在第一个剧本中的行动转化为实力', null, 'ch1_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_settled_first":true,"first_scenario_cleared":true},"stats":{"insight":1},"story_fragments":5,"coins":20}', 0,
      'stage_final', null, 0, 1],
    ['choice_ch1_08_search_platform', 'ch1_08_stage_final', '【行动】在站台上搜索有用的资源', null, 'ch1_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_searched_platform":true},"coins":8,"story_fragments":1}', 0,
      'action', null, 1, 0],
    ['choice_ch1_08_talk_survivors', 'ch1_08_stage_final', '【行动】与站台上的其他幸存者交谈', null, 'ch1_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch1_talked_survivors":true},"stats":{"bond":1,"leadership":1}}', 0,
      'action', null, 1, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_01_station_camp: 站台营地
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_01_explore_camp', 'ch2_01_station_camp', '【调查】探索站台营地的各个角落', null, 'ch2_01_station_camp',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_explored_camp":true},"coins":8,"story_fragments":2}', 0,
      'action', null, 1, 0],
    ['choice_ch2_01_talk_people', 'ch2_01_station_camp', '【行动】与营地中的幸存者建立联系', null, 'ch2_01_station_camp',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_talked_people":true},"stats":{"bond":2,"leadership":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_01_join_meeting', 'ch2_01_station_camp', '【剧情】参与营地的第一次组织会议', null, 'ch2_02_survivor_tension',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_joined_meeting":true},"stats":{"leadership":1},"coins":5}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_02_survivor_tension: 幸存者之间的张力
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_02_observe_factions', 'ch2_02_survivor_tension', '【调查】观察营地的派系分布——谁在拉拢谁', null, 'ch2_02_survivor_tension',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_observed_factions":true},"stats":{"insight":2,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_02_scavenge_food', 'ch2_02_survivor_tension', '【行动】在附近区域搜集食物和水', null, 'ch2_02_survivor_tension',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_scavenged_food":true},"coins":10}', 0,
      'action', null, 1, 0],
    ['choice_ch2_02_defuse_conflict', 'ch2_02_survivor_tension', '【剧情】化解韩明武支持者与刘尚雅之间的对峙', null, 'ch2_03_yjh_enters',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_defused_conflict":true},"stats":{"leadership":2,"bond":2},"relationships":{"刘尚雅":10}}', 0,
      'progress', null, 0, 0],
    ['choice_ch2_02_side_strong', 'ch2_02_survivor_tension', '【分歧】支持韩明武的强者逻辑——世界已经变了，弱者没有资格消耗资源',
      '这会让强权派对你产生好感，但你会失去弱者阵营的信任。',
      'ch2_03_yjh_enters',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_sided_strong":true},"stats":{"leadership":1,"cruelty":1},"permanent_flags":{"强权路线":1}}', 1,
      'decision', 'ch2_faction', 0, 0],
    ['choice_ch2_02_side_weak', 'ch2_02_survivor_tension', '【分歧】支持刘尚雅保护弱者——秩序应该建立在互相帮助的基础上',
      '你站在了弱者一边。他们会感激你，但在接下来的剧本中，你需要为这个选择付出更多努力。',
      'ch2_03_yjh_enters',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_sided_weak":true},"stats":{"bond":3,"leadership":1},"relationships":{"刘尚雅":20}}', 1,
      'decision', 'ch2_faction', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_03_yjh_enters: 回归者降临
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_03_watch_yjh', 'ch2_03_yjh_enters', '【调查】观察刘众赫的动作细节——他的习惯、装备、以及状态', null, 'ch2_03_yjh_enters',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_watched_yjh":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_03_trace_yjh', 'ch2_03_yjh_enters', '【调查】追踪刘众赫进入站台前在隧道中留下的痕迹', null, 'ch2_03_yjh_enters',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_traced_yjh":true},"stats":{"intelligence":1},"coins":5}', 0,
      'action', null, 1, 0],
    ['choice_ch2_03_approach_yjh', 'ch2_03_yjh_enters', '【剧情】在其他人都因恐惧而退缩时，上前与刘众赫对话', null, 'ch2_04_knowledge_test',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_approached_yjh":true},"stats":{"combat":1,"insight":1},"relationships":{"刘众赫":5}}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_04_knowledge_test: 知识即是力量
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_04_examine_gear', 'ch2_04_knowledge_test', '【调查】观察刘众赫的装备——你知道他每次轮回初始装备的区别', null, 'ch2_04_knowledge_test',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_examined_gear":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_04_analyze_situation', 'ch2_04_knowledge_test', '【行动】分析当前所有已知信息，制定与刘众赫对话的策略', null, 'ch2_04_knowledge_test',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_analyzed_situation":true},"stats":{"insight":1},"coins":5}', 0,
      'action', null, 1, 0],
    ['choice_ch2_04_prove_knowledge', 'ch2_04_knowledge_test', '【剧情】用你对剧本的了解来证明自己的价值——让他知道你不是普通的幸存者', null, 'ch2_05_flag_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_proved_knowledge":true},"stats":{"insight":2},"relationships":{"刘众赫":15}}', 0,
      'progress', null, 0, 0],
    ['choice_ch2_04_reveal_info', 'ch2_04_knowledge_test', '【分歧】直接告诉刘众赫你知道他的过去——包括轮回的事',
      '这将永远改变你和刘众赫的关系。他会视你为威胁还是盟友？',
      'ch2_05_flag_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_revealed_info":true},"stats":{"insight":2},"relationships":{"刘众赫":25}}', 1,
      'decision', 'ch2_reveal', 0, 0],
    ['choice_ch2_04_keep_secret', 'ch2_04_knowledge_test', '【分歧】保守秘密——你的知识是最强的武器，过早暴露没有好处',
      '保持谨慎也许是对的。刘众赫从不轻易信任任何人——而且在他的多次轮回中，背叛是他学到的最深刻的教训。',
      'ch2_05_flag_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_kept_secret":true},"stats":{"insight":2,"intelligence":1},"coins":10}', 1,
      'decision', 'ch2_reveal', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_05_flag_scenario: 旗帜争夺战开始
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_05_study_flags', 'ch2_05_flag_scenario', '【调查】研究旗帜的颜色和可能代表的含义', null, 'ch2_05_flag_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_studied_flags":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_05_scout_teams', 'ch2_05_flag_scenario', '【行动】侦查其他队伍的动向和实力', null, 'ch2_05_flag_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_scouted_teams":true},"stats":{"insight":1},"coins":5}', 0,
      'action', null, 1, 0],
    ['choice_ch2_05_form_strategy', 'ch2_05_flag_scenario', '【剧情】制定你的旗帜战略——夺取、防守还是交易？', null, 'ch2_06_alliance_web',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_formed_strategy":true},"stats":{"intelligence":1,"leadership":1},"coins":8}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_06_alliance_web: 联盟与背叛
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_06_gather_intel', 'ch2_06_alliance_web', '【调查】收集各方势力的情报——谁的实力最强，谁的旗帜最多', null, 'ch2_06_alliance_web',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_gathered_intel":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_06_reinforce_position', 'ch2_06_alliance_web', '【行动】加固你的阵地，设置简单的防御', null, 'ch2_06_alliance_web',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_reinforced_position":true},"stats":{"combat":1},"coins":8}', 0,
      'action', null, 1, 0],
    ['choice_ch2_06_build_alliance', 'ch2_06_alliance_web', '【剧情】建立关键的联盟关系——决定在这场旗帜之争中谁是你的盟友', null, 'ch2_07_flag_climax',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_built_alliance":true},"stats":{"leadership":2,"bond":2}}', 0,
      'progress', null, 0, 0],
    ['choice_ch2_06_ally_kim', 'ch2_06_alliance_web', '【分歧】与金代理的队伍结盟——他们人数多但战斗力一般',
      '金代理的队伍人多势众，也许不够强，但足够可靠。',
      'ch2_07_flag_climax',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_allied_kim":true},"stats":{"bond":3},"relationships":{"金代理":20}}', 1,
      'decision', 'ch2_alliance', 0, 0],
    ['choice_ch2_06_ally_independent', 'ch2_06_alliance_web', '【分歧】与几个独立行动的化身联手——他们每个人都有自己的独特能力',
      '独立化身更加灵活，但忠诚度也更难保证。',
      'ch2_07_flag_climax',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_allied_independent":true},"stats":{"insight":2},"coins":15}', 1,
      'decision', 'ch2_alliance', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_07_flag_climax: 旗帜之战高潮
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_07_support_ally', 'ch2_07_flag_climax', '【行动】在关键时刻支援你的盟友', null, 'ch2_07_flag_climax',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_supported_ally":true},"stats":{"bond":2,"combat":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch2_07_ambush_enemy', 'ch2_07_flag_climax', '【行动】伏击一支正在接近的敌方队伍', null, 'ch2_07_flag_climax',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_ambushed_enemy":true},"stats":{"combat":2,"cruelty":1},"coins":20}', 0,
      'action', null, 1, 0],
    ['choice_ch2_07_final_clash', 'ch2_07_flag_climax', '【剧情】冲向旗帜争夺的最终战场——不分出胜负，一切就不会结束', null, 'ch2_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_final_clash":true},"stats":{"combat":2,"leadership":1},"coins":25}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch2_08_stage_final: 旗帜之下
    // ══════════════════════════════════════════════════════════════
    ['choice_ch2_08_settle_flags', 'ch2_08_stage_final', '【阶段最终】结算旗帜之战——将你在这场战争中的表现转化为成长', null, 'ch2_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_settled_flags":true},"stats":{"insight":2},"story_fragments":10,"coins":30}', 0,
      'stage_final', null, 0, 1],
    ['choice_ch2_08_loot_battlefield', 'ch2_08_stage_final', '【行动】收集战后散落的物资——战败者已经不需要它们了', null, 'ch2_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_looted_battlefield":true},"coins":15,"story_fragments":3}', 0,
      'action', null, 1, 0],
    ['choice_ch2_08_tend_wounded', 'ch2_08_stage_final', '【行动】帮助战场上的伤员——包括你的敌人', null, 'ch2_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch2_tended_wounded":true},"stats":{"bond":3,"leadership":1}}', 0,
      'action', null, 1, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_01_star_stream_open: 星流开启
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_01_feel_constellations', 'ch3_01_star_stream_open', '【调查】静下心来感受星座的目光——试着辨别它们的数量和性质', null, 'ch3_01_star_stream_open',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_felt_constellations":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_01_study_channel', 'ch3_01_star_stream_open', '【调查】研究星流频道的工作机制——了解观众系统', null, 'ch3_01_star_stream_open',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_studied_channel":true},"stats":{"intelligence":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_01_answer_stream', 'ch3_01_star_stream_open', '【剧情】回应星流的召唤——让星座们知道你的存在', null, 'ch3_02_sponsor_offers',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_answered_stream":true},"stats":{"leadership":1},"coins":10}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_02_sponsor_offers: 赞助者的邀请
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_02_research_constellations', 'ch3_02_sponsor_offers', '【调查】调查向你发出邀请的各个星座——它们在小说中的背景和真实目的', null, 'ch3_02_sponsor_offers',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_researched_constellations":true},"stats":{"insight":2,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_02_compare_offers', 'ch3_02_sponsor_offers', '【行动】仔细比较各个赞助条件的优劣', null, 'ch3_02_sponsor_offers',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_compared_offers":true},"stats":{"intelligence":1},"coins":5}', 0,
      'action', null, 1, 0],
    ['choice_ch3_02_face_first_offer', 'ch3_02_sponsor_offers', '【剧情】正面面对第一个正式的赞助邀请', null, 'ch3_03_readers_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_faced_offer":true},"stats":{"insight":1},"coins":8}', 0,
      'progress', null, 0, 0],
    ['choice_ch3_02_accept_mild', 'ch3_02_sponsor_offers', '【分歧】接受一个温和星座的赞助——它的条件不算严苛',
      '一个温和的赞助可以给你力量而不至于被完全控制。但即使是温和的星座，也有自己的目的。',
      'ch3_03_readers_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_accepted_mild":true},"sponsors_add":["温和的观察者"],"coins":30,"stats":{"insight":1}}', 1,
      'decision', 'ch3_first_sponsor', 0, 0],
    ['choice_ch3_02_decline_all', 'ch3_02_sponsor_offers', '【分歧】礼貌地婉拒所有赞助邀请——至少现在不要被任何星座束缚',
      '拒绝需要勇气——因为你正在拒绝力量本身。但这也许就是读者之道的第一步。',
      'ch3_03_readers_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_declined_all":true},"stats":{"insight":2},"permanent_flags":{"赞助拒绝者":1}}', 1,
      'decision', 'ch3_first_sponsor', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_03_readers_path: 读者之道
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_03_study_independent', 'ch3_03_readers_path', '【调查】研究不依赖赞助的生存方式——小说中有先例', null, 'ch3_03_readers_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_studied_independent":true},"stats":{"intelligence":2,"insight":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_03_seek_advice', 'ch3_03_readers_path', '【行动】向其他人征求意见——刘众赫拒绝赞助的原因可能会给你启发', null, 'ch3_03_readers_path',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_sought_advice":true},"stats":{"bond":1},"relationships":{"刘众赫":5}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_03_confirm_path', 'ch3_03_readers_path', '【剧情】确认自己的道路——不管选什么，最重要的是坚定不移', null, 'ch3_04_companion_bonds',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_confirmed_path":true},"stats":{"insight":1,"leadership":1},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch3_03_readers_way', 'ch3_03_readers_path', '【分歧】走读者之路——绝不依赖任何星座的赞助，依靠知识和判断力活下去',
      '这是一条最艰难的路。但也只有这条路能让你保持完全的自由。',
      'ch3_04_companion_bonds',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_readers_way":true},"stats":{"insight":3},"permanent_flags":{"读者之道":true}}', 1,
      'decision', 'ch3_path', 0, 0],
    ['choice_ch3_03_keep_open', 'ch3_03_readers_path', '【分歧】保持开放态度——不马上拒绝所有可能，但也不轻易被收买',
      '在不确定的世界里，保留选择权本身也是一种智慧。',
      'ch3_04_companion_bonds',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_kept_open":true},"stats":{"intelligence":2},"coins":15}', 1,
      'decision', 'ch3_path', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_04_companion_bonds: 同伴的羁绊
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_04_talk_sangah', 'ch3_04_companion_bonds', '【行动】与刘尚雅深入交谈——了解她的过去和她的决心', null, 'ch3_04_companion_bonds',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_talked_sangah":true},"stats":{"bond":2},"relationships":{"刘尚雅":15}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_04_train_jihye', 'ch3_04_companion_bonds', '【行动】帮助李智慧进行战斗训练——她有天分但缺乏经验', null, 'ch3_04_companion_bonds',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_trained_jihye":true},"stats":{"combat":1,"bond":1},"relationships":{"李智慧":15}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_04_deepen_bond', 'ch3_04_companion_bonds', '【剧情】加深与一位同伴的羁绊——在这条路上，可靠的同伴比任何赞助都珍贵', null, 'ch3_05_hidden_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_deepened_bond":true},"stats":{"bond":2,"leadership":1},"coins":10}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_05_hidden_scenario: 隐藏剧本浮现
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_05_investigate_clue', 'ch3_05_hidden_scenario', '【调查】深入调查隐藏剧本的线索——这条信息是谁留下的？', null, 'ch3_05_hidden_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_investigated_clue":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_05_record_anomaly', 'ch3_05_hidden_scenario', '【行动】记录星流频道中的异常现象——这些数据以后可能会派上用场', null, 'ch3_05_hidden_scenario',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_recorded_anomaly":true},"stats":{"intelligence":2}, "story_fragments":3}', 0,
      'action', null, 1, 0],
    ['choice_ch3_05_trace_source', 'ch3_05_hidden_scenario', '【剧情】追踪隐藏剧本的源头——不管是什么，你必须找到答案', null, 'ch3_06_theater_of_gods',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_traced_source":true},"stats":{"insight":1},"coins":15}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_06_theater_of_gods: 众神的剧场
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_06_observe_theater', 'ch3_06_theater_of_gods', '【调查】观察剧场的构造和规则——每个剧场都有隐含的通过条件', null, 'ch3_06_theater_of_gods',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_observed_theater":true},"stats":{"insight":2,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_06_search_passage', 'ch3_06_theater_of_gods', '【行动】寻找剧场的秘密通道——星座们总是喜欢藏东西', null, 'ch3_06_theater_of_gods',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_searched_passage":true},"stats":{"insight":1},"coins":10}', 0,
      'action', null, 1, 0],
    ['choice_ch3_06_complete_trial', 'ch3_06_theater_of_gods', '【剧情】完成剧场设置的核心试炼——用你的方式打动星座', null, 'ch3_07_sponsor_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_completed_trial":true},"stats":{"insight":2,"leadership":1},"coins":20,"story_fragments":5}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_07_sponsor_choice: 抉择时刻
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_07_reevaluate', 'ch3_07_sponsor_choice', '【调查】重新评估所有赞助条件——经过剧场的经历后，你的视角已经不同了', null, 'ch3_07_sponsor_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_reevaluated":true},"stats":{"insight":1,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_07_discuss_team', 'ch3_07_sponsor_choice', '【行动】与队友深入讨论——你的选择会影响所有人', null, 'ch3_07_sponsor_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_discussed_team":true},"stats":{"bond":2,"leadership":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_07_decide_sponsor', 'ch3_07_sponsor_choice', '【剧情】做出最终决定——不管选择什么，这都将定义你在星流中的身份', null, 'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_decided_sponsor":true},"stats":{"insight":1},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch3_07_accept_strong', 'ch3_07_sponsor_choice', '【分歧】接受一位强大星座的赞助——获得力量但背负契约',
      '力量是有代价的。但在这个世界里，没有力量的人连选择的资格都没有。',
      'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_accepted_strong":true},"sponsors_add":["黑色火焰的魔王"],"coins":80,"stats":{"combat":3}}', 1,
      'decision', 'ch3_final_sponsor', 0, 0],
    ['choice_ch3_07_refuse_all_final', 'ch3_07_sponsor_choice', '【分歧】坚定拒绝所有赞助——靠自己的力量走到底',
      '没有星座的枷锁。没有必须遵守的契约。纯粹的自主——也是最艰难的路。',
      'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_refused_all":true},"stats":{"insight":3},"permanent_flags":{"无赞助者":true},"coins":20}', 1,
      'decision', 'ch3_final_sponsor', 0, 0],
    ['choice_ch3_07_negotiate_deal', 'ch3_07_sponsor_choice', '【分歧】与星座谈判——用你掌握的信息换取独一无二的契约条件',
      '你不是乞求赞助的化身。你是在与星座做交易。你知道的信息比它们认为的更多——这就是你的筹码。',
      'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_negotiated_deal":true},"sponsors_add":["黑暗中的观察者"],"coins":50,"stats":{"intelligence":2,"insight":1}}', 1,
      'decision', 'ch3_final_sponsor', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch3_08_stage_final: 契约之刻
    // ══════════════════════════════════════════════════════════════
    ['choice_ch3_08_settle_contract', 'ch3_08_stage_final', '【阶段最终】结算契约阶段——将你的选择转化为可见的力量和地位', null, 'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_settled_contract":true},"stats":{"insight":2},"story_fragments":15,"coins":40}', 0,
      'stage_final', null, 0, 1],
    ['choice_ch3_08_review_terms', 'ch3_08_stage_final', '【行动】仔细审查契约条款——即使签了，也要知道每一条的含义', null, 'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_reviewed_terms":true},"stats":{"intelligence":1,"insight":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch3_08_prepare_resources', 'ch3_08_stage_final', '【行动】整理资源为下一阶段做准备——你不知道接下来会面对什么', null, 'ch3_08_stage_final',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch3_prepared_resources":true},"coins":15,"story_fragments":3}', 0,
      'action', null, 1, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_01_throne_prophecy: 王座预言
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_01_collect_legends', 'ch4_01_throne_prophecy', '【调查】收集关于绝对王座的所有传说和情报', null, 'ch4_01_throne_prophecy',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_collected_legends":true},"stats":{"intelligence":2,"insight":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_01_analyze_prophecy', 'ch4_01_throne_prophecy', '【调查】逐字分析王座预言的文本——预言从不说谎，但总是被误解', null, 'ch4_01_throne_prophecy',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_analyzed_prophecy":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_01_accept_destiny', 'ch4_01_throne_prophecy', '【剧情】接受王座争夺的命运——不管你愿不愿意，你已经卷入了这场游戏', null, 'ch4_02_kings_gather',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_accepted_destiny":true},"stats":{"leadership":1},"coins":15}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_02_kings_gather: 诸王集结
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_02_observe_kings', 'ch4_02_kings_gather', '【调查】观察其他王座候补——他们的能力、性格和意图', null, 'ch4_02_kings_gather',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_observed_kings":true},"stats":{"insight":2,"intelligence":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_02_gather_intel', 'ch4_02_kings_gather', '【行动】收集各方势力的详细情报——知己知彼', null, 'ch4_02_kings_gather',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_gathered_intel":true},"stats":{"insight":1},"coins":10}', 0,
      'action', null, 1, 0],
    ['choice_ch4_02_claim_candidacy', 'ch4_02_kings_gather', '【剧情】确立自己的王座候补身份——让所有人知道你不会只是旁观', null, 'ch4_03_path_to_throne',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_claimed_candidacy":true},"stats":{"leadership":2},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch4_02_public_claim', 'ch4_02_kings_gather', '【分歧】公开宣称自己的王座候补身份——威慑对手，吸引盟友',
      '公开身份让你成为焦点——所有星座和化身都会把目光投向你。这是风险，也是力量。',
      'ch4_03_path_to_throne',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_public_claim":true},"stats":{"leadership":3},"permanent_flags":{"王座候补":true}}', 1,
      'decision', 'ch4_king_claim', 0, 0],
    ['choice_ch4_02_hidden_claim', 'ch4_02_kings_gather', '【分歧】暗中谋划，不公开身份——在暗处观察和行动更为安全',
      '隐藏身份让你可以在不被针对的情况下积蓄力量。最后的时刻再出手——这是更谨慎的策略。',
      'ch4_03_path_to_throne',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_hidden_claim":true},"stats":{"insight":2},"coins":15}', 1,
      'decision', 'ch4_king_claim', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_03_path_to_throne: 通往王座之路
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_03_challenge_trial', 'ch4_03_path_to_throne', '【行动】主动挑战路径上的试炼——每一次胜利都会增加你的资格', null, 'ch4_03_path_to_throne',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_challenged_trial":true},"stats":{"combat":2},"coins":20}', 0,
      'action', null, 1, 0],
    ['choice_ch4_03_collect_rare', 'ch4_03_path_to_throne', '【行动】在通往王座的途中收集稀有资源——这里的机会不会再有第二次', null, 'ch4_03_path_to_throne',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_collected_rare":true},"coins":25,"story_fragments":5}', 0,
      'action', null, 1, 0],
    ['choice_ch4_03_break_barrier', 'ch4_03_path_to_throne', '【剧情】突破最后一道屏障——只有最坚定的人才能继续前进', null, 'ch4_04_rival_kings',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_broke_barrier":true},"stats":{"combat":2,"insight":1},"coins":20}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_04_rival_kings: 对峙诸王
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_04_test_rivals', 'ch4_04_rival_kings', '【行动】试探对手的实力——不动真格但收集关键的战斗力信息', null, 'ch4_04_rival_kings',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_tested_rivals":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_04_find_weakness', 'ch4_04_rival_kings', '【调查】寻找对手的弱点——每个人都有一个致命的缺陷', null, 'ch4_04_rival_kings',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_found_weakness":true},"stats":{"insight":1,"intelligence":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_04_face_rival_headon', 'ch4_04_rival_kings', '【剧情】与最强的对手正面交锋——没有退路，没有借口', null, 'ch4_05_throne_battle',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_faced_headon":true},"stats":{"combat":2,"leadership":1},"coins":30}', 0,
      'progress', null, 0, 0],
    ['choice_ch4_04_propose_alliance', 'ch4_04_rival_kings', '【分歧】向对手提出临时同盟——先清除其他竞争者再决一胜负',
      '在这个阶段，多一个盟友比多一个敌人更有意义。但同盟随时可能破裂。',
      'ch4_05_throne_battle',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_proposed_alliance":true},"stats":{"leadership":2,"bond":1}}', 1,
      'decision', 'ch4_rival', 0, 0],
    ['choice_ch4_04_direct_challenge', 'ch4_04_rival_kings', '【分歧】直接挑战最强的对手——你不打算用政治手段拖延',
      '正面挑战是最危险的选择。但也是最快的方法——如果你赢了。',
      'ch4_05_throne_battle',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_direct_challenge":true},"stats":{"combat":3},"coins":20}', 1,
      'decision', 'ch4_rival', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_05_throne_battle: 王座之战
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_05_use_environment', 'ch4_05_throne_battle', '【行动】利用大厅的环境优势——星光本身可以被引导', null, 'ch4_05_throne_battle',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_used_environment":true},"stats":{"intelligence":1,"combat":1}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_05_launch_strike', 'ch4_05_throne_battle', '【行动】发动决定性的一击——机会稍纵即逝', null, 'ch4_05_throne_battle',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_launched_strike":true},"stats":{"combat":2},"coins":15}', 0,
      'action', null, 1, 0],
    ['choice_ch4_05_final_battle', 'ch4_05_throne_battle', '【剧情】投入最终决战——不是你死就是我活。王座就在眼前。', null, 'ch4_06_absolute_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_final_battle":true},"stats":{"combat":2,"insight":1},"coins":30}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_06_absolute_choice: 绝对选择
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_06_reevaluate_cost', 'ch4_06_absolute_choice', '【调查】重新评估王座的代价——你可以感受到它散发出来的引力在扭曲你的意志', null, 'ch4_06_absolute_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_reevaluated_cost":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_06_confer_yjh', 'ch4_06_absolute_choice', '【行动】在这个关键时刻与刘众赫商议——你们的目标和看法也许不再一致', null, 'ch4_06_absolute_choice',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_conferred_yjh":true},"stats":{"bond":1},"relationships":{"刘众赫":10}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_06_final_decision', 'ch4_06_absolute_choice', '【剧情】做出关于王座的最终决定——这个选择将决定第一卷的终点', null, 'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_final_decision":true},"stats":{"insight":1},"coins":10}', 0,
      'progress', null, 0, 0],
    ['choice_ch4_06_stop_yjh', 'ch4_06_absolute_choice', '【分歧】阻止刘众赫坐上王座——你知道那会毁了他',
      '在小说中，坐上王座的刘众赫失去了一切——包括他自己。你要阻止第三次轮回重蹈覆辙。',
      'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_stopped_yjh":true},"stats":{"bond":3,"insight":2},"relationships":{"刘众赫":25}}', 1,
      'decision', 'ch4_throne_decision', 0, 0],
    ['choice_ch4_06_support_yjh', 'ch4_06_absolute_choice', '【分歧】支持刘众赫坐上王座——也许这次与小说中不同，也许他需要这份力量',
      '你已经改变了太多剧情。也许这一次，刘众赫可以承受王座的代价。你选择相信他。',
      'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_supported_yjh":true},"stats":{"leadership":1,"bond":2},"relationships":{"刘众赫":20}}', 1,
      'decision', 'ch4_throne_decision', 0, 0],
    ['choice_ch4_06_take_throne', 'ch4_06_absolute_choice', '【分歧】自己坐上王座——成为那个承受所有星座目光的人',
      '你知道代价。你知道坐上王座的人会成为众矢之的。但也许——也许一个读者比一个角色更懂得如何使用这份力量。这是不可逆的。',
      'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_took_throne":true},"stats":{"leadership":5,"cruelty":1},"permanent_flags":{"绝对王座拥有者":true},"coins":200}', 1,
      'decision', 'ch4_throne_decision', 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_07_throne_resolved: 王座归属
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_07_witness_result', 'ch4_07_throne_resolved', '【行动】见证王座归属的最终结果', null, 'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_witnessed_result":true},"stats":{"insight":2}}', 0,
      'action', null, 1, 0],
    ['choice_ch4_07_clean_battlefield', 'ch4_07_throne_resolved', '【行动】收拾战场的残局——王座之争的遗迹中蕴含着珍贵的资源', null, 'ch4_07_throne_resolved',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_cleaned_battlefield":true},"coins":25,"story_fragments":5}', 0,
      'action', null, 1, 0],
    ['choice_ch4_07_accept_new_reality', 'ch4_07_throne_resolved', '【剧情】接受眼前的新现实——第一卷的故事已经完全偏离了你知道的剧情', null, 'ch4_08_volume1_end',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_accepted_new_reality":true},"stats":{"insight":1,"leadership":1},"coins":20}', 0,
      'progress', null, 0, 0],

    // ══════════════════════════════════════════════════════════════
    // ch4_08_volume1_end: 第一卷·终
    // ══════════════════════════════════════════════════════════════
    ['choice_ch4_08_settle_volume1', 'ch4_08_volume1_end', '【阶段最终】结算第一卷——将第一卷所有的经历转化为你的终章基础', null, 'ch4_08_volume1_end',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_settled_volume1":true,"volume1_cleared":true},"stats":{"insight":3},"story_fragments":20,"coins":50}', 0,
      'stage_final', null, 0, 1],
    ['choice_ch4_08_reflect_journey', 'ch4_08_volume1_end', '【行动】回顾从地铁到王座的一路旅程——每一段经历都值得铭记', null, 'ch4_08_volume1_end',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_reflected_journey":true},"stats":{"insight":2},"story_fragments":5}', 0,
      'action', null, 1, 0],
    ['choice_ch4_08_talk_companions', 'ch4_08_volume1_end', '【行动】与同伴们交谈——不管结果是怎样的，他们都是与你一起走到这里的人', null, 'ch4_08_volume1_end',
      '{}', '{}', '[]', '[]',
      '{"flags":{"ch4_talked_companions":true},"stats":{"bond":3,"leadership":1}}', 0,
      'action', null, 1, 0],
  ];

  for (const c of choices) {
    insertChoice.run(...c);
  }
}

function seedStory(db) {
  seedChapters(db);
  seedChoices(db);
  console.log('Story seed data (ORV Volume 1 — 32 nodes) inserted.');
}

module.exports = { seedStory };
