// 战斗系统 — 回合制自动战斗
const { getDb } = require('../db/database');
const playerService = require('./playerService');
const titleService = require('./titleService');
const inventoryService = require('./inventoryService');
const chapterService = require('./chapterService');
const equipmentService = require('./equipmentService');

// 计算玩家总战力（含装备、称号加成）
// 返回 { atk, def, spd, hp, maxHp, critRate, critDamage, level, equipmentBonusHp }
// NOTE: equipmentBonusHp 是临时HP加成，不应持久化到 stats.hp
function calculateCombatPower(player) {
  const stats = player.stats;
  let atk = (stats.attack || 10) + (stats.allocatedAtk || 0);
  let def = (stats.defense || 5) + (stats.allocatedDef || 0);
  let spd = (stats.speed || 10) + (stats.allocatedSpd || 0);
  let baseHp = stats.hp || 100;
  let critRate = (stats.critRate || 0.05) + (stats.allocatedCrit || 0) * 0.02;
  let critDamage = stats.critDamage || 1.5;

  // 称号 combat_bonus 加成
  const titleMods = titleService.computeCombatModifiers(player);
  if (titleMods.attackBonus) atk += Math.round(atk * titleMods.attackBonus);
  if (titleMods.defenseBonus) def += Math.round(def * titleMods.defenseBonus);
  if (titleMods.critRateBonus) critRate += titleMods.critRateBonus;
  if (titleMods.critDamageBonus) critDamage += titleMods.critDamageBonus;
  if (titleMods.damageReduction) def += Math.round(def * titleMods.damageReduction);

  // 称号 stat_modifier 效果: 剧情属性影响战斗属性
  const effectiveStats = titleService.computeEffectiveStats(player);
  if (effectiveStats.combat) atk += effectiveStats.combat * 2;
  if (effectiveStats.intelligence) spd += effectiveStats.intelligence;
  if (effectiveStats.bond) def += effectiveStats.bond;

  // 装备加成 (从 player_equipment 表读取)
  const equipmentStats = equipmentService.computeEquipmentStats(player.id);
  if (equipmentStats.attack) atk += equipmentStats.attack;
  if (equipmentStats.defense) def += equipmentStats.defense;
  if (equipmentStats.speed) spd += equipmentStats.speed;
  let equipmentHp = equipmentStats.hp || 0;

  // 背后星加成
  const constBonus = playerService.getConstellationBonus(stats);
  if (constBonus.atk) atk += constBonus.atk;
  if (constBonus.def) def += constBonus.def;
  if (constBonus.spd) spd += constBonus.spd;
  if (constBonus.critRate) critRate += constBonus.critRate;
  if (constBonus.critDamage) critDamage += constBonus.critDamage;

  // Level scaling: +2 atk, +1 def per level
  const lv = stats.level || 1;
  atk += (lv - 1) * 2;
  def += (lv - 1) * 1;

  const totalHp = baseHp + equipmentHp;
  return { atk, def, spd, hp: totalHp, maxHp: stats.maxHp || 100, critRate, critDamage, level: lv, equipmentBonusHp: equipmentHp };
}

// 回合制自动战斗
function simulateBattle(player, monsterKey, options) {
  const db = getDb();
  const playerPower = calculateCombatPower(player);
  const monsterDef = db.prepare('SELECT * FROM monsters WHERE monster_key = ?').get(monsterKey);
  if (!monsterDef) return { success: false, error: { code: 'MONSTER_NOT_FOUND', message: '怪物不存在' } };

  // 星流放送修正
  let broadcastMods = {};
  try {
    const broadcastService = require('./broadcastService');
    broadcastMods = broadcastService.getActiveModifiers(player.id);
    if (broadcastMods.combatDamageBonus) {
      playerPower.atk += Math.round(playerPower.atk * broadcastMods.combatDamageBonus);
    }
  } catch (e) { /* broadcast not critical */ }

  // 阵营领域修正 — 战斗伤害加成
  let factionMods = {};
  try {
    const factionService = require('./factionService');
    factionMods = factionService.getDomainModifiers(player.id);
    if (factionMods.combatDamageBonus) {
      playerPower.atk += Math.round(playerPower.atk * factionMods.combatDamageBonus);
    }
  } catch (e) { /* faction not critical */ }

  // 世界线修正 — 怪物伤害倍率
  let worldlineMods = { monsterDamageMult: 1.0 };
  try {
    const worldlineService = require('./worldlineService');
    worldlineMods = worldlineService.getWorldlineGlobalModifiers();
  } catch (e) { /* worldline not critical */ }

  let playerHp = playerPower.hp;
  let monsterHp = monsterDef.hp;
  const rounds = [];
  let result = 'draw';

  // 解析怪物技能
  const monsterSkills = JSON.parse(monsterDef.skills_json || '[]');

  // 应用世界线怪物伤害倍率
  const monsterAtkMult = worldlineMods.monsterDamageMult || 1.0;
  const effectiveMonsterAtk = Math.round(monsterDef.attack * monsterAtkMult);

  const attackerFirst = playerPower.spd >= monsterDef.speed;
  const MAX_ROUNDS = 30;

  for (let i = 0; i < MAX_ROUNDS; i++) {
    const round = { round: i + 1, actions: [] };

    // Determine skill usage for this round
    const monsterSkill = (monsterSkills.length > 0 && Math.random() < 0.3)
      ? monsterSkills[Math.floor(Math.random() * monsterSkills.length)]
      : null;

    if (attackerFirst) {
      // Player attacks
      const pDmg = calcDamage(playerPower.atk, monsterDef.defense, playerPower.critRate, playerPower.critDamage);
      monsterHp -= pDmg.damage;
      round.actions.push({ actor: 'player', type: 'attack', damage: pDmg.damage, crit: pDmg.crit, monsterHp: Math.max(0, monsterHp) });
      if (monsterHp <= 0) { result = 'win'; rounds.push(round); break; }

      // Monster attacks
      let mDmg;
      if (monsterSkill) {
        mDmg = calcDamage(Math.round(effectiveMonsterAtk * 1.3), playerPower.def, 0.05, 1.5);
        mDmg.skill = monsterSkill;
        round.actions.push({ actor: 'monster', type: 'skill', skill: monsterSkill, damage: mDmg.damage, crit: mDmg.crit, playerHp: Math.max(0, playerHp - mDmg.damage) });
        playerHp -= mDmg.damage;
      } else {
        mDmg = calcDamage(effectiveMonsterAtk, playerPower.def, 0, 1);
        round.actions.push({ actor: 'monster', type: 'attack', damage: mDmg.damage, crit: false, playerHp: Math.max(0, playerHp - mDmg.damage) });
        playerHp -= mDmg.damage;
      }
      if (playerHp <= 0) { result = 'loss'; rounds.push(round); break; }
    } else {
      // Monster attacks first
      let mDmg;
      if (monsterSkill) {
        mDmg = calcDamage(Math.round(effectiveMonsterAtk * 1.3), playerPower.def, 0.05, 1.5);
        mDmg.skill = monsterSkill;
        round.actions.push({ actor: 'monster', type: 'skill', skill: monsterSkill, damage: mDmg.damage, crit: mDmg.crit, playerHp: Math.max(0, playerHp - mDmg.damage) });
        playerHp -= mDmg.damage;
      } else {
        mDmg = calcDamage(effectiveMonsterAtk, playerPower.def, 0, 1);
        round.actions.push({ actor: 'monster', type: 'attack', damage: mDmg.damage, crit: false, playerHp: Math.max(0, playerHp - mDmg.damage) });
        playerHp -= mDmg.damage;
      }
      if (playerHp <= 0) { result = 'loss'; rounds.push(round); break; }

      // Player attacks
      const pDmg = calcDamage(playerPower.atk, monsterDef.defense, playerPower.critRate, playerPower.critDamage);
      monsterHp -= pDmg.damage;
      round.actions.push({ actor: 'player', type: 'attack', damage: pDmg.damage, crit: pDmg.crit, monsterHp: Math.max(0, monsterHp) });
      if (monsterHp <= 0) { result = 'win'; rounds.push(round); break; }
    }

    rounds.push(round);
  }

  // 30 回合未分胜负
  if (result === 'draw') {
    if (playerHp > monsterHp) result = 'win';
    else if (monsterHp > playerHp) result = 'loss';
  }

  return {
    result,
    playerHpRemaining: Math.round(Math.max(0, playerHp)),
    monsterHpRemaining: Math.round(Math.max(0, monsterHp)),
    totalRounds: rounds.length,
    rounds,
    equipmentBonusHp: playerPower.equipmentBonusHp,
    monster: { name: monsterDef.name, level: monsterDef.level, hp: monsterDef.hp, attack: effectiveMonsterAtk, defense: monsterDef.defense, speed: monsterDef.speed },
    player: { name: player.player_name, level: playerPower.level, atk: playerPower.atk, def: playerPower.def, spd: playerPower.spd, hp: playerPower.hp }
  };
}

function calcDamage(atk, def, critRate, critDmg) {
  const baseDmg = Math.max(1, Math.round(atk - def * 0.5));
  const crit = Math.random() < critRate;
  const dmg = crit ? Math.round(baseDmg * critDmg) : baseDmg;
  return { damage: dmg, crit };
}

// 应用战斗奖励
function applyBattleRewards(playerId, monsterKey, battleResult, battleData) {
  const db = getDb();
  const monster = db.prepare('SELECT * FROM monsters WHERE monster_key = ?').get(monsterKey);
  if (!monster) return null;

  const player = playerService.getRaw(playerId);
  if (!player) return { success: false, error: { code: 'PLAYER_NOT_FOUND', message: 'Player not found' } };
  const stats = JSON.parse(player.stats_json);
  const rewards = JSON.parse(monster.rewards_json);

  const earned = { exp: 0, coins: 0, items: [] };

  if (battleResult === 'win') {
    // Exp
    if (rewards.exp) {
      earned.exp = rewards.exp;
      stats.exp = (stats.exp || 0) + rewards.exp;
      // Level up check
      const newLevel = Math.floor(stats.exp / 100) + 1;
      const oldLevel = stats.level || 1;
      if (newLevel > oldLevel) {
        const levelsGained = newLevel - oldLevel;
        stats.level = newLevel;
        stats.maxHp = 100 + (newLevel - 1) * 20;
        stats.hp = stats.maxHp;
        stats.attack = 10 + (newLevel - 1) * 2;
        stats.defense = 5 + (newLevel - 1) * 1;
        stats.freePoints = (stats.freePoints || 0) + 3 * levelsGained;
      } else {
        // Return base HP after combat (not including equipment bonus)
        const remainingHp = battleData ? Math.max(1, battleData.playerHpRemaining) : stats.hp;
        const equipmentHp = battleData ? (battleData.equipmentBonusHp || 0) : 0;
        stats.hp = Math.max(1, Math.min(stats.maxHp, remainingHp - equipmentHp));
      }
    } else {
      const remainingHp = battleData ? Math.max(1, battleData.playerHpRemaining) : stats.hp;
      const equipmentHp = battleData ? (battleData.equipmentBonusHp || 0) : 0;
      stats.hp = Math.max(1, Math.min(stats.maxHp, remainingHp - equipmentHp));
    }

    // Coins (w/ title coin multiplier + channel heat)
    if (rewards.coins) {
      const playerFull = playerService.get(playerId);
      const coinMult = titleService.computeCoinMultiplier(playerFull);
      const heatBonus = 1 + (stats.channelHeat || 0) * 0.01;
      earned.coins = Math.round(rewards.coins * coinMult * heatBonus);
      const newCoins = (player.coins || 0) + earned.coins;
      playerService.update(playerId, { coins: newCoins });
    }

    // Drop items (w/ title drop rate bonus)
    const explorationMods = titleService.computeExplorationModifiers(playerService.get(playerId));
    const dropRateBonus = explorationMods.dropRateBonus || 0;
    const dropTable = JSON.parse(monster.drop_table_json);
    for (const drop of dropTable) {
      if (Math.random() < ((drop.rate || 0) + dropRateBonus)) {
        inventoryService.addItem(playerId, drop.item_key, 1);
        earned.items.push(drop.item_key);
      }
    }

    // Boss kill: record and grant breakthrough resources
    if (monster.is_boss) {
      chapterService.recordBossKill(playerId, monsterKey);
      // Bosses grant storyFragments (merged from scenarioProof at 10x)
      const fragmentAmount = Math.max(1, Math.floor((monster.level || 1) / 3)) + Math.max(1, Math.floor((monster.level || 1) / 5)) * 10;
      chapterService.awardResource(playerId, 'storyFragments', fragmentAmount);
      earned.story_fragments = fragmentAmount;
      // Late-game bosses grant abyssMark
      if (monster.level >= 10) {
        chapterService.awardResource(playerId, 'abyssMark', 1);
        earned.abyss_mark = 1;
      }
      // 世界线稳定化 — 击杀Boss减少偏移
      try {
        const worldlineService = require('./worldlineService');
        worldlineService.contributeShift(-2, playerId);
      } catch (e) { /* worldline not critical */ }
    }

    // Elite monsters (level >= 8 but not boss) grant storyFragments
    if (!monster.is_boss && monster.level >= 8) {
      chapterService.awardResource(playerId, 'storyFragments', 1);
      earned.story_fragments = (earned.story_fragments || 0) + 1;
    }

    playerService.update(playerId, { stats_json: stats });

    // Round 6: 星流放送贡献记录
    try {
      const broadcastService = require('./broadcastService');
      const contribs = [{ type: 'kill_monster', amount: 1, metadata: { monster_key: monsterKey } }];
      if (monster.is_boss) contribs.push({ type: 'defeat_boss', amount: 1, metadata: { boss_key: monsterKey, boss_level: monster.level } });
      broadcastService.tryRecordContributions(playerId, contribs);
    } catch (e) { /* broadcast not critical */ }

    // Phase 3: 阵营贡献记录
    try {
      const factionService = require('./factionService');
      var combatContrib = monster.is_boss ? 5 : (monster.level >= 8 ? 2 : 1);
      factionService.recordContribution(playerId, combatContrib, monster.is_boss ? 'defeat_boss' : 'kill_monster');
    } catch (e) { /* faction not critical */ }
  } else {
    // Loss penalty
    const coinsLost = Math.min(player.coins, 10);
    if (coinsLost > 0) {
      playerService.update(playerId, { coins: Math.max(0, player.coins - coinsLost) });
      earned.coins = -coinsLost;
    }
    // HP after loss (return base HP without equipment bonus)
    const remainingHp = battleData ? Math.max(1, battleData.playerHpRemaining) : 1;
    const equipmentHp = battleData ? (battleData.equipmentBonusHp || 0) : 0;
    stats.hp = Math.max(1, Math.min(stats.maxHp, remainingHp - equipmentHp));
    playerService.update(playerId, { stats_json: stats });
  }

  return earned;
}

// 玩家选择行动后的战斗结算
function resolveCombat(playerId, monsterKey, action) {
  const db = getDb();
  const player = playerService.get(playerId);
  if (!player) return { success: false, error: { code: 'PLAYER_NOT_FOUND', message: '玩家不存在' } };

  const monster = db.prepare('SELECT * FROM monsters WHERE monster_key = ?').get(monsterKey);
  if (!monster) return { success: false, error: { code: 'MONSTER_NOT_FOUND', message: '怪物不存在' } };

  // Durability degradation on any combat action
  try {
    var equipmentService = require('./equipmentService');
    equipmentService.degradeDurability(playerId, 1 + Math.floor(Math.random() * 2));
  } catch (e) { /* not critical */ }

  const playerPower = calculateCombatPower(player);
  const stats = player.stats;
  const bond = stats.bond || 0;

  if (action === 'flee') {
    const fleeRate = Math.min(0.9, Math.max(0.1, 0.3 + (playerPower.spd - monster.speed) * 0.05));
    const success = Math.random() < fleeRate;
    if (success) {
      playerService.addLog(playerId, `成功从 ${monster.name} 面前逃脱。`);
      return {
        action: 'flee',
        success: true,
        message: `逃跑成功！你安全脱离了战斗。`
      };
    } else {
      // Monster gets a free attack
      const mDmg = calcDamage(monster.attack, playerPower.def, 0, 1);
      const newHp = Math.max(0, (stats.hp || playerPower.hp) - mDmg.damage);
      const s = { ...stats, hp: newHp };
      playerService.update(playerId, { stats_json: s });
      playerService.addLog(playerId, `逃跑失败！${monster.name} 趁机攻击，造成 ${mDmg.damage} 点伤害。`);
      return {
        action: 'flee',
        success: false,
        message: `逃跑失败！${monster.name} 趁机攻击，造成 ${mDmg.damage} 点伤害。`,
        damage_taken: mDmg.damage,
        player: playerService.get(playerId)
      };
    }
  }

  if (action === 'support') {
    const supportRate = Math.min(0.8, Math.max(0.2, bond * 0.08));
    const success = Math.random() < supportRate;
    if (success) {
      playerService.addLog(playerId, `羁绊之力召唤了援助！攻击力获得强化。`);
      // Apply support bonus then fight
      const boostedPlayer = playerService.get(playerId);
      const origAtk = boostedPlayer.stats.attack;
      boostedPlayer.stats.attack = Math.round(origAtk * 1.3);
      const battle = simulateBattle(boostedPlayer, monsterKey, { supportBoost: true });
      boostedPlayer.stats.attack = origAtk; // restore

      if (battle.error) return battle;

      let rewards = null;
      let logType = 'monster';
      if (battle.result === 'win') {
        rewards = applyBattleRewards(playerId, monsterKey, 'win', battle);
        battle.rewards = rewards;
      } else {
        rewards = applyBattleRewards(playerId, monsterKey, 'loss', battle);
        battle.rewards = rewards;
      }

      db.prepare(`INSERT INTO battle_logs (player_id, battle_type, enemy_key, result, battle_data_json, rewards_json)
        VALUES (?, ?, ?, ?, ?, ?)`).run(
        playerId, logType, monsterKey, battle.result,
        JSON.stringify({ rounds: battle.totalRounds, playerHpRemaining: battle.playerHpRemaining, support: true }),
        JSON.stringify(rewards || {})
      );

      // Quest progress tracking
      try {
        var qService = require('./questService');
        if (battle.result === 'win') {
          qService.checkProgress(playerId, 'defeat_monster', { monster_key: monsterKey });
          if (monster.is_boss) {
            qService.checkProgress(playerId, 'defeat_boss', { monster_key: monsterKey, is_boss: true });
          }
        }
        if (monster.is_elite) {
          qService.checkProgress(playerId, 'defeat_elite', { monster_key: monsterKey, is_elite: true });
        }
      } catch (e) { /* quest not critical */ }

      return {
        action: 'support',
        success: true,
        message: '羁绊回应了你！援军加入战斗。',
        battle,
        player: playerService.get(playerId)
      };
    } else {
      // Failed support — monster gets free attack
      const mDmg = calcDamage(monster.attack, playerPower.def, 0, 1);
      const newHp = Math.max(0, (stats.hp || playerPower.hp) - mDmg.damage);
      const s = { ...stats, hp: newHp };
      playerService.update(playerId, { stats_json: s });
      playerService.addLog(playerId, `请求支援失败！${monster.name} 趁机攻击，造成 ${mDmg.damage} 点伤害。`);
      return {
        action: 'support',
        success: false,
        message: `没有回应...${monster.name} 趁机攻击，造成 ${mDmg.damage} 点伤害。`,
        damage_taken: mDmg.damage,
        player: playerService.get(playerId)
      };
    }
  }

  // Default: fight
  const battle = simulateBattle(player, monsterKey, {});
  if (battle.error) return battle;

  let rewards = null;
  let logType = 'monster';
  if (battle.result === 'win') {
    rewards = applyBattleRewards(playerId, monsterKey, 'win', battle);
    battle.rewards = rewards;
  } else {
    rewards = applyBattleRewards(playerId, monsterKey, 'loss', battle);
    battle.rewards = rewards;
  }

  db.prepare(`INSERT INTO battle_logs (player_id, battle_type, enemy_key, result, battle_data_json, rewards_json)
    VALUES (?, ?, ?, ?, ?, ?)`).run(
    playerId, logType, monsterKey, battle.result,
    JSON.stringify({ rounds: battle.totalRounds, playerHpRemaining: battle.playerHpRemaining }),
    JSON.stringify(rewards || {})
  );

  // Quest progress tracking
  try {
    var questService = require('./questService');
    if (battle.result === 'win') {
      questService.checkProgress(playerId, 'defeat_monster', { monster_key: monsterKey });
      if (monster.is_boss) {
        questService.checkProgress(playerId, 'defeat_boss', { monster_key: monsterKey, is_boss: true });
      }
    }
    if (monster.is_elite) {
      questService.checkProgress(playerId, 'defeat_elite', { monster_key: monsterKey, is_elite: true });
    }
  } catch (e) { /* quest not critical */ }

  return {
    action: 'fight',
    success: true,
    battle,
    player: playerService.get(playerId)
  };
}

module.exports = { calculateCombatPower, simulateBattle, applyBattleRewards, resolveCombat };
