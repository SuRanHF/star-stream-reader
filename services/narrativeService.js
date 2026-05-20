// 碎片化叙事系统 (Phase 5 - Round 2: 对话树格式)
// 物品记忆、地点回响、NPC残影遭遇
const { getDb } = require('../db/database');

function getItemMemories(itemKey) {
  var db = getDb();
  return db.prepare('SELECT * FROM item_memories WHERE item_key = ?').all(itemKey).map(formatMemory);
}

function getAllItemMemories() {
  var db = getDb();
  return db.prepare('SELECT * FROM item_memories').all().map(formatMemory);
}

function formatMemory(row) {
  return {
    id: row.id,
    itemKey: row.item_key,
    memoryText: row.memory_text,
    narrator: row.narrator
  };
}

function getLocationEchoes(locationKey) {
  var db = getDb();
  return db.prepare('SELECT * FROM location_echoes WHERE location_key = ? ORDER BY weight DESC').all(locationKey).map(formatEcho);
}

function formatEcho(row) {
  return {
    id: row.id,
    locationKey: row.location_key,
    echoText: row.echo_text,
    narrator: row.narrator,
    weight: row.weight
  };
}

function recordLocationEcho(playerId, locationKey) {
  var db = getDb();
  var echoes = getLocationEchoes(locationKey);
  if (echoes.length === 0) return null;

  // Weighted random selection
  var totalWeight = echoes.reduce(function(s, e) { return s + e.weight; }, 0);
  var roll = Math.random() * totalWeight;
  var cumulative = 0;
  var chosen = null;
  for (var i = 0; i < echoes.length; i++) {
    cumulative += echoes[i].weight;
    if (roll <= cumulative) { chosen = echoes[i]; break; }
  }
  if (!chosen) chosen = echoes[0];

  return chosen;
}

// ── NPC残影遭遇（对话树格式） ──

function checkNpcGhostEncounter(playerId, locationKey) {
  var db = getDb();
  var player = db.prepare('SELECT stats_json FROM players WHERE id = ?').get(playerId);
  if (!player) return null;

  var stats = JSON.parse(player.stats_json || '{}');
  var luck = stats.luck || 1;

  var ghosts = db.prepare(
    "SELECT * FROM npc_ghosts WHERE location_keys_json LIKE ?"
  ).all('%' + locationKey + '%');

  if (ghosts.length === 0) return null;

  for (var i = 0; i < ghosts.length; i++) {
    var g = ghosts[i];
    var effectiveWeight = g.encounter_weight * (1 + luck * 0.02);

    if (g.is_unique) {
      var prevEncounter = db.prepare(
        'SELECT COUNT(*) as c FROM player_npc_encounters WHERE player_id = ? AND ghost_key = ?'
      ).get(playerId, g.ghost_key).c;
      if (prevEncounter > 0) {
        effectiveWeight *= 0.3;
      }
    }

    if (Math.random() < effectiveWeight) {
      // Parse dialogue tree
      var dialogueTree = JSON.parse(g.dialogue_tree_json || '[]');

      // Count previous encounters to determine starting node
      var encounterCount = db.prepare(
        'SELECT COUNT(*) as c FROM player_npc_encounters WHERE player_id = ? AND ghost_key = ?'
      ).get(playerId, g.ghost_key).c;

      // Start node: use encounter count, capped at last node
      var startNodeIndex = Math.min(encounterCount, dialogueTree.length - 1);

      return {
        ghostKey: g.ghost_key,
        name: g.name,
        description: g.description,
        encounterCount: encounterCount,
        currentNodeIndex: startNodeIndex,
        dialogueTree: dialogueTree
      };
    }
  }

  return null;
}

function processNpcGhostEncounter(playerId, ghostKey, nodeIndex, choiceIndex) {
  var db = getDb();
  var ghost = db.prepare('SELECT * FROM npc_ghosts WHERE ghost_key = ?').get(ghostKey);
  if (!ghost) return { error: 'ghost_not_found' };

  var dialogueTree = JSON.parse(ghost.dialogue_tree_json || '[]');

  // Validate node index
  if (nodeIndex < 0 || nodeIndex >= dialogueTree.length) {
    return { error: 'invalid_node_index' };
  }

  var node = dialogueTree[nodeIndex];
  if (!node || !node.choices) {
    return { error: 'invalid_node' };
  }

  // Validate choice index
  if (choiceIndex < 0 || choiceIndex >= node.choices.length) {
    return { error: 'invalid_choice_index' };
  }

  var choice = node.choices[choiceIndex];

  // Record the encounter
  db.prepare(
    'INSERT INTO player_npc_encounters (player_id, ghost_key, choice_made, outcome) VALUES (?, ?, ?, ?)'
  ).run(
    playerId, ghostKey,
    '[Node ' + nodeIndex + '] ' + choice.text,
    choice.response
  );

  // Apply effects
  var effectsApplied = {};
  if (choice.effects) {
    effectsApplied = applyNpcEffects(db, playerId, choice.effects);
  }

  return {
    ghostKey: ghostKey,
    ghostName: ghost.name,
    nodeIndex: nodeIndex,
    choiceIndex: choiceIndex,
    nodeText: node.text,
    playerChoice: choice.text,
    npcResponse: choice.response,
    effects: choice.effects || {},
    effectsApplied: effectsApplied,
    nextNodeIndex: choice.leadToIndex
  };
}

// 应用NPC对话效果
function applyNpcEffects(db, playerId, effects) {
  var result = { applied: [], errors: [] };

  // Stat changes
  if (effects.statChange) {
    try {
      var player = db.prepare('SELECT stats_json FROM players WHERE id = ?').get(playerId);
      if (player) {
        var stats = JSON.parse(player.stats_json || '{}');
        var sc = effects.statChange;

        // Map stat names to player stat fields
        var statMap = {
          strength: 'strength', hp: 'maxHp', maxHp: 'maxHp',
          defense: 'defense', speed: 'speed', luck: 'luck',
          insight: 'wisdom', wisdom: 'wisdom', sanity: 'sanity',
          heat: 'channelHeat', intelligence: 'intelligence', charisma: 'charisma'
        };

        for (var key in sc) {
          if (sc.hasOwnProperty(key)) {
            var mappedKey = statMap[key] || key;
            var oldVal = (stats[mappedKey] !== undefined) ? stats[mappedKey] : 0;
            stats[mappedKey] = oldVal + sc[key];
            result.applied.push({ stat: mappedKey, from: oldVal, to: stats[mappedKey], delta: sc[key] });
          }
        }

        // Handle HP changes (HP stored in stats_json)
        if (sc.hp) {
          var currentHp = stats.hp || stats.maxHp || 100;
          stats.hp = Math.max(0, currentHp + sc.hp);
          result.applied.push({ stat: 'hp', from: currentHp, to: stats.hp, delta: sc.hp });
        }

        db.prepare('UPDATE players SET stats_json = ? WHERE id = ?').run(JSON.stringify(stats), playerId);
      }
    } catch (e) {
      result.errors.push('statChange: ' + e.message);
    }
  }

  // Add items to player inventory
  if (effects.addItem) {
    try {
      var items = Array.isArray(effects.addItem) ? effects.addItem : [effects.addItem];
      for (var ii = 0; ii < items.length; ii++) {
        var itemKey = items[ii];
        var existing = db.prepare(
          'SELECT id, quantity FROM player_inventory WHERE player_id = ? AND item_key = ?'
        ).get(playerId, itemKey);

        if (existing) {
          db.prepare(
            'UPDATE player_inventory SET quantity = quantity + 1 WHERE id = ?'
          ).run(existing.id);
        } else {
          db.prepare(
            'INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, 1)'
          ).run(playerId, itemKey);
        }
        result.applied.push({ type: 'addItem', itemKey: itemKey });
      }
    } catch (e) {
      result.errors.push('addItem: ' + e.message);
    }
  }

  // Unlock title (stored in players.titles_json)
  if (effects.unlockTitle) {
    try {
      var titleKey = effects.unlockTitle;
      var playerData = db.prepare('SELECT titles_json FROM players WHERE id = ?').get(playerId);
      var playerTitles = JSON.parse((playerData && playerData.titles_json) || '[]');

      if (playerTitles.indexOf(titleKey) === -1) {
        playerTitles.push(titleKey);
        db.prepare('UPDATE players SET titles_json = ? WHERE id = ?').run(JSON.stringify(playerTitles), playerId);
        result.applied.push({ type: 'unlockTitle', titleKey: titleKey });
      }
    } catch (e) {
      result.errors.push('unlockTitle: ' + e.message);
    }
  }

  // Story flags (stored as object in story_flags_json)
  if (effects.addStoryFlag) {
    try {
      var flags = Array.isArray(effects.addStoryFlag) ? effects.addStoryFlag : [effects.addStoryFlag];
      var playerData = db.prepare('SELECT story_flags_json FROM players WHERE id = ?').get(playerId);
      var storyFlags = JSON.parse((playerData && playerData.story_flags_json) || '{}');

      for (var fi = 0; fi < flags.length; fi++) {
        var flag = flags[fi];
        if (!storyFlags[flag]) {
          storyFlags[flag] = true;
          result.applied.push({ type: 'addStoryFlag', flag: flag });
        }
      }

      db.prepare('UPDATE players SET story_flags_json = ? WHERE id = ?').run(JSON.stringify(storyFlags), playerId);
    } catch (e) {
      result.errors.push('addStoryFlag: ' + e.message);
    }
  }

  // Skills (stored in player_skills table)
  if (effects.addSkill) {
    try {
      var skills = Array.isArray(effects.addSkill) ? effects.addSkill : [effects.addSkill];

      for (var si = 0; si < skills.length; si++) {
        var skillKey = skills[si];
        var existing = db.prepare(
          'SELECT id FROM player_skills WHERE player_id = ? AND skill_key = ?'
        ).get(playerId, skillKey);

        if (!existing) {
          db.prepare(
            'INSERT INTO player_skills (player_id, skill_key) VALUES (?, ?)'
          ).run(playerId, skillKey);
          result.applied.push({ type: 'addSkill', skill: skillKey });
        }
      }
    } catch (e) {
      result.errors.push('addSkill: ' + e.message);
    }
  }

  return result;
}

function getPlayerEncounters(playerId, limit) {
  var db = getDb();
  return db.prepare(
    'SELECT * FROM player_npc_encounters WHERE player_id = ? ORDER BY created_at DESC LIMIT ?'
  ).all(playerId, limit || 20).map(function(r) {
    var ghost = db.prepare('SELECT name FROM npc_ghosts WHERE ghost_key = ?').get(r.ghost_key);
    return {
      id: r.id,
      ghostKey: r.ghost_key,
      ghostName: ghost ? ghost.name : r.ghost_key,
      choiceMade: r.choice_made,
      outcome: r.outcome,
      createdAt: r.created_at
    };
  });
}

module.exports = {
  getItemMemories, getAllItemMemories, getLocationEchoes,
  recordLocationEcho, checkNpcGhostEncounter, processNpcGhostEncounter,
  getPlayerEncounters
};
