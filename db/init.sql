-- 全知读者视角 文字冒险游戏 数据库初始化 (Round 2)

CREATE TABLE IF NOT EXISTS chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_key TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    is_ending_chapter INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS choices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    choice_key TEXT UNIQUE NOT NULL,
    chapter_key TEXT NOT NULL REFERENCES chapters(chapter_key),
    text TEXT NOT NULL,
    warning TEXT DEFAULT NULL,
    next_chapter_key TEXT NOT NULL,
    required_flags_json TEXT NOT NULL DEFAULT '{}',
    blocked_flags_json TEXT NOT NULL DEFAULT '{}',
    required_titles_json TEXT NOT NULL DEFAULT '[]',
    blocked_titles_json TEXT NOT NULL DEFAULT '[]',
    effects_json TEXT NOT NULL DEFAULT '{}',
    is_irreversible INTEGER NOT NULL DEFAULT 0,
    choice_type TEXT NOT NULL DEFAULT 'progress',
    decision_group TEXT DEFAULT NULL,
    is_repeatable INTEGER NOT NULL DEFAULT 0,
    completes_stage INTEGER NOT NULL DEFAULT 0,
    hide_after_use INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS titles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    rarity TEXT NOT NULL DEFAULT 'common',
    conditions_json TEXT NOT NULL DEFAULT '{}',
    effects_json TEXT NOT NULL DEFAULT '{}',
    exclusive_with_json TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE IF NOT EXISTS endings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ending_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    conditions_json TEXT NOT NULL DEFAULT '{}',
    is_hidden INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS players (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_name TEXT NOT NULL DEFAULT '未命名读者',
    current_chapter TEXT NOT NULL DEFAULT 'ch1_01_last_train',
    coins INTEGER NOT NULL DEFAULT 0 CHECK(coins >= 0),
    story_fragments INTEGER NOT NULL DEFAULT 0 CHECK(story_fragments >= 0),
    stats_json TEXT NOT NULL DEFAULT '{}',
    relationships_json TEXT NOT NULL DEFAULT '{}',
    route_history_json TEXT NOT NULL DEFAULT '[]',
    story_flags_json TEXT NOT NULL DEFAULT '{}',
    permanent_flags_json TEXT NOT NULL DEFAULT '{}',
    titles_json TEXT NOT NULL DEFAULT '[]',
    title_progress_json TEXT NOT NULL DEFAULT '{}',
    sponsors_json TEXT NOT NULL DEFAULT '[]',
    logs_json TEXT NOT NULL DEFAULT '[]',
    user_id INTEGER DEFAULT NULL,
    consumed_chapters_json TEXT NOT NULL DEFAULT '[]',
    pending_next_chapter TEXT DEFAULT NULL,
    chapter_actions_json TEXT NOT NULL DEFAULT '{}',
    activity_history_json TEXT NOT NULL DEFAULT '[]',
    unlocked_chapters_json TEXT NOT NULL DEFAULT '[]',
    completed_chapters_json TEXT NOT NULL DEFAULT '[]',
    current_main_chapter TEXT NOT NULL DEFAULT 'main_ch01_paid_service',
    breakthrough_resources_json TEXT NOT NULL DEFAULT '{}',
    boss_kills_json TEXT NOT NULL DEFAULT '[]',
    decision_history_json TEXT NOT NULL DEFAULT '[]',
    visited_nodes_json TEXT NOT NULL DEFAULT '[]',
    stage_progress_json TEXT NOT NULL DEFAULT '{}',
    current_location TEXT NOT NULL DEFAULT '',
    last_heartbeat TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    prestige_level INTEGER NOT NULL DEFAULT 0,
    prestige_bonus_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS pk_challenges (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    attacker_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    defender_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','rejected','expired')),
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    resolved_at TEXT
);

CREATE TABLE IF NOT EXISTS saves (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    save_name TEXT NOT NULL,
    save_data_json TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_choices_chapter ON choices(chapter_key);
CREATE INDEX IF NOT EXISTS idx_saves_player ON saves(player_id);

-- ============================================================
-- Round 2: 探索、战斗、道具、装备、技能、PK、排行榜、授权导入
-- ============================================================

CREATE TABLE IF NOT EXISTS locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    location_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    unlock_conditions_json TEXT NOT NULL DEFAULT '{}',
    monster_pool_json TEXT NOT NULL DEFAULT '[]',
    event_pool_json TEXT NOT NULL DEFAULT '[]',
    min_level INTEGER NOT NULL DEFAULT 1,
    danger_level INTEGER NOT NULL DEFAULT 1,
    drop_rate_modifier REAL NOT NULL DEFAULT 1.0,
    event_probabilities_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS monsters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    monster_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    hp INTEGER NOT NULL DEFAULT 100,
    attack INTEGER NOT NULL DEFAULT 10,
    defense INTEGER NOT NULL DEFAULT 5,
    speed INTEGER NOT NULL DEFAULT 5,
    skills_json TEXT NOT NULL DEFAULT '{}',
    rewards_json TEXT NOT NULL DEFAULT '{}',
    drop_table_json TEXT NOT NULL DEFAULT '[]',
    location_keys_json TEXT NOT NULL DEFAULT '[]',
    is_boss INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    item_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    type TEXT NOT NULL,
    rarity TEXT NOT NULL DEFAULT 'common',
    effects_json TEXT NOT NULL DEFAULT '{}',
    stackable INTEGER NOT NULL DEFAULT 1,
    sell_price INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS equipment (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    equipment_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    slot TEXT NOT NULL,
    rarity TEXT NOT NULL DEFAULT 'common',
    stats_json TEXT NOT NULL DEFAULT '{}',
    effects_json TEXT NOT NULL DEFAULT '{}',
    required_level INTEGER NOT NULL DEFAULT 1,
    title_synergy_json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS player_inventory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    item_key TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity >= 0),
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(player_id, item_key)
);

CREATE TABLE IF NOT EXISTS player_equipment (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    slot TEXT NOT NULL,
    equipment_key TEXT NOT NULL,
    durability INTEGER NOT NULL DEFAULT 100,
    max_durability INTEGER NOT NULL DEFAULT 100,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(player_id, slot)
);

CREATE TABLE IF NOT EXISTS skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    skill_type TEXT NOT NULL,
    rarity TEXT NOT NULL DEFAULT 'common',
    effects_json TEXT NOT NULL DEFAULT '{}',
    required_titles_json TEXT NOT NULL DEFAULT '[]',
    required_fragments INTEGER NOT NULL DEFAULT 0,
    cooldown INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    skill_key TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 1,
    unlocked_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(player_id, skill_key)
);

CREATE TABLE IF NOT EXISTS exploration_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    location_key TEXT NOT NULL,
    result_type TEXT NOT NULL,
    result_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS battle_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    battle_type TEXT NOT NULL,
    enemy_key TEXT NOT NULL,
    result TEXT NOT NULL,
    battle_data_json TEXT NOT NULL DEFAULT '{}',
    rewards_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS pk_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    attacker_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    defender_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    winner_id INTEGER REFERENCES players(id) ON DELETE SET NULL,
    loser_id INTEGER REFERENCES players(id) ON DELETE SET NULL,
    battle_data_json TEXT NOT NULL DEFAULT '{}',
    rating_change_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS rankings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL UNIQUE REFERENCES players(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL DEFAULT 1000,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    highest_rating INTEGER NOT NULL DEFAULT 1000,
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS authorized_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_key TEXT UNIQUE NOT NULL,
    source_type TEXT NOT NULL,
    file_name TEXT NOT NULL,
    import_status TEXT NOT NULL DEFAULT 'draft',
    notes TEXT DEFAULT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS story_drafts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL REFERENCES authorized_sources(id),
    draft_key TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    raw_reference_json TEXT NOT NULL DEFAULT '{}',
    adapted_summary TEXT DEFAULT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- ============================================================
-- Round 3: 主线章节突破系统
-- ============================================================

CREATE TABLE IF NOT EXISTS main_chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_key TEXT UNIQUE NOT NULL,
    chapter_name TEXT NOT NULL,
    description TEXT NOT NULL,
    order_index INTEGER NOT NULL DEFAULT 0,
    is_unlocked_by_default INTEGER NOT NULL DEFAULT 0,
    story_chapter_keys_json TEXT NOT NULL DEFAULT '[]',
    last_story_node_key TEXT NOT NULL,
    unlock_conditions_json TEXT NOT NULL DEFAULT '{}',
    breakthrough_cost_json TEXT NOT NULL DEFAULT '{}',
    rewards_json TEXT NOT NULL DEFAULT '{}',
    first_story_node_key TEXT NOT NULL,
    breakthrough_text TEXT NOT NULL DEFAULT '',
    stage_objectives_json TEXT NOT NULL DEFAULT '{}'
);

-- 扩展 players 表 (sql.js 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS, 通过 migrate 处理)

-- 扩展 chapters 表
-- ALTER TABLE chapters ADD COLUMN main_chapter_key TEXT NOT NULL DEFAULT '';

-- Round 2 Indexes
CREATE INDEX IF NOT EXISTS idx_player_inventory_player ON player_inventory(player_id);
CREATE INDEX IF NOT EXISTS idx_player_equipment_player ON player_equipment(player_id);
CREATE INDEX IF NOT EXISTS idx_exploration_logs_player ON exploration_logs(player_id);
CREATE INDEX IF NOT EXISTS idx_battle_logs_player ON battle_logs(player_id);
CREATE INDEX IF NOT EXISTS idx_pk_records_attacker ON pk_records(attacker_id);
CREATE INDEX IF NOT EXISTS idx_pk_records_defender ON pk_records(defender_id);
CREATE INDEX IF NOT EXISTS idx_rankings_rating ON rankings(rating DESC);

-- ============================================================
-- Round 5: 探索驱动剧情触发系统
-- ============================================================

CREATE TABLE IF NOT EXISTS exploration_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_key TEXT UNIQUE NOT NULL,
    event_type TEXT NOT NULL,
    stage_key TEXT DEFAULT NULL,
    location_key TEXT DEFAULT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    weight REAL NOT NULL DEFAULT 1.0,
    repeatable INTEGER NOT NULL DEFAULT 0,
    required_conditions_json TEXT NOT NULL DEFAULT '{}',
    rewards_json TEXT NOT NULL DEFAULT '{}',
    risks_json TEXT NOT NULL DEFAULT '{}',
    progress_effects_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_exploration_events_type ON exploration_events(event_type);
CREATE INDEX IF NOT EXISTS idx_exploration_events_stage ON exploration_events(stage_key);
CREATE INDEX IF NOT EXISTS idx_exploration_events_location ON exploration_events(location_key);

-- ============================================================
-- Round 6: 星流放送系统 (Star Stream Broadcast)
-- ============================================================

CREATE TABLE IF NOT EXISTS broadcast_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_key TEXT UNIQUE NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    event_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    start_time TEXT,
    end_time TEXT,
    requirements_json TEXT NOT NULL DEFAULT '{}',
    objectives_json TEXT NOT NULL DEFAULT '[]',
    rewards_json TEXT NOT NULL DEFAULT '{}',
    failure_penalty_json TEXT NOT NULL DEFAULT '{}',
    generated_by TEXT NOT NULL DEFAULT 'system',
    ai_reason_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS broadcast_participation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id INTEGER NOT NULL REFERENCES broadcast_events(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    joined_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    status TEXT NOT NULL DEFAULT 'joined',
    contribution_score REAL NOT NULL DEFAULT 0,
    claimed_reward TEXT NOT NULL DEFAULT 'none',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(event_id, player_id)
);

CREATE TABLE IF NOT EXISTS broadcast_contributions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id INTEGER NOT NULL REFERENCES broadcast_events(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    contribution_type TEXT NOT NULL,
    amount REAL NOT NULL DEFAULT 1,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_broadcast_participation_event ON broadcast_participation(event_id);
CREATE INDEX IF NOT EXISTS idx_broadcast_participation_player ON broadcast_participation(player_id);
CREATE INDEX IF NOT EXISTS idx_broadcast_contributions_event ON broadcast_contributions(event_id);
CREATE INDEX IF NOT EXISTS idx_broadcast_contributions_player ON broadcast_contributions(player_id);

-- 聊天频道
CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    player_name TEXT NOT NULL,
    message TEXT NOT NULL,
    channel TEXT NOT NULL DEFAULT 'global',
    msg_type TEXT NOT NULL DEFAULT 'chat',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_channel ON chat_messages(channel);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created ON chat_messages(created_at);

-- 好友系统
CREATE TABLE IF NOT EXISTS friendships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    friend_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending', -- pending / accepted / blocked
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(player_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friendships_player ON friendships(player_id);
CREATE INDEX IF NOT EXISTS idx_friendships_friend ON friendships(friend_id);

-- Constellation factions (Phase 3)
CREATE TABLE IF NOT EXISTS constellation_factions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    constellation_key TEXT UNIQUE NOT NULL,
    faction_name TEXT NOT NULL,
    total_contribution_score REAL NOT NULL DEFAULT 0,
    active_members INTEGER NOT NULL DEFAULT 0,
    faction_level INTEGER NOT NULL DEFAULT 1,
    faction_skills_json TEXT NOT NULL DEFAULT '[]',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS faction_contributions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    constellation_key TEXT NOT NULL,
    contribution_type TEXT NOT NULL,
    amount REAL NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS faction_wars (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start TEXT NOT NULL,
    week_end TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    final_scores_json TEXT NOT NULL DEFAULT '{}',
    winner_constellation TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_faction_contributions_player ON faction_contributions(player_id);
CREATE INDEX IF NOT EXISTS idx_faction_contributions_const ON faction_contributions(constellation_key);
CREATE INDEX IF NOT EXISTS idx_faction_wars_status ON faction_wars(status);

-- Global world state (singleton, Phase 2)
CREATE TABLE IF NOT EXISTS world_state (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    world_line_shift REAL NOT NULL DEFAULT 0,
    ripple_decay_rate REAL NOT NULL DEFAULT 0.01,
    last_decay_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    global_event_triggered_at_10 INTEGER NOT NULL DEFAULT 0,
    global_event_triggered_at_25 INTEGER NOT NULL DEFAULT 0,
    global_event_triggered_at_50 INTEGER NOT NULL DEFAULT 0,
    global_event_triggered_at_100 INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- ============================================================
-- Phase 4: 交易系统 + 组队Boss战
-- ============================================================

CREATE TABLE IF NOT EXISTS trade_listings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    item_key TEXT NOT NULL,
    item_type TEXT NOT NULL DEFAULT 'item' CHECK(item_type IN ('item','equipment')),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK(quantity > 0),
    price INTEGER NOT NULL CHECK(price >= 0),
    listing_status TEXT NOT NULL DEFAULT 'active' CHECK(listing_status IN ('active','sold','cancelled')),
    buyer_id INTEGER REFERENCES players(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    sold_at TEXT
);

CREATE TABLE IF NOT EXISTS parties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    leader_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'recruiting' CHECK(status IN ('recruiting','full','in_combat','disbanded')),
    boss_key TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS party_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    party_id INTEGER NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    ready INTEGER NOT NULL DEFAULT 0,
    joined_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(party_id, player_id)
);

CREATE INDEX IF NOT EXISTS idx_trade_listings_seller ON trade_listings(seller_id);
CREATE INDEX IF NOT EXISTS idx_trade_listings_status ON trade_listings(listing_status, item_type);
CREATE INDEX IF NOT EXISTS idx_parties_status ON parties(status);
CREATE INDEX IF NOT EXISTS idx_party_members_party ON party_members(party_id);
CREATE INDEX IF NOT EXISTS idx_party_members_player ON party_members(player_id);

-- ============================================================
-- Phase 3 Round 2: 星座阵营技能树
-- ============================================================

CREATE TABLE IF NOT EXISTS faction_skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    constellation_key TEXT NOT NULL,
    skill_key TEXT UNIQUE NOT NULL,
    skill_name TEXT NOT NULL,
    description TEXT NOT NULL,
    skill_type TEXT NOT NULL,
    effect_json TEXT NOT NULL DEFAULT '{}',
    required_faction_level INTEGER NOT NULL DEFAULT 1,
    cost_faction_contribution INTEGER NOT NULL DEFAULT 0,
    cooldown INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_faction_skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    skill_key TEXT NOT NULL,
    unlocked_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(player_id, skill_key)
);

-- ============================================================
-- Phase 3 Round 2: 装备套装系统
-- ============================================================

CREATE TABLE IF NOT EXISTS equipment_sets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    set_key TEXT UNIQUE NOT NULL,
    set_name TEXT NOT NULL,
    pieces_json TEXT NOT NULL DEFAULT '[]',
    bonuses_json TEXT NOT NULL DEFAULT '[]'
);

-- ============================================================
-- Phase 5: 碎片化叙事系统
-- ============================================================

CREATE TABLE IF NOT EXISTS item_memories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    item_key TEXT NOT NULL,
    memory_text TEXT NOT NULL,
    narrator TEXT NOT NULL DEFAULT 'system',
    unlock_condition TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS location_echoes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    location_key TEXT NOT NULL,
    echo_text TEXT NOT NULL,
    narrator TEXT NOT NULL DEFAULT 'location',
    weight REAL NOT NULL DEFAULT 1.0
);

CREATE TABLE IF NOT EXISTS npc_ghosts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ghost_key TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    dialogue_tree_json TEXT NOT NULL DEFAULT '[]',
    location_keys_json TEXT NOT NULL DEFAULT '[]',
    encounter_weight REAL NOT NULL DEFAULT 0.05,
    is_unique INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_npc_encounters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    ghost_key TEXT NOT NULL,
    choice_made TEXT DEFAULT NULL,
    outcome TEXT DEFAULT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE INDEX IF NOT EXISTS idx_item_memories_item ON item_memories(item_key);
CREATE INDEX IF NOT EXISTS idx_location_echoes_location ON location_echoes(location_key);
CREATE INDEX IF NOT EXISTS idx_player_npc_encounters_player ON player_npc_encounters(player_id);

-- ============================================================
-- Round 11: 悬赏求助系统 (Help Bounty)
-- ============================================================

CREATE TABLE IF NOT EXISTS help_bounties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    monster_key TEXT NOT NULL,
    location_key TEXT NOT NULL DEFAULT '',
    monster_name TEXT NOT NULL DEFAULT '',
    share_percent INTEGER NOT NULL DEFAULT 50 CHECK(share_percent >= 10 AND share_percent <= 90),
    status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','accepted','resolved','expired','cancelled')),
    helper_id INTEGER REFERENCES players(id) ON DELETE SET NULL,
    bounty_rewards_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    resolved_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_help_bounties_status ON help_bounties(status);
CREATE INDEX IF NOT EXISTS idx_help_bounties_owner ON help_bounties(owner_id);

-- World Boss system
CREATE TABLE IF NOT EXISTS world_bosses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    boss_key TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    hp INTEGER NOT NULL DEFAULT 5000,
    max_hp INTEGER NOT NULL DEFAULT 5000,
    attack INTEGER NOT NULL DEFAULT 30,
    defense INTEGER NOT NULL DEFAULT 20,
    speed INTEGER NOT NULL DEFAULT 8,
    level INTEGER NOT NULL DEFAULT 10,
    rewards_json TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'active',
    spawn_time TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    defeat_time TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS world_boss_participation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    boss_id INTEGER NOT NULL REFERENCES world_bosses(id) ON DELETE CASCADE,
    player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    damage_dealt INTEGER NOT NULL DEFAULT 0,
    contribution_score INTEGER NOT NULL DEFAULT 0,
    rewards_claimed INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    UNIQUE(boss_id, player_id)
);

CREATE INDEX IF NOT EXISTS idx_world_bosses_status ON world_bosses(status);
CREATE INDEX IF NOT EXISTS idx_world_boss_participation_boss ON world_boss_participation(boss_id);
