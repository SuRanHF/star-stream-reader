// 背包系统 — 道具管理
const { getDb } = require('../db/database');
const playerService = require('./playerService');

function getInventory(playerId) {
  const db = getDb();
  const rows = db.prepare(`
    SELECT pi.*, i.name, i.description, i.type, i.rarity, i.effects_json, i.stackable, i.sell_price
    FROM player_inventory pi
    JOIN items i ON pi.item_key = i.item_key
    WHERE pi.player_id = ?
    ORDER BY i.rarity DESC, i.name
  `).all(playerId);

  return rows.map(r => ({
    item_key: r.item_key,
    name: r.name,
    description: r.description,
    type: r.type,
    rarity: r.rarity,
    effects: JSON.parse(r.effects_json),
    quantity: r.quantity,
    stackable: !!r.stackable,
    sell_price: r.sell_price
  }));
}

function addItem(playerId, itemKey, quantity) {
  const db = getDb();
  const existing = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, itemKey);

  if (existing) {
    db.prepare(`UPDATE player_inventory SET quantity = quantity + ?, updated_at = datetime('now','localtime')
      WHERE player_id = ? AND item_key = ?`).run(quantity, playerId, itemKey);
  } else {
    db.prepare(`INSERT INTO player_inventory (player_id, item_key, quantity)
      VALUES (?, ?, ?)`).run(playerId, itemKey, quantity);
  }

  playerService.addLog(playerId, `获得道具: ${itemKey} x${quantity}`);
  return getInventory(playerId);
}

function removeItem(playerId, itemKey, quantity) {
  const db = getDb();
  const existing = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, itemKey);
  if (!existing || existing.quantity < quantity) return false;

  if (existing.quantity === quantity) {
    db.prepare('DELETE FROM player_inventory WHERE player_id = ? AND item_key = ?').run(playerId, itemKey);
  } else {
    db.prepare(`UPDATE player_inventory SET quantity = quantity - ?, updated_at = datetime('now','localtime')
      WHERE player_id = ? AND item_key = ?`).run(quantity, playerId, itemKey);
  }
  return true;
}

function useItem(playerId, itemKey) {
  const db = getDb();
  const row = db.prepare(`
    SELECT pi.*, i.type, i.effects_json, i.name
    FROM player_inventory pi
    JOIN items i ON pi.item_key = i.item_key
    WHERE pi.player_id = ? AND pi.item_key = ?
  `).get(playerId, itemKey);

  if (!row || row.quantity <= 0) return { error: { code: 'NO_ITEM', message: '道具不足' } };
  if (row.type !== 'consumable') return { error: { code: 'NOT_CONSUMABLE', message: '该道具不可使用' } };

  const effects = JSON.parse(row.effects_json);
  const player = playerService.getRaw(playerId);
  const stats = JSON.parse(player.stats_json);

  // Apply effects
  const result = { used: row.name, effects: {} };

  if (effects.heal_hp) {
    const heal = effects.heal_hp;
    stats.hp = Math.min(stats.maxHp || 100, (stats.hp || 100) + heal);
    result.effects.heal = heal;
  }
  if (effects.restore_stamina) {
    const restore = effects.restore_stamina;
    stats.stamina = Math.min(stats.maxStamina || 50, (stats.stamina || 0) + restore);
    result.effects.stamina_restore = restore;
  }
  if (effects.coins_random) {
    const coins = effects.coins_random.min + Math.floor(Math.random() * (effects.coins_random.max - effects.coins_random.min));
    playerService.update(playerId, { coins: (player.coins || 0) + coins });
    result.effects.coins = coins;
  }
  if (effects.escape_battle) {
    result.effects.escape = true;
  }
  if (effects.world_line_shift) {
    result.effects.worldLineShift = effects.world_line_shift;
  }

  // Apply stat changes
  playerService.update(playerId, { stats_json: stats });

  // Consume item
  removeItem(playerId, itemKey, 1);
  playerService.addLog(playerId, `使用了: ${row.name}`);

  return { success: true, ...result };
}

module.exports = { getInventory, addItem, removeItem, useItem };
