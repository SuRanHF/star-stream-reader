// 装备系统 — 装备/卸下、属性变化
const { getDb } = require('../db/database');
const playerService = require('./playerService');

function getEquipment(playerId) {
  const db = getDb();
  const equipped = db.prepare(`
    SELECT pe.slot, pe.equipment_key, e.name, e.description, e.rarity, e.stats_json, e.effects_json, e.required_level
    FROM player_equipment pe
    JOIN equipment e ON pe.equipment_key = e.equipment_key
    WHERE pe.player_id = ?
  `).all(playerId);

  return equipped.map(e => ({
    slot: e.slot,
    equipment_key: e.equipment_key,
    name: e.name,
    description: e.description,
    rarity: e.rarity,
    stats: JSON.parse(e.stats_json),
    effects: JSON.parse(e.effects_json),
    required_level: e.required_level
  }));
}

function getAvailableEquipment(playerId) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return [];

  const allEquip = db.prepare('SELECT * FROM equipment ORDER BY required_level').all();
  const inventory = db.prepare('SELECT item_key FROM player_inventory WHERE player_id = ?').all(playerId);
  const invKeys = new Set(inventory.map(i => i.item_key));

  // In this design, equipment is obtained directly (not from inventory items).
  // Equipment becomes available when it's been "found" (exists in player_inventory as type=equipment) or always available for testing.
  // For simplicity, return all equipment with level check
  return allEquip.map(e => ({
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
  }));
}

function equipItem(playerId, equipmentKey, slot) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const equipDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(equipmentKey);
  if (!equipDef) return { error: { code: 'EQUIP_NOT_FOUND', message: '装备不存在' } };

  if ((player.stats.level || 1) < equipDef.required_level) {
    return { error: { code: 'LEVEL_TOO_LOW', message: `需要等级 ${equipDef.required_level}` } };
  }

  const targetSlot = slot || equipDef.slot;

  // Remove existing equipment in same slot
  db.prepare('DELETE FROM player_equipment WHERE player_id = ? AND slot = ?').run(playerId, targetSlot);

  // Equip new
  db.prepare(`INSERT INTO player_equipment (player_id, slot, equipment_key)
    VALUES (?, ?, ?)`).run(playerId, targetSlot, equipmentKey);

  playerService.addLog(playerId, `装备了: ${equipDef.name}`);

  return {
    success: true,
    equipped: {
      slot: targetSlot,
      equipment_key: equipmentKey,
      name: equipDef.name,
      stats: JSON.parse(equipDef.stats_json),
      effects: JSON.parse(equipDef.effects_json)
    }
  };
}

function unequipItem(playerId, slot) {
  const db = getDb();
  const existing = db.prepare('SELECT * FROM player_equipment WHERE player_id = ? AND slot = ?').get(playerId, slot);
  if (!existing) return { error: { code: 'NOT_EQUIPPED', message: '该栏位没有装备' } };

  db.prepare('DELETE FROM player_equipment WHERE player_id = ? AND slot = ?').run(playerId, slot);

  const equipDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(existing.equipment_key);
  playerService.addLog(playerId, `卸下了: ${equipDef ? equipDef.name : existing.equipment_key}`);

  return { success: true };
}

// 计算装备提供的总属性加成
function computeEquipmentStats(playerId) {
  const db = getDb();
  const equips = db.prepare('SELECT equipment_key FROM player_equipment WHERE player_id = ?').all(playerId);
  const stats = { attack: 0, defense: 0, speed: 0, hp: 0 };

  for (const eq of equips) {
    const eqDef = db.prepare('SELECT * FROM equipment WHERE equipment_key = ?').get(eq.equipment_key);
    if (eqDef) {
      const eqStats = JSON.parse(eqDef.stats_json);
      for (const [k, v] of Object.entries(eqStats)) {
        stats[k] = (stats[k] || 0) + v;
      }
    }
  }
  return stats;
}

module.exports = { getEquipment, getAvailableEquipment, equipItem, unequipItem, computeEquipmentStats };
