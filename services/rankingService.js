// 排行榜服务
const { getDb } = require('../db/database');

function getRankings(limit = 50) {
  const db = getDb();
  const rows = db.prepare(`
    SELECT r.*, p.player_name, p.stats_json
    FROM rankings r
    JOIN players p ON r.player_id = p.id
    ORDER BY r.rating DESC
    LIMIT ?
  `).all(limit);

  return rows.map((r, i) => {
    const stats = JSON.parse(r.stats_json);
    return {
      rank: i + 1,
      player_id: r.player_id,
      player_name: r.player_name,
      level: stats.level || 1,
      rating: r.rating,
      wins: r.wins,
      losses: r.losses,
      highest_rating: r.highest_rating
    };
  });
}

function getPlayerRank(playerId) {
  const db = getDb();
  const allRanked = db.prepare('SELECT player_id, rating FROM rankings ORDER BY rating DESC').all();
  const idx = allRanked.findIndex(r => r.player_id === playerId);
  if (idx < 0) return null;

  const r = db.prepare(`
    SELECT r.*, p.player_name FROM rankings r
    JOIN players p ON r.player_id = p.id
    WHERE r.player_id = ?
  `).get(playerId);

  return {
    rank: idx + 1,
    total_players: allRanked.length,
    rating: r.rating,
    wins: r.wins,
    losses: r.losses,
    highest_rating: r.highest_rating
  };
}

function getAvatarRankLeaderboard(limit) {
  var avatarRankService = require('./avatarRankService');
  return avatarRankService.getAvatarRankLeaderboard(limit);
}

module.exports = { getRankings, getPlayerRank, getAvatarRankLeaderboard };
