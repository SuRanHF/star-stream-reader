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

  // Quest progress
  try {
    var questService = require('./questService');
    questService.checkProgress(playerId, 'use_item', { item_key: itemKey });
  } catch (e) { /* quest not critical */ }

  return { success: true, ...result };
}

// Batch sell items
function sellBatch(playerId, items) {
  const db = getDb();
  var player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  var totalCoins = 0;
  var soldNames = [];

  for (var i = 0; i < items.length; i++) {
    var itemKey = items[i].itemKey;
    var sellQty = items[i].quantity || 1;

    var row = db.prepare(`
      SELECT pi.quantity, i.sell_price, i.name
      FROM player_inventory pi
      JOIN items i ON pi.item_key = i.item_key
      WHERE pi.player_id = ? AND pi.item_key = ?
    `).get(playerId, itemKey);

    if (!row || row.quantity < sellQty) continue;
    if (!row.sell_price) continue;

    var price = row.sell_price * sellQty;
    totalCoins += price;
    soldNames.push(row.name + ' x' + sellQty);

    if (row.quantity === sellQty) {
      db.prepare('DELETE FROM player_inventory WHERE player_id = ? AND item_key = ?').run(playerId, itemKey);
    } else {
      db.prepare("UPDATE player_inventory SET quantity = quantity - ?, updated_at = datetime('now','localtime') WHERE player_id = ? AND item_key = ?")
        .run(sellQty, playerId, itemKey);
    }
  }

  if (totalCoins > 0) {
    playerService.update(playerId, { coins: (player.coins || 0) + totalCoins });
    playerService.addLog(playerId, '批量出售: ' + soldNames.join(', ') + ' 获得 ' + totalCoins + ' 硬币');
  }

  return { success: true, coins: totalCoins, sold: soldNames };
}

// Batch use multiple of the same consumable
function useBatch(playerId, itemKey, quantity) {
  var results = [];
  for (var i = 0; i < quantity; i++) {
    var result = useItem(playerId, itemKey);
    if (result.error) break;
    results.push(result);
  }
  return { success: true, used: results.length, results: results };
}

// Synthesis recipes
var SYNTHESIS_RECIPES = {
  relic_to_talisman: { name: '破损遗物→临时护符', inputs: [{ itemKey: 'broken_relic', quantity: 3 }], outputs: [{ itemKey: 'temporary_talisman', quantity: 1 }], cost: 0 },
  potion_to_stamina: { name: '恢复剂→体力药', inputs: [{ itemKey: 'small_hp_potion', quantity: 3 }], outputs: [{ itemKey: 'stamina_pill', quantity: 1 }], cost: 0 },
  scrap_to_memory: { name: '残页→记忆残片', inputs: [{ itemKey: 'story_scrap', quantity: 5 }], outputs: [{ itemKey: 'memory_fragment', quantity: 1 }], cost: 50 },
  token_to_jammer: { name: '赞助凭证→干扰器', inputs: [{ itemKey: 'low_sponsor_token', quantity: 3 }], outputs: [{ itemKey: 'channel_jammer', quantity: 1 }], cost: 30 },
  coin_bag_to_ticket: { name: '硬币袋→地铁票', inputs: [{ itemKey: 'broken_coin_bag', quantity: 3 }], outputs: [{ itemKey: 'old_subway_ticket', quantity: 1 }], cost: 0, bonusCoins: 50 },
  stardust_to_permit: { name: '星屑→剧本许可', inputs: [{ itemKey: 'stardust_powder', quantity: 5 }], outputs: [{ itemKey: 'script_permit_fragment', quantity: 1 }], cost: 100 }
};

function getSynthesisRecipes() {
  return Object.keys(SYNTHESIS_RECIPES).map(function(k) {
    var r = SYNTHESIS_RECIPES[k];
    return { recipeKey: k, name: r.name, inputs: r.inputs, outputs: r.outputs, cost: r.cost, bonusCoins: r.bonusCoins || 0 };
  });
}

function synthesize(playerId, recipeKey) {
  var recipe = SYNTHESIS_RECIPES[recipeKey];
  if (!recipe) return { error: { code: 'INVALID_RECIPE', message: '无效的合成配方' } };

  var db = getDb();
  var player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // Check all inputs
  for (var i = 0; i < recipe.inputs.length; i++) {
    var inp = recipe.inputs[i];
    var row = db.prepare('SELECT quantity FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, inp.itemKey);
    if (!row || row.quantity < inp.quantity) {
      return { error: { code: 'NOT_ENOUGH_MATERIALS', message: '材料不足: 需要 ' + inp.itemKey + ' x' + inp.quantity } };
    }
  }

  // Check coins
  if (recipe.cost && (player.coins || 0) < recipe.cost) {
    return { error: { code: 'NOT_ENOUGH_COINS', message: '硬币不足，需要 ' + recipe.cost + ' 硬币' } };
  }

  // Consume inputs
  for (var j = 0; j < recipe.inputs.length; j++) {
    removeItem(playerId, recipe.inputs[j].itemKey, recipe.inputs[j].quantity);
  }

  // Pay cost
  if (recipe.cost) {
    playerService.update(playerId, { coins: player.coins - recipe.cost });
  }

  // Grant outputs
  for (var k = 0; k < recipe.outputs.length; k++) {
    addItem(playerId, recipe.outputs[k].itemKey, recipe.outputs[k].quantity);
  }

  // Bonus coins
  if (recipe.bonusCoins) {
    playerService.update(playerId, { coins: (player.coins || 0) + recipe.bonusCoins });
  }

  playerService.addLog(playerId, '合成: ' + recipe.name);
  return { success: true, recipe: recipe.name };
}

function synthesizeAll(playerId, recipeKey) {
  var recipe = SYNTHESIS_RECIPES[recipeKey];
  if (!recipe) return { error: { code: 'INVALID_RECIPE', message: '无效的合成配方' } };

  var db = getDb();
  var player = playerService.getRaw(playerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // Count how many times we can synthesize
  var maxTimes = Infinity;
  for (var i = 0; i < recipe.inputs.length; i++) {
    var inp = recipe.inputs[i];
    var row = db.prepare('SELECT quantity FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, inp.itemKey);
    var have = row ? row.quantity : 0;
    var times = Math.floor(have / inp.quantity);
    if (times < maxTimes) maxTimes = times;
  }

  if (maxTimes <= 0) return { error: { code: 'NOT_ENOUGH_MATERIALS', message: '材料不足' } };

  // Check coins
  var totalCost = recipe.cost * maxTimes;
  if (totalCost && (player.coins || 0) < totalCost) {
    // Reduce to what we can afford
    maxTimes = Math.floor((player.coins || 0) / recipe.cost);
    if (maxTimes <= 0) return { error: { code: 'NOT_ENOUGH_COINS', message: '硬币不足' } };
  }

  // Consume inputs
  for (var j = 0; j < recipe.inputs.length; j++) {
    removeItem(playerId, recipe.inputs[j].itemKey, recipe.inputs[j].quantity * maxTimes);
  }

  // Pay cost
  if (recipe.cost) {
    playerService.update(playerId, { coins: (player.coins || 0) - recipe.cost * maxTimes });
  }

  // Grant outputs
  for (var k = 0; k < recipe.outputs.length; k++) {
    addItem(playerId, recipe.outputs[k].itemKey, recipe.outputs[k].quantity * maxTimes);
  }

  // Bonus coins
  if (recipe.bonusCoins) {
    playerService.update(playerId, { coins: (player.coins || 0) + recipe.bonusCoins * maxTimes });
  }

  playerService.addLog(playerId, '批量合成: ' + recipe.name + ' x' + maxTimes);
  return { success: true, recipe: recipe.name, times: maxTimes };
}

module.exports = { getInventory, addItem, removeItem, useItem, sellBatch, useBatch, getSynthesisRecipes, synthesize, synthesizeAll };
