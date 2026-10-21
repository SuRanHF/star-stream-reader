-- Isolated H2 schema for combat persistence tests. Never used by the local MySQL database.
SET NON_KEYWORDS VALUE;

CREATE ALIAS IF NOT EXISTS JSON_EXTRACT FOR "com.huazhenghai.readergame.test.H2MysqlJsonFunctions.jsonExtract";
CREATE ALIAS IF NOT EXISTS JSON_UNQUOTE FOR "com.huazhenghai.readergame.test.H2MysqlJsonFunctions.jsonUnquote";

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'player',
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    current_main_chapter VARCHAR(100),
    current_stage VARCHAR(100),
    current_chapter VARCHAR(100),
    current_location VARCHAR(100),
    coins INT NOT NULL DEFAULT 0,
    story_fragments INT NOT NULL DEFAULT 0,
    scenario_proof INT,
    constellation_favor INT,
    king_token INT,
    abyss_mark INT,
    final_page INT,
    stats_json CLOB,
    stage_progress_json CLOB,
    story_flags_json CLOB,
    permanent_flags_json CLOB,
    route_history_json CLOB,
    decision_history_json CLOB,
    visited_nodes_json CLOB,
    activity_history_json CLOB,
    titles_json CLOB,
    title_progress_json CLOB,
    relationships_json CLOB,
    sponsors_json CLOB,
    daily_help_count INT,
    daily_assist_count INT,
    help_date VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    skill_key VARCHAR(100) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    equipped INT NOT NULL DEFAULT 0,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (player_id, skill_key)
);

CREATE TABLE IF NOT EXISTS skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    rarity VARCHAR(30) NOT NULL DEFAULT 'common',
    description CLOB,
    unlock_conditions_json CLOB,
    effects_json CLOB,
    cost_json CLOB,
    cooldown_seconds INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_titles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    title_key VARCHAR(100) NOT NULL,
    equipped INT NOT NULL DEFAULT 0,
    source VARCHAR(100),
    progress_json CLOB,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exploration_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(40),
    stage_key VARCHAR(100),
    location_key VARCHAR(100),
    name VARCHAR(255),
    description CLOB,
    weight INT,
    stamina_cost INT,
    repeatable INT,
    required_conditions_json CLOB,
    rewards_json CLOB,
    risks_json CLOB,
    progress_effects_json CLOB,
    choices_json CLOB,
    log_template CLOB,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_story_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    location_key VARCHAR(100),
    location_name VARCHAR(120),
    event_name VARCHAR(255),
    choice_index INT,
    choice_label VARCHAR(255),
    consequence_text CLOB,
    rewards_snapshot CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    location_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120),
    description VARCHAR(1000),
    unlock_conditions_json CLOB,
    event_rates_json CLOB,
    min_level INT,
    danger_level INT,
    recommended_rank VARCHAR(20),
    is_default INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    item_type VARCHAR(40),
    rarity VARCHAR(40),
    description VARCHAR(1000),
    effects_json CLOB,
    consume_on_use INT,
    sell_price INT,
    max_stack INT NOT NULL DEFAULT 999,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    item_key VARCHAR(100) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_player_item UNIQUE (player_id, item_key)
);

CREATE TABLE IF NOT EXISTS synthesis_recipes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    result_item_key VARCHAR(100) NOT NULL,
    result_quantity INT NOT NULL DEFAULT 1,
    ingredients_json CLOB NOT NULL,
    cost_coins INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS world_bosses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boss_key VARCHAR(100),
    boss_no VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    level INT,
    max_hp BIGINT,
    current_hp BIGINT,
    attack INT,
    defense INT,
    speed INT,
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    killed_at TIMESTAMP,
    rewards_json CLOB,
    rank_rewards_json CLOB,
    worldline_effects_json CLOB,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS world_boss_daily_runs (
    run_date DATE PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS world_boss_participation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    boss_no VARCHAR(100) NOT NULL,
    player_id BIGINT NOT NULL,
    party_no VARCHAR(100),
    damage BIGINT NOT NULL DEFAULT 0,
    attack_count INT NOT NULL DEFAULT 0,
    last_attack_at TIMESTAMP,
    reward_claimed INT NOT NULL DEFAULT 0,
    rank_no INT,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_boss_player UNIQUE (boss_no, player_id),
    CONSTRAINT fk_test_boss_participation_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    slot VARCHAR(40),
    rarity VARCHAR(40),
    description VARCHAR(1000),
    base_stats_json CLOB,
    special_effects_json CLOB,
    set_key VARCHAR(100),
    max_durability INT,
    repair_cost INT,
    sell_price INT,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    equipment_key VARCHAR(100) NOT NULL,
    equipped INT NOT NULL DEFAULT 0,
    slot VARCHAR(40),
    durability INT NOT NULL DEFAULT 100,
    enhancement_level INT NOT NULL DEFAULT 0,
    listed INT NOT NULL DEFAULT 0,
    metadata_json CLOB,
    acquired_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_player_equipment UNIQUE (player_id, equipment_key)
);

CREATE TABLE IF NOT EXISTS player_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    message VARCHAR(2000) NOT NULL,
    type VARCHAR(40) NOT NULL,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS npc_ghosts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ghost_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description CLOB NOT NULL,
    dialogue_tree_json CLOB NOT NULL,
    location_keys_json CLOB NOT NULL,
    encounter_weight DOUBLE NOT NULL DEFAULT 0.05,
    is_unique INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_npc_encounters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    ghost_key VARCHAR(100) NOT NULL,
    choice_made VARCHAR(500),
    outcome CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_action_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    target_player_id BIGINT,
    payload_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS avatar_rank_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rank_key VARCHAR(20) NOT NULL UNIQUE,
    rank_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description CLOB,
    order_num INT NOT NULL,
    next_rank_key VARCHAR(20),
    requirements_json CLOB,
    rewards_json CLOB,
    unlocks_json CLOB,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS constellation_factions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    faction_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    constellation_name VARCHAR(100),
    description CLOB,
    alignment VARCHAR(50),
    ideology VARCHAR(100),
    level INT NOT NULL DEFAULT 1,
    exp BIGINT NOT NULL DEFAULT 0,
    member_count INT NOT NULL DEFAULT 0,
    total_contribution BIGINT NOT NULL DEFAULT 0,
    buffs_json CLOB,
    unlocks_json CLOB,
    metadata_json CLOB,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_factions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    faction_key VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'member',
    reputation BIGINT NOT NULL DEFAULT 0,
    contribution_total BIGINT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP NOT NULL,
    left_at TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_player_faction_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS faction_contributions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    faction_key VARCHAR(100) NOT NULL,
    player_id BIGINT NOT NULL,
    contribution_type VARCHAR(50) NOT NULL,
    value BIGINT NOT NULL DEFAULT 0,
    source VARCHAR(100),
    related_id VARCHAR(100),
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS faction_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    faction_key VARCHAR(100) NOT NULL,
    skill_key VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description CLOB,
    unlock_level INT NOT NULL DEFAULT 1,
    effects_json CLOB,
    cost_json CLOB,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_faction_skill UNIQUE (faction_key, skill_key)
);

CREATE TABLE IF NOT EXISTS player_faction_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    faction_key VARCHAR(100) NOT NULL,
    skill_key VARCHAR(100) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    learned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_player_faction_skill UNIQUE (player_id, skill_key),
    CONSTRAINT fk_test_player_faction_skill_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS player_resource_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_key VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    transaction_group_id VARCHAR(64),
    idempotency_key VARCHAR(160) NOT NULL,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_resource_player_idempotency UNIQUE (player_id, idempotency_key),
    CONSTRAINT fk_test_resource_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS player_action_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_action_receipt UNIQUE (player_id, action_type, idempotency_key),
    CONSTRAINT fk_test_action_receipt_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quest_key VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description CLOB,
    quest_type VARCHAR(20) NOT NULL,
    category VARCHAR(50),
    target_type VARCHAR(50) NOT NULL,
    target_value INT NOT NULL DEFAULT 1,
    conditions_json CLOB,
    rewards_json CLOB,
    reset_cycle VARCHAR(20),
    sort_order INT NOT NULL DEFAULT 0,
    enabled INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS player_quests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    quest_key VARCHAR(100) NOT NULL,
    quest_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    progress INT NOT NULL DEFAULT 0,
    target_value INT NOT NULL DEFAULT 1,
    reward_claimed INT NOT NULL DEFAULT 0,
    cycle_key VARCHAR(50) NOT NULL DEFAULT 'none',
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    claimed_at TIMESTAMP,
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_player_quest_cycle UNIQUE (player_id, quest_key, cycle_key),
    CONSTRAINT fk_test_player_quest_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quest_progress_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    quest_key VARCHAR(100) NOT NULL,
    delta INT NOT NULL DEFAULT 0,
    progress_before INT NOT NULL DEFAULT 0,
    progress_after INT NOT NULL DEFAULT 0,
    source VARCHAR(50),
    related_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trade_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_no VARCHAR(100) NOT NULL UNIQUE,
    seller_player_id BIGINT NOT NULL,
    listing_type VARCHAR(30) NOT NULL,
    item_key VARCHAR(100),
    equipment_key VARCHAR(100),
    quantity INT NOT NULL DEFAULT 1,
    unit_price INT NOT NULL,
    total_price INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sold_at TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS trade_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    listing_no VARCHAR(100) NOT NULL,
    seller_player_id BIGINT NOT NULL,
    buyer_player_id BIGINT NOT NULL,
    listing_type VARCHAR(30) NOT NULL,
    item_key VARCHAR(100),
    equipment_key VARCHAR(100),
    quantity INT NOT NULL DEFAULT 1,
    total_price INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'success',
    metadata_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS battle_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    request_key VARCHAR(64),
    request_action VARCHAR(20),
    monster_key VARCHAR(100) NOT NULL,
    result VARCHAR(30) NOT NULL,
    rounds INT NOT NULL DEFAULT 0,
    player_snapshot_json CLOB,
    monster_snapshot_json CLOB,
    rounds_json CLOB,
    rewards_json CLOB,
    drops_json CLOB,
    result_json CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_battle_request UNIQUE (player_id, request_key)
);

CREATE TABLE IF NOT EXISTS pk_challenges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    mode VARCHAR(20) NOT NULL DEFAULT 'spar',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_test_pk_challenge_attacker FOREIGN KEY (attacker_id) REFERENCES players(id) ON DELETE CASCADE,
    CONSTRAINT fk_test_pk_challenge_defender FOREIGN KEY (defender_id) REFERENCES players(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pk_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attacker_id BIGINT NOT NULL,
    defender_id BIGINT NOT NULL,
    winner_id BIGINT NOT NULL,
    loser_id BIGINT NOT NULL,
    battle_data_json CLOB,
    rating_change_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rankings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL UNIQUE,
    rating INT NOT NULL DEFAULT 1000,
    wins INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0,
    highest_rating INT NOT NULL DEFAULT 1000,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS world_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    state_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description CLOB,
    value_json CLOB,
    level INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'normal',
    updated_by_event_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS worldline_change_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    state_key VARCHAR(100) NOT NULL,
    state_name VARCHAR(100),
    value_key VARCHAR(100) NOT NULL,
    change_amount DOUBLE NOT NULL,
    before_value DOUBLE NOT NULL,
    after_value DOUBLE NOT NULL,
    cause VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS broadcast_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    description CLOB,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'draft',
    target_value INT NOT NULL DEFAULT 100,
    current_value INT NOT NULL DEFAULT 0,
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    rewards_json CLOB,
    personal_rewards_json CLOB,
    worldline_effects_json CLOB,
    conditions_json CLOB,
    metadata_json CLOB,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(50) NOT NULL DEFAULT 'world',
    sender_player_id BIGINT,
    sender_name VARCHAR(100),
    message_type VARCHAR(50) NOT NULL DEFAULT 'player',
    content CLOB NOT NULL,
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS broadcast_contributions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(100) NOT NULL,
    player_id BIGINT NOT NULL,
    contribution_value INT NOT NULL DEFAULT 0,
    contribution_type VARCHAR(50),
    reward_claimed TINYINT NOT NULL DEFAULT 0,
    last_contributed_at TIMESTAMP,
    metadata_json CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_test_broadcast_event_player UNIQUE (event_key, player_id)
);
