// 玩家间交易系统 (Phase 4)
const { getDb } = require('../db/database');
const playerService = require('./playerService');

function createListing(sellerId, itemKey, itemType, quantity, price) {
  var db = getDb();
  var player = playerService.getRaw(sellerId);
  if (!player) return { error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  // Verify seller owns the item
  if (itemType === 'item') {
    var inv = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ?').get(sellerId, itemKey);
    if (!inv || inv.quantity < quantity) {
      return { error: { code: 'NOT_ENOUGH_ITEMS', message: '物品数量不足' } };
    }
    // Reserve items
    db.prepare('UPDATE player_inventory SET quantity = quantity - ? WHERE player_id = ? AND item_key = ?').run(quantity, sellerId, itemKey);
  } else if (itemType === 'equipment') {
    var eq = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ? AND quantity >= 1').get(sellerId, itemKey);
    if (!eq) {
      return { error: { code: 'NOT_ENOUGH_ITEMS', message: '装备不存在或已装备' } };
    }
    db.prepare('UPDATE player_inventory SET quantity = quantity - 1 WHERE player_id = ? AND item_key = ?').run(sellerId, itemKey);
  } else {
    return { error: { code: 'INVALID_TYPE', message: '无效的物品类型' } };
  }

  var result = db.prepare(
    'INSERT INTO trade_listings (seller_id, item_key, item_type, quantity, price) VALUES (?, ?, ?, ?, ?)'
  ).run(sellerId, itemKey, itemType, quantity, price);

  return { listing: getListing(result.lastInsertRowid) };
}

function getListing(id) {
  var db = getDb();
  var row = db.prepare('SELECT * FROM trade_listings WHERE id = ?').get(id);
  if (!row) return null;
  return formatListing(row);
}

function getActiveListings(itemType) {
  var db = getDb();
  var sql = 'SELECT * FROM trade_listings WHERE listing_status = ?';
  var params = ['active'];
  if (itemType) {
    sql += ' AND item_type = ?';
    params.push(itemType);
  }
  sql += ' ORDER BY created_at DESC';
  var rows = db.prepare(sql).all.apply(db.prepare(sql), params);
  return rows.map(formatListing);
}

function getPlayerListings(playerId) {
  var db = getDb();
  var rows = db.prepare('SELECT * FROM trade_listings WHERE seller_id = ? ORDER BY created_at DESC').all(playerId);
  return rows.map(formatListing);
}

function buyListing(listingId, buyerId) {
  var db = getDb();
  var listing = db.prepare('SELECT * FROM trade_listings WHERE id = ? AND listing_status = ?').get(listingId, 'active');
  if (!listing) return { error: { code: 'LISTING_NOT_FOUND', message: '该挂单不存在或已售出' } };
  if (listing.seller_id === buyerId) return { error: { code: 'SELF_BUY', message: '不能购买自己的物品' } };

  var buyer = playerService.get(buyerId);
  if (!buyer) return { error: { code: 'PLAYER_NOT_FOUND', message: '买家不存在' } };
  if ((buyer.coins || 0) < listing.price) return { error: { code: 'NOT_ENOUGH_COINS', message: '金币不足' } };

  // Transfer coins: buyer → seller
  playerService.update(buyerId, { coins: buyer.coins - listing.price });
  var seller = playerService.getRaw(listing.seller_id);
  playerService.update(listing.seller_id, { coins: (seller.coins || 0) + listing.price });

  // Transfer item to buyer
  var existingInv = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ?').get(buyerId, listing.item_key);
  if (existingInv) {
    db.prepare('UPDATE player_inventory SET quantity = quantity + ? WHERE player_id = ? AND item_key = ?').run(listing.quantity, buyerId, listing.item_key);
  } else {
    db.prepare('INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, ?)').run(buyerId, listing.item_key, listing.quantity);
  }

  // Mark listing as sold
  db.prepare("UPDATE trade_listings SET listing_status = 'sold', buyer_id = ?, sold_at = datetime('now','localtime') WHERE id = ?").run(buyerId, listingId);

  playerService.addLog(buyerId, '从市场购买了 ' + listing.item_key + ' ×' + listing.quantity + '，花费 ' + listing.price + ' 金币');
  playerService.addLog(listing.seller_id, '挂单售出: ' + listing.item_key + ' ×' + listing.quantity + '，收入 ' + listing.price + ' 金币');

  // Quest progress tracking (trade for both buyer and seller)
  try {
    var questService = require('./questService');
    questService.checkProgress(buyerId, 'trade', { item_key: listing.item_key });
    questService.checkProgress(listing.seller_id, 'trade', { item_key: listing.item_key });
  } catch (e) { /* quest not critical */ }

  return { success: true, listing: getListing(listingId) };
}

function cancelListing(listingId, playerId) {
  var db = getDb();
  var listing = db.prepare('SELECT * FROM trade_listings WHERE id = ? AND listing_status = ?').get(listingId, 'active');
  if (!listing) return { error: { code: 'LISTING_NOT_FOUND', message: '该挂单不存在或已失效' } };
  if (listing.seller_id !== playerId) return { error: { code: 'NOT_OWNER', message: '只能取消自己的挂单' } };

  // Return items to seller
  var existingInv = db.prepare('SELECT * FROM player_inventory WHERE player_id = ? AND item_key = ?').get(playerId, listing.item_key);
  if (existingInv) {
    db.prepare('UPDATE player_inventory SET quantity = quantity + ? WHERE player_id = ? AND item_key = ?').run(listing.quantity, playerId, listing.item_key);
  } else {
    db.prepare('INSERT INTO player_inventory (player_id, item_key, quantity) VALUES (?, ?, ?)').run(playerId, listing.item_key, listing.quantity);
  }

  db.prepare("UPDATE trade_listings SET listing_status = 'cancelled' WHERE id = ?").run(listingId);
  playerService.addLog(playerId, '取消了挂单: ' + listing.item_key);

  return { success: true };
}

function formatListing(row) {
  var db = getDb();
  var seller = db.prepare('SELECT player_name FROM players WHERE id = ?').get(row.seller_id);
  var itemName = row.item_key;
  var itemDef = db.prepare('SELECT name FROM items WHERE item_key = ?').get(row.item_key);
  if (!itemDef) itemDef = db.prepare('SELECT name FROM equipment WHERE equipment_key = ?').get(row.item_key);
  if (itemDef) itemName = itemDef.name;

  return {
    id: row.id,
    sellerId: row.seller_id,
    sellerName: seller ? seller.player_name : '未知',
    itemKey: row.item_key,
    itemName: itemName,
    itemType: row.item_type,
    quantity: row.quantity,
    price: row.price,
    listingStatus: row.listing_status,
    buyerId: row.buyer_id,
    createdAt: row.created_at,
    soldAt: row.sold_at
  };
}

module.exports = { createListing, getListing, getActiveListings, getPlayerListings, buyListing, cancelListing };
