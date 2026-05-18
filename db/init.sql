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
