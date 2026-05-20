// 装备系统 — 装备/卸下、属性变化、套装加成
const { getDb } = require('../db/database');
const playerService = require('./playerService');

var RARITY_MAX_DURABILITY = { common: 50, uncommon: 75, rare: 100, epic: 150, legendary: 200 };
var RARITY_REPAIR_COST = { common: 2, uncommon: 4, rare: 8, epic: 15, legendary: 25 };

function getEquipment(playerId) {
  var db = getDb();
  var equipped = db.prepare(`
    SELECT pe.slot, pe.equipment_key, pe.durability, pe.max_durability,
           e.name, e.description, e.rarity, e.stats_json, e.effects_json, e.required_level
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ?
  `).all(playerId);

  var setBonuses = getActiveSetBonuses(playerId);

  return equipped.map(function(e) {
    return {
      slot: e.slot,
      equipment_key: e.equipment_key,
      name: e.name,
      description: e.description,
      rarity: e.rarity,
      stats: JSON.parse(e.stats_json),
      effects: JSON.parse(e.effects_json),
      required_level: e.required_level,
      durability: e.durability,
      max_durability: e.max_durability
    };
  });
}

function getEquipmentWithSets(playerId) {
  var db = getDb();
  var equipped = db.prepare(`
    SELECT pe.slot, pe.equipment_key, pe.durability, pe.max_durability,
           e.name, e.description, e.rarity, e.stats_json, e.effects_json, e.required_level
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ?
  `).all(playerId);

  var setBonuses = getActiveSetBonuses(playerId);
  var setInfo = getActiveSetInfo(playerId);

  return {
    equipped: equipped.map(function(e) {
      return {
        slot: e.slot,
        equipment_key: e.equipment_key,
        name: e.name,
        description: e.description,
        rarity: e.rarity,
        stats: JSON.parse(e.stats_json),
        effects: JSON.parse(e.effects_json),
        required_level: e.required_level,
        durability: e.durability,
        max_durability: e.max_durability
      };
    }),
    set_bonuses: setBonuses,
    active_sets: setInfo
  };
}

function getAvailableEquipment(playerId) {
  var db = getDb();
  var player = playerService.get(playerId);
  if (!player) return [];

  var allEquip = db.prepare('SELECT * FROM equipment ORDER BY required_level').all();
  var inventory = db.prepare('SELECT item_key FROM player_inventory WHERE player_id = ?').all(playerId);
  var invKeys = new Set(inventory.map(function(i) { return i.item_key; }));

  return allEquip.map(function(e) {
    return {
      equipment_key: e.equipment_key,
      name: e.name,
      description: e.description,
      slot: e.slot,
      rarity: e.rarity,
      stats: JSON.parse(e.stats_json),
      effects: JSON.parse(e.effects_json),
      required_level: e.required_level,
      can_equip: (player.stats.level || 1) >= e.required_level,
      owned: invKeys.has(e.equipment_key)
    };
  });
}

function equipItem(playerId, equipmentKey, slot) {
  var db = getDb();
  var player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var equipDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(equipmentKey);
  if (!equipDef) return { error: { code: 'EQUIP_NOT_FOUND', message: '装备不存在' } };

  if ((player.stats.level || 1) < equipDef.required_level) {
    return { error: { code: 'LEVEL_TOO_LOW', message: `需要等级 ${equipDef.required_level}` } };
  }

  var targetSlot = slot || equipDef.slot;

  var maxDura = RARITY_MAX_DURABILITY[equipDef.rarity] || 100;

  // Remove existing equipment in same slot
  db.prepare('DELETE FROM player_equipment WHERE player_id = ? AND slot = ?').run(playerId, targetSlot);

  // Equip new with durability
  db.prepare(`INSERT INTO player_equipment (player_id, slot, equipment_key, durability, max_durability)
    VALUES (?, ?, ?, ?, ?)`).run(playerId, targetSlot, equipmentKey, maxDura, maxDura);

  playerService.addLog(playerId, '装备了: ' + equipDef.name);

  // Get set bonuses after equipping
  var setBonuses = getActiveSetBonuses(playerId);

  return {
    success: true,
    equipped: {
      slot: targetSlot,
      equipment_key: equipmentKey,
      name: equipDef.name,
      stats: JSON.parse(equipDef.stats_json),
      effects: JSON.parse(equipDef.effects_json)
    },
    set_bonuses: setBonuses
  };
}

function unequipItem(playerId, slot) {
  var db = getDb();
  var existing = db.prepare('SELECT * FROM player_equipment WHERE player_id = ? AND slot = ?').get(playerId, slot);
  if (!existing) return { error: { code: 'NOT_EQUIPPED', message: '该栏位没有装备' } };

  db.prepare('DELETE FROM player_equipment WHERE player_id = ? AND slot = ?').run(playerId, slot);

  var equipDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(existing.equipment_key);
  playerService.addLog(playerId, '卸下了: ' + (equipDef ? equipDef.name : existing.equipment_key));

  return { success: true };
}

// 计算装备提供的总属性加成 (跳过耐久为0的损坏装备)
function computeEquipmentStats(playerId) {
  var db = getDb();
  var equips = db.prepare('SELECT equipment_key, durability FROM player_equipment WHERE player_id = ? AND durability > 0').all(playerId);
  var stats = { attack: 0, defense: 0, speed: 0, hp: 0 };

  for (var i = 0; i < equips.length; i++) {
    var eq = equips[i];
    var eqDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(eq.equipment_key);
    if (eqDef) {
      var eqStats = JSON.parse(eqDef.stats_json);
      for (var k in eqStats) {
        stats[k] = (stats[k] || 0) + eqStats[k];
      }
    }
  }

  // Add set bonuses
  var setBonuses = getActiveSetBonuses(playerId);
  for (var bk in setBonuses) {
    stats[bk] = (stats[bk] || 0) + setBonuses[bk];
  }

  return stats;
}

function getEquippedMap(playerId) {
  var db = getDb();
  var rows = db.prepare(`
    SELECT pe.slot, pe.equipment_key, pe.durability, pe.max_durability,
           e.name, e.rarity, e.stats_json, e.effects_json
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ?
  `).all(playerId);

  var map = {};
  for (var i = 0; i < rows.length; i++) {
    var r = rows[i];
    map[r.slot] = {
      name: r.name,
      equipment_key: r.equipment_key,
      stats: JSON.parse(r.stats_json),
      effects: JSON.parse(r.effects_json),
      rarity: r.rarity,
      durability: r.durability,
      max_durability: r.max_durability
    };
  }
  return map;
}

// Decrease durability of all equipped items (called after combat/exploration)
function degradeDurability(playerId, amount) {
  var db = getDb();
  var degrade = amount || 1;
  db.prepare(
    "UPDATE player_equipment SET durability = MAX(0, durability - ?), updated_at = datetime('now','localtime') WHERE player_id = ? AND durability > 0"
  ).run(degrade, playerId);
}

// Repair a single equipped item
function repairItem(playerId, slot) {
  var db = getDb();
  var row = db.prepare(`
    SELECT pe.durability, pe.max_durability, e.rarity, e.name
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ? AND pe.slot = ?
  `).get(playerId, slot);

  if (!row) return { error: { code: 'NOT_EQUIPPED', message: '该栏位没有装备' } };
  if (row.durability >= row.max_durability) return { error: { code: 'ALREADY_FULL', message: '装备耐久已满' } };

  var missing = row.max_durability - row.durability;
  var costPerPoint = RARITY_REPAIR_COST[row.rarity] || 5;
  var totalCost = missing * costPerPoint;

  var player = playerService.getRaw(playerId);
  if (!player || (player.coins || 0) < totalCost) {
    return { error: { code: 'NOT_ENOUGH_COINS', message: '修理需要 ' + totalCost + ' 硬币，余额不足' } };
  }

  db.prepare("UPDATE player_equipment SET durability = max_durability, updated_at = datetime('now','localtime') WHERE player_id = ? AND slot = ?")
    .run(playerId, slot);

  playerService.update(playerId, { coins: player.coins - totalCost });
  playerService.addLog(playerId, '修理了 ' + row.name + '（花费 ' + totalCost + ' 硬币）');

  return { success: true, cost: totalCost, slot: slot, item: row.name };
}

// Repair all equipped items
function repairAll(playerId) {
  var db = getDb();
  var rows = db.prepare(`
    SELECT pe.slot, pe.durability, pe.max_durability, e.rarity, e.name
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ? AND pe.durability < pe.max_durability
  `).all(playerId);

  if (rows.length === 0) return { error: { code: 'NOTHING_TO_REPAIR', message: '没有需要修理的装备' } };

  var totalCost = 0;
  for (var i = 0; i < rows.length; i++) {
    var missing = rows[i].max_durability - rows[i].durability;
    var costPerPoint = RARITY_REPAIR_COST[rows[i].rarity] || 5;
    totalCost += missing * costPerPoint;
  }

  var player = playerService.getRaw(playerId);
  if (!player || (player.coins || 0) < totalCost) {
    return { error: { code: 'NOT_ENOUGH_COINS', message: '全部修理需要 ' + totalCost + ' 硬币，余额不足' } };
  }

  for (var j = 0; j < rows.length; j++) {
    db.prepare("UPDATE player_equipment SET durability = max_durability, updated_at = datetime('now','localtime') WHERE player_id = ? AND slot = ?")
      .run(playerId, rows[j].slot);
  }

  playerService.update(playerId, { coins: player.coins - totalCost });
  playerService.addLog(playerId, '修理了全部装备（花费 ' + totalCost + ' 硬币）');

  return { success: true, cost: totalCost, repaired: rows.length };
}

// ===== 套装系统 =====

// 获取所有套装定义
function getAllSets() {
  var db = getDb();
  return db.prepare('SELECT * FROM equipment_sets').all();
}

// 计算玩家当前激活的套装加成
function getActiveSetBonuses(playerId) {
  var db = getDb();
  var equipped = db.prepare('SELECT equipment_key FROM player_equipment WHERE player_id = ?').all(playerId);
  var equippedKeys = new Set(equipped.map(function(e) { return e.equipment_key; }));

  var sets = db.prepare('SELECT * FROM equipment_sets').all();
  var totalBonuses = {};

  for (var i = 0; i < sets.length; i++) {
    var set = sets[i];
    var pieces = JSON.parse(set.pieces_json || '[]');
    // Count how many from this set are equipped
    var matched = 0;
    for (var j = 0; j < pieces.length; j++) {
      if (equippedKeys.has(pieces[j])) {
        matched++;
      }
    }

    if (matched >= 2) {
      var bonuses = JSON.parse(set.bonuses_json || '[]');
      // Apply all qualifying bonus tiers
      for (var b = 0; b < bonuses.length; b++) {
        if (matched >= bonuses[b].pieces_required) {
          var bonus = bonuses[b].bonus;
          for (var bk in bonus) {
            totalBonuses[bk] = (totalBonuses[bk] || 0) + bonus[bk];
          }
        }
      }
    }
  }

  return totalBonuses;
}

// 获取当前激活的套装信息（含 set name, matched pieces, bonuses）
function getActiveSetInfo(playerId) {
  var db = getDb();
  var equipped = db.prepare('SELECT equipment_key FROM player_equipment WHERE player_id = ?').all(playerId);
  var equippedKeys = new Set(equipped.map(function(e) { return e.equipment_key; }));

  var sets = db.prepare('SELECT * FROM equipment_sets').all();
  var activeSets = [];

  for (var i = 0; i < sets.length; i++) {
    var set = sets[i];
    var pieces = JSON.parse(set.pieces_json || '[]');
    var matchedPieces = [];
    for (var j = 0; j < pieces.length; j++) {
      if (equippedKeys.has(pieces[j])) {
        matchedPieces.push(pieces[j]);
      }
    }

    if (matchedPieces.length >= 1) {
      var bonuses = JSON.parse(set.bonuses_json || '[]');
      var activeBonuses = [];
      for (var b = 0; b < bonuses.length; b++) {
        if (matchedPieces.length >= bonuses[b].pieces_required) {
          activeBonuses.push(bonuses[b]);
        }
      }

      activeSets.push({
        set_key: set.set_key,
        set_name: set.set_name,
        total_pieces: pieces.length,
        matched_pieces: matchedPieces.length,
        pieces: pieces,
        equipped_pieces: matchedPieces,
        bonuses: bonuses,
        active_bonuses: activeBonuses
      });
    }
  }

  return activeSets;
}

module.exports = { getEquipment, getEquipmentWithSets, getAvailableEquipment, equipItem, unequipItem, computeEquipmentStats, getEquippedMap, getActiveSetBonuses, getActiveSetInfo, getAllSets, degradeDurability, repairItem, repairAll, RARITY_MAX_DURABILITY, RARITY_REPAIR_COST };
