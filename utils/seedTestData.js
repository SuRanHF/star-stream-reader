// 测试数据种子工具 — 为后端测试创建模拟数据
var getDb = function() { return require('../db/database').getDb(); };
var playerService = require('../services/playerService');
var broadcastService = require('../services/broadcastService');
var chatService = require('../services/chatService');

function seedTestData() {
  var db = getDb();
  var results = { players: 0, broadcasts: 0, chats: 0, friends: 0 };

  // 1. Create test players if not enough
  var existingPlayers = db.prepare('SELECT COUNT(*) as c FROM players').get().c;
  if (existingPlayers < 5) {
    var names = ['星流读者', '剧本猎人', '最终之页', '黑炎龙', '影之支配者'];
    var locations = ['ruined_station', 'abandoned_platform', 'dark_tunnel'];
    var ranks = ['F', 'F', 'E', 'D', 'C'];
    for (var i = 0; i < names.length; i++) {
      try {
        var userId = db.prepare("SELECT id FROM users WHERE username = ?").get('testbot' + i);
        if (!userId) {
          db.prepare("INSERT INTO users (username, email, password_hash, role) VALUES (?, ?, ?, 'player')").run(
            'testbot' + i, 'bot' + i + '@test.local', '$2a$10$placeholder'
          );
          userId = db.prepare("SELECT id FROM users WHERE username = ?").get('testbot' + i);
        }
        // Create player
        var stats = {
          level: 1 + i * 3,
          hp: 100 + i * 20,
          maxHp: 100 + i * 20,
          stamina: 45 + i * 5,
          maxStamina: 50 + i * 5,
          attack: 10 + i * 3,
          defense: 5 + i * 2,
          speed: 10 + i,
          critRate: 0.05,
          critDamage: 1.5,
          channelHeat: i * 50,
          worldLineShift: i * 2,
          avatarRank: ranks[i],
          avatarRankName: {F: '临时化身', E: '剧本幸存者', D: '频道记录者', C: '剧本执行者'}[ranks[i]],
          storyGrade: i >= 3 ? 'notable' : 'ordinary',
          insight: i * 2,
          willpower: i,
          leadership: Math.floor(i / 2)
        };
        var playerId = playerService.create(names[i], userId.id);
        if (playerId) {
          // Give some coins and fragments
          playerService.update(playerId, { coins: i * 100, story_fragments: i * 5 });

          // Create ranking entry
          var existingRank = db.prepare('SELECT id FROM rankings WHERE player_id = ?').get(playerId);
          if (!existingRank) {
            db.prepare('INSERT INTO rankings (player_id, rating, wins, losses) VALUES (?, ?, ?, ?)').run(
              playerId, 1000 + i * 50, i * 2, i
            );
          }

          results.players++;
        }
      } catch (e) {
        // Player might already exist
      }
    }
  }

  // 2. Create test broadcasts
  var activeEvents = db.prepare("SELECT COUNT(*) as c FROM broadcast_events WHERE status = 'active'").get().c;
  if (activeEvents === 0) {
    var drafts = [
      { event_key: 'test_explore_drive', eventType: 'exploration_drive', title: '废都探索驱动', description: '废弃车站周边出现了异常的能量波动。全体化身协力探索，突破迷雾！', objectives: [{ type: 'explore_location', label: '探索位置', target: 30, score_per_unit: 1 }], durationMinutes: 60 },
      { event_key: 'test_story_hunt', eventType: 'story_hunt', title: '遗失章节狩猎', description: '星流中漂浮着失落的章节片段。谁能第一个找到完整的剧本？', objectives: [{ type: 'trigger_story', label: '触发剧情', target: 10, score_per_unit: 3 }], durationMinutes: 45 }
    ];

    for (var di = 0; di < drafts.length; di++) {
      var d = drafts[di];
      try {
        var created = broadcastService.createDraft(d);
        if (created.success) {
          broadcastService.activateEvent(created.data.id);
          results.broadcasts++;
        }
      } catch (e) {
        // Ignore duplicates
      }
    }
  }

  // 3. Create test chat messages
  var chatCount = db.prepare('SELECT COUNT(*) as c FROM chat_messages').get().c;
  if (chatCount === 0) {
    var chatMessages = [
      { name: '系统', msg: '星流频道初始化完成。欢迎各位化身进入观测站。' },
      { name: '星流读者', msg: '新来的吗？废弃车站有很多东西可以探索。' },
      { name: '系统', msg: '提示：探索可能遇到随机事件，包括战斗、支线剧情和隐藏宝箱。' }
    ];
    for (var ci = 0; ci < chatMessages.length; ci++) {
      chatService.sendMessage(0, chatMessages[ci].name, chatMessages[ci].msg, 'global');
      results.chats++;
    }
  }

  // 4. Create test broadcast contributions
  if (results.broadcasts > 0) {
    var event = db.prepare("SELECT * FROM broadcast_events WHERE status = 'active' LIMIT 1").get();
    if (event) {
      var players = db.prepare('SELECT id FROM players LIMIT 3').all();
      for (var pi = 0; pi < players.length; pi++) {
        try {
          broadcastService.joinEvent(event.id, players[pi].id);
          var types = ['explore_location', 'trigger_story'];
          broadcastService.recordContribution(event.id, players[pi].id, types[Math.floor(Math.random() * types.length)], 1 + Math.floor(Math.random() * 3));
        } catch (e) {
          // Already joined
        }
      }
    }
  }

  return { success: true, data: results };
}

module.exports = { seedTestData };
