-- ============================================================
-- 全知读者视角 文字冒险游戏
-- MySQL 8 数据库初始化 — Phase 1 核心表
-- 数据库: reader_game
-- 字符集: utf8mb4 / 排序规则: utf8mb4_unicode_ci
-- ============================================================

CREATE DATABASE IF NOT EXISTS `reader_game`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `reader_game`;

-- ============================================================
-- 1. 用户表 (users)
-- 存储登录账号信息，与游戏角色(players)分离
-- ============================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(50)     NOT NULL,
    `email`         VARCHAR(100)    NOT NULL,
    `password_hash` VARCHAR(255)    NOT NULL,
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'player'   COMMENT 'player / admin / moderator',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'normal'   COMMENT 'normal / banned / suspended',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_username` (`username`),
    UNIQUE KEY `uk_users_email`    (`email`),
    INDEX      `idx_users_role`    (`role`),
    INDEX      `idx_users_status`  (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- ============================================================
-- 2. 玩家表 (players)
-- 每个用户一个游戏角色，存储全部游戏状态
-- ============================================================
CREATE TABLE IF NOT EXISTS `players` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`              BIGINT       NOT NULL,
    `player_name`          VARCHAR(50)  NOT NULL,

    -- 剧情进度
    `current_main_chapter` VARCHAR(100) NOT NULL DEFAULT 'main_ch01_paid_service' COMMENT '当前主线章节',
    `current_stage`        VARCHAR(100) DEFAULT NULL                            COMMENT '当前阶段标识',
    `current_chapter`      VARCHAR(100) DEFAULT NULL                            COMMENT '当前剧情章节',
    `current_location`     VARCHAR(100) NOT NULL DEFAULT ''                     COMMENT '当前所在地点',

    -- 通用货币 (由应用层保证 >= 0)
    `coins`                INT          NOT NULL DEFAULT 0  COMMENT '金币',
    `story_fragments`      INT          NOT NULL DEFAULT 0  COMMENT '故事碎片',
    `scenario_proof`       INT          NOT NULL DEFAULT 0  COMMENT '剧情认证',
    `constellation_favor`  INT          NOT NULL DEFAULT 0  COMMENT '星座好感度',
    `king_token`           INT          NOT NULL DEFAULT 0  COMMENT '王者代币',
    `abyss_mark`           INT          NOT NULL DEFAULT 0  COMMENT '深渊印记',
    `final_page`           INT          NOT NULL DEFAULT 0  COMMENT '终章书页',

    -- 战斗属性 (JSON)
    `stats_json`           JSON         DEFAULT NULL COMMENT 'level/exp/hp/attack/defense/speed/crit 等',

    -- 游戏状态 (JSON)
    `stage_progress_json`  JSON         DEFAULT NULL COMMENT '各阶段推进进度',
    `story_flags_json`     JSON         DEFAULT NULL COMMENT '剧情旗标 {flag: true/false}',
    `permanent_flags_json` JSON         DEFAULT NULL COMMENT '永久旗标(跨轮回保留)',
    `route_history_json`   JSON         DEFAULT NULL COMMENT '已走过路线 [{chapter_key, choice_key, timestamp}]',
    `decision_history_json`JSON         DEFAULT NULL COMMENT '重大决策记录',
    `visited_nodes_json`   JSON         DEFAULT NULL COMMENT '已访问剧情节点',
    `activity_history_json`JSON         DEFAULT NULL COMMENT '活动参与历史',

    -- 称号与进度 (JSON)
    `titles_json`          JSON         DEFAULT NULL COMMENT '已解锁称号列表',
    `title_progress_json`  JSON         DEFAULT NULL COMMENT '称号进度追踪',

    -- 社交/阵营 (JSON)
    `relationships_json`   JSON         DEFAULT NULL COMMENT '角色关系值 {character_key: value}',
    `sponsors_json`        JSON         DEFAULT NULL COMMENT '赞助者/星座信息',

    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_players_user_id`          (`user_id`),
    INDEX      `idx_players_player_name`     (`player_name`),
    INDEX      `idx_players_current_main`    (`current_main_chapter`),
    INDEX      `idx_players_current_location`(`current_location`),

    CONSTRAINT `fk_players_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家角色表';

-- ============================================================
-- 3. 玩家日志表 (player_logs)
-- 记录玩家在游戏中的各类事件日志
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_logs` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`     BIGINT       NOT NULL,
    `type`          VARCHAR(50)  NOT NULL DEFAULT 'info'   COMMENT 'info / warning / error / milestone / battle / exploration',
    `message`       TEXT         NOT NULL,
    `metadata_json` JSON         DEFAULT NULL              COMMENT '附加结构化数据',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_player_logs_player_id` (`player_id`),
    INDEX `idx_player_logs_type`      (`type`),
    INDEX `idx_player_logs_created`   (`created_at`),

    CONSTRAINT `fk_player_logs_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家事件日志表';

-- ============================================================
-- 4. 反馈表 (feedbacks)
-- 玩家提交的 bug 反馈、建议等
-- ============================================================
CREATE TABLE IF NOT EXISTS `feedbacks` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`  BIGINT       DEFAULT NULL              COMMENT '提交玩家ID(可匿名)',
    `nickname`   VARCHAR(50)  DEFAULT NULL,
    `type`       VARCHAR(50)  DEFAULT NULL              COMMENT 'bug / suggestion / praise / other',
    `content`    TEXT         NOT NULL,
    `page`       VARCHAR(100) DEFAULT NULL              COMMENT '提交时所在页面',
    `stage`      VARCHAR(100) DEFAULT NULL              COMMENT '提交时所在章节/阶段',
    `status`     VARCHAR(30)  NOT NULL DEFAULT 'new'    COMMENT 'new / reviewing / resolved / closed',
    `admin_note` TEXT         DEFAULT NULL              COMMENT '管理员备注',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_feedbacks_player_id` (`player_id`),
    INDEX `idx_feedbacks_status`    (`status`),
    INDEX `idx_feedbacks_created`   (`created_at`),

    CONSTRAINT `fk_feedbacks_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家反馈表';

-- ============================================================
-- 5. 管理员操作日志表 (admin_action_logs)
-- 审计追踪: 记录管理员的所有操作
-- ============================================================
CREATE TABLE IF NOT EXISTS `admin_action_logs` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `admin_user_id`    BIGINT       DEFAULT NULL              COMMENT '操作者(管理员)用户ID',
    `action`           VARCHAR(100) NOT NULL                  COMMENT '操作类型: ban_user / edit_player / add_item / send_mail 等',
    `target_player_id` BIGINT       DEFAULT NULL              COMMENT '被操作的玩家ID',
    `payload_json`     JSON         DEFAULT NULL              COMMENT '操作详情(修改了什么、参数等)',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_admin_logs_admin`   (`admin_user_id`),
    INDEX `idx_admin_logs_target`  (`target_player_id`),
    INDEX `idx_admin_logs_action`  (`action`),
    INDEX `idx_admin_logs_created` (`created_at`),

    CONSTRAINT `fk_admin_logs_admin`
        FOREIGN KEY (`admin_user_id`) REFERENCES `users` (`id`)
        ON DELETE SET NULL,

    CONSTRAINT `fk_admin_logs_target`
        FOREIGN KEY (`target_player_id`) REFERENCES `players` (`id`)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作审计日志表';

-- ============================================================
-- 6. 地点表 (locations)
-- 探索系统: 玩家可前往探索的地点
-- ============================================================
CREATE TABLE IF NOT EXISTS `locations` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT,
    `location_key`           VARCHAR(100) NOT NULL,
    `name`                   VARCHAR(100) NOT NULL,
    `description`            TEXT         DEFAULT NULL,
    `unlock_conditions_json` JSON         DEFAULT NULL COMMENT '解锁条件 {required_level, required_flags, required_titles}',
    `event_rates_json`       JSON         DEFAULT NULL COMMENT '事件类型概率覆盖 {}',
    `min_level`              INT          NOT NULL DEFAULT 1,
    `danger_level`           INT          NOT NULL DEFAULT 1,
    `recommended_rank`       VARCHAR(20)  DEFAULT NULL COMMENT '推荐位阶',
    `is_default`             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认解锁',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_locations_key`   (`location_key`),
    INDEX      `idx_locations_min`  (`min_level`),
    INDEX      `idx_locations_danger` (`danger_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='探索地点表';

-- ============================================================
-- 7. 探索事件表 (exploration_events)
-- 探索时按权重抽取事件
-- ============================================================
CREATE TABLE IF NOT EXISTS `exploration_events` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `event_key`                VARCHAR(100) NOT NULL,
    `event_type`               VARCHAR(50)  NOT NULL COMMENT 'story / side_story / resource / opportunity / boss_clue / hidden / empty / battle_placeholder',
    `stage_key`                VARCHAR(100) DEFAULT NULL COMMENT '主线阶段key (story类型使用)',
    `location_key`             VARCHAR(100) DEFAULT NULL COMMENT '限定地点 (NULL=不限)',
    `name`                     VARCHAR(100) NOT NULL,
    `description`              TEXT         DEFAULT NULL,
    `weight`                   INT          NOT NULL DEFAULT 10 COMMENT '抽取权重',
    `stamina_cost`             INT          NOT NULL DEFAULT 5 COMMENT '体力消耗',
    `repeatable`               TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可重复触发',
    `required_conditions_json` JSON         DEFAULT NULL COMMENT '触发条件',
    `rewards_json`             JSON         DEFAULT NULL COMMENT '奖励',
    `risks_json`               JSON         DEFAULT NULL COMMENT '风险',
    `progress_effects_json`    JSON         DEFAULT NULL COMMENT '进度效果',
    `choices_json`             JSON         DEFAULT NULL COMMENT '分支选项 JSON: [{label, consequence_text, rewards_override, unlock_locations, unlock_events, title_bias}]',
    `log_template`             TEXT         DEFAULT NULL COMMENT '日志模板',
    `enabled`                  TINYINT      NOT NULL DEFAULT 1,
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_events_key`   (`event_key`),
    INDEX      `idx_events_type` (`event_type`),
    INDEX      `idx_events_stage` (`stage_key`),
    INDEX      `idx_events_location` (`location_key`),
    INDEX      `idx_events_enabled`  (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='探索事件表';

-- ============================================================
-- 8. 称号定义表 (titles)
-- 所有可获得称号的静态定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `titles` (
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT,
    `title_key`              VARCHAR(100) NOT NULL,
    `name`                   VARCHAR(100) NOT NULL,
    `category`               VARCHAR(50)  DEFAULT NULL COMMENT 'reader/survivor/explorer/combat/starstream/king/abyss/support',
    `rarity`                 VARCHAR(30)  NOT NULL DEFAULT 'common' COMMENT 'common/uncommon/rare/epic/legendary',
    `description`            TEXT         DEFAULT NULL,
    `unlock_conditions_json` JSON         DEFAULT NULL COMMENT '解锁条件 JSON',
    `effects_json`           JSON         DEFAULT NULL COMMENT '加成效果 JSON',
    `tags_json`              JSON         DEFAULT NULL COMMENT '标签列表 JSON',
    `strong_against_json`    JSON         DEFAULT NULL COMMENT '克制叙事身份 JSON',
    `weak_against_json`      JSON         DEFAULT NULL COMMENT '被克制叙事身份 JSON',
    `enabled`                TINYINT      NOT NULL DEFAULT 1,
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_titles_key`    (`title_key`),
    INDEX      `idx_titles_category` (`category`),
    INDEX      `idx_titles_rarity`   (`rarity`),
    INDEX      `idx_titles_enabled`  (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='称号定义表';

-- ============================================================
-- 9. 玩家称号关联表 (player_titles)
-- 记录玩家拥有哪些称号及装备状态
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_titles` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`     BIGINT       NOT NULL,
    `title_key`     VARCHAR(100) NOT NULL,
    `equipped`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否装备为主称号',
    `source`        VARCHAR(100) DEFAULT NULL COMMENT '获得途径',
    `progress_json` JSON         DEFAULT NULL COMMENT '解锁进度追踪',
    `unlocked_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_title`   (`player_id`, `title_key`),
    INDEX      `idx_pt_player_id`  (`player_id`),
    INDEX      `idx_pt_title_key`  (`title_key`),
    INDEX      `idx_pt_equipped`   (`equipped`),

    CONSTRAINT `fk_pt_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pt_title`
        FOREIGN KEY (`title_key`) REFERENCES `titles` (`title_key`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家称号关联表';

-- ============================================================
-- 10. 化身位阶配置表 (avatar_rank_configs)
-- 定义 F→SSS 各等级升阶条件与奖励
-- ============================================================
CREATE TABLE IF NOT EXISTS `avatar_rank_configs` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `rank_key`          VARCHAR(20)  NOT NULL,
    `rank_name`         VARCHAR(100) NOT NULL,
    `display_name`      VARCHAR(100) NOT NULL,
    `description`       TEXT         DEFAULT NULL,
    `order_num`         INT          NOT NULL,
    `next_rank_key`     VARCHAR(20)  DEFAULT NULL,
    `requirements_json` JSON         DEFAULT NULL COMMENT '升阶条件列表 JSON',
    `rewards_json`      JSON         DEFAULT NULL COMMENT '升阶奖励 JSON',
    `unlocks_json`      JSON         DEFAULT NULL COMMENT '位阶解锁内容 JSON',
    `enabled`           TINYINT      NOT NULL DEFAULT 1,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_arc_rank_key` (`rank_key`),
    INDEX      `idx_arc_order`   (`order_num`),
    INDEX      `idx_arc_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='化身位阶配置表';

-- ============================================================
-- 11. 主线阶段表 (main_chapters)
-- 定义主线各阶段的解锁条件、完成目标与奖励
-- ============================================================
CREATE TABLE IF NOT EXISTS `main_chapters` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `chapter_key`              VARCHAR(100) NOT NULL,
    `name`                     VARCHAR(100) NOT NULL,
    `description`              TEXT         DEFAULT NULL,
    `order_num`                INT          NOT NULL,
    `unlock_conditions_json`   JSON         DEFAULT NULL COMMENT '解锁条件 JSON',
    `completion_conditions_json` JSON       DEFAULT NULL COMMENT '完成条件 JSON',
    `rewards_json`             JSON         DEFAULT NULL COMMENT '阶段奖励 JSON',
    `next_chapter_key`         VARCHAR(100) DEFAULT NULL,
    `enabled`                  TINYINT      NOT NULL DEFAULT 1,
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mc_chapter_key` (`chapter_key`),
    INDEX      `idx_mc_order`      (`order_num`),
    INDEX      `idx_mc_enabled`    (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主线阶段表';

-- ============================================================
-- 12. 物品定义表 (items)
-- 所有可用物品的静态定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `items` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `item_key`        VARCHAR(100) NOT NULL,
    `name`            VARCHAR(100) NOT NULL,
    `item_type`       VARCHAR(30)  NOT NULL COMMENT 'consumable / resource',
    `rarity`          VARCHAR(20)  NOT NULL DEFAULT 'common' COMMENT 'common / uncommon / rare / epic / legendary',
    `description`     TEXT         DEFAULT NULL,
    `effects_json`    JSON         DEFAULT NULL COMMENT '使用效果 JSON',
    `consume_on_use`  TINYINT      NOT NULL DEFAULT 1 COMMENT '使用后是否消耗',
    `sell_price`      INT          NOT NULL DEFAULT 0 COMMENT '出售单价(金币)',
    `max_stack`       INT          NOT NULL DEFAULT 999 COMMENT '最大堆叠数',
    `enabled`         TINYINT      NOT NULL DEFAULT 1,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_items_key`   (`item_key`),
    INDEX      `idx_items_type` (`item_type`),
    INDEX      `idx_items_rarity` (`rarity`),
    INDEX      `idx_items_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物品定义表';

-- ============================================================
-- 13. 玩家背包表 (player_inventory)
-- 记录玩家拥有哪些物品及数量
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_inventory` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`   BIGINT       NOT NULL,
    `item_key`    VARCHAR(100) NOT NULL,
    `quantity`    INT          NOT NULL DEFAULT 0,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pi_player_item` (`player_id`, `item_key`),
    INDEX      `idx_pi_player_id`  (`player_id`),
    INDEX      `idx_pi_item_key`   (`item_key`),

    CONSTRAINT `fk_pi_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pi_item`
        FOREIGN KEY (`item_key`) REFERENCES `items` (`item_key`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家背包表';

-- ============================================================
-- Phase 4 (items + inventory) 初始化完成
-- ============================================================

-- ============================================================
-- 14. 装备定义表 (equipment)
-- 所有可用装备的静态定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `equipment` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `equipment_key`        VARCHAR(100) NOT NULL,
    `name`                 VARCHAR(100) NOT NULL,
    `slot`                 VARCHAR(50)  NOT NULL COMMENT 'weapon / armor / accessory / relic',
    `rarity`               VARCHAR(30)  NOT NULL DEFAULT 'common' COMMENT 'common / uncommon / rare / epic / legendary',
    `description`          TEXT         DEFAULT NULL,
    `base_stats_json`      JSON         DEFAULT NULL COMMENT '基础属性加成 JSON',
    `special_effects_json` JSON         DEFAULT NULL COMMENT '特殊效果 JSON',
    `set_key`              VARCHAR(100) DEFAULT NULL COMMENT '所属套装 key',
    `max_durability`       INT          NOT NULL DEFAULT 100,
    `repair_cost`          INT          NOT NULL DEFAULT 10 COMMENT '单次修理花费(金币)',
    `sell_price`           INT          NOT NULL DEFAULT 0,
    `enabled`              TINYINT      NOT NULL DEFAULT 1,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_equip_key`     (`equipment_key`),
    INDEX      `idx_equip_slot`   (`slot`),
    INDEX      `idx_equip_rarity` (`rarity`),
    INDEX      `idx_equip_set`    (`set_key`),
    INDEX      `idx_equip_enabled`(`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='装备定义表';

-- ============================================================
-- 15. 玩家装备表 (player_equipment)
-- 记录玩家拥有哪些装备及穿戴/耐久状态
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_equipment` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`         BIGINT       NOT NULL,
    `equipment_key`     VARCHAR(100) NOT NULL,
    `equipped`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否穿戴',
    `slot`              VARCHAR(50)  NOT NULL,
    `durability`        INT          NOT NULL DEFAULT 100,
    `enhancement_level` INT          NOT NULL DEFAULT 0,
    `metadata_json`     JSON         DEFAULT NULL,
    `acquired_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pe_player_equip` (`player_id`, `equipment_key`),
    INDEX      `idx_pe_player_id`   (`player_id`),
    INDEX      `idx_pe_equip_key`   (`equipment_key`),
    INDEX      `idx_pe_slot`        (`slot`),
    INDEX      `idx_pe_equipped`    (`equipped`),

    CONSTRAINT `fk_pe_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pe_equip`
        FOREIGN KEY (`equipment_key`) REFERENCES `equipment` (`equipment_key`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家装备表';

-- ============================================================
-- 16. 套装定义表 (equipment_sets)
-- 装备套装及其加成效果定义
-- ============================================================
CREATE TABLE IF NOT EXISTS `equipment_sets` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `set_key`       VARCHAR(100) NOT NULL,
    `name`          VARCHAR(100) NOT NULL,
    `description`   TEXT         DEFAULT NULL,
    `bonuses_json`  JSON         DEFAULT NULL COMMENT '套装加成效果 JSON',
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_es_set_key` (`set_key`),
    INDEX      `idx_es_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套装定义表';

-- ============================================================
-- Phase 5A (equipment) 初始化完成
-- ============================================================

-- ============================================================
-- 17. 技能定义表 (skills)
-- ============================================================
CREATE TABLE IF NOT EXISTS `skills` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `skill_key`                VARCHAR(100) NOT NULL,
    `name`                     VARCHAR(100) NOT NULL,
    `type`                     VARCHAR(50)  NOT NULL COMMENT 'passive / active_placeholder / exploration / narrative / combat',
    `rarity`                   VARCHAR(30)  NOT NULL DEFAULT 'common' COMMENT 'common / uncommon / rare / epic / legendary',
    `description`              TEXT         DEFAULT NULL,
    `unlock_conditions_json`   JSON         DEFAULT NULL COMMENT '解锁条件',
    `effects_json`             JSON         DEFAULT NULL COMMENT '技能效果',
    `cost_json`                JSON         DEFAULT NULL COMMENT '解锁消耗 (coins / storyFragments)',
    `cooldown_seconds`         INT          NOT NULL DEFAULT 0,
    `enabled`                  TINYINT      NOT NULL DEFAULT 1,
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_key`   (`skill_key`),
    INDEX      `idx_skill_type` (`type`),
    INDEX      `idx_skill_rarity` (`rarity`),
    INDEX      `idx_skill_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能定义表';

-- ============================================================
-- 18. 玩家技能表 (player_skills)
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_skills` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`        BIGINT       NOT NULL,
    `skill_key`        VARCHAR(100) NOT NULL,
    `level`            INT          NOT NULL DEFAULT 1,
    `equipped`         TINYINT      NOT NULL DEFAULT 0 COMMENT '暂未使用，预留后续技能槽功能',
    `unlocked_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `metadata_json`    JSON         DEFAULT NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_skill`  (`player_id`, `skill_key`),
    INDEX      `idx_ps_player_id` (`player_id`),
    INDEX      `idx_ps_skill_key` (`skill_key`),
    INDEX      `idx_ps_equipped`  (`equipped`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家技能表';

-- ============================================================
-- Phase 5B (skill) 初始化完成
-- ============================================================

-- ============================================================
-- 19. 怪物定义表 (monsters)
-- ============================================================
CREATE TABLE IF NOT EXISTS `monsters` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `monster_key`    VARCHAR(100) NOT NULL,
    `name`           VARCHAR(100) NOT NULL,
    `type`           VARCHAR(50)  NOT NULL COMMENT 'normal / elite / boss_placeholder',
    `rarity`         VARCHAR(30)  NOT NULL DEFAULT 'common' COMMENT 'common / uncommon / rare / epic',
    `location_key`   VARCHAR(100) DEFAULT NULL,
    `stage_key`      VARCHAR(100) DEFAULT NULL,
    `description`    TEXT         DEFAULT NULL,
    `stats_json`     JSON         DEFAULT NULL COMMENT '怪物属性',
    `skills_json`    JSON         DEFAULT NULL COMMENT '怪物技能',
    `rewards_json`   JSON         DEFAULT NULL COMMENT '战斗奖励',
    `drops_json`     JSON         DEFAULT NULL COMMENT '掉落配置',
    `enabled`        TINYINT      NOT NULL DEFAULT 1,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_monster_key`    (`monster_key`),
    INDEX      `idx_monster_type`  (`type`),
    INDEX      `idx_monster_rarity` (`rarity`),
    INDEX      `idx_monster_location` (`location_key`),
    INDEX      `idx_monster_stage`  (`stage_key`),
    INDEX      `idx_monster_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='怪物定义表';

-- ============================================================
-- 20. 战斗日志表 (battle_logs)
-- ============================================================
CREATE TABLE IF NOT EXISTS `battle_logs` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`             BIGINT       NOT NULL,
    `monster_key`           VARCHAR(100) NOT NULL,
    `result`                VARCHAR(30)  NOT NULL COMMENT 'win / lose / escape / draw',
    `rounds`                INT          NOT NULL DEFAULT 0,
    `player_snapshot_json`  JSON         DEFAULT NULL COMMENT '战斗开始时的玩家属性快照',
    `monster_snapshot_json` JSON         DEFAULT NULL COMMENT '怪物属性快照',
    `rounds_json`           JSON         DEFAULT NULL COMMENT '每回合详情',
    `rewards_json`          JSON         DEFAULT NULL COMMENT '获得的奖励',
    `drops_json`            JSON         DEFAULT NULL COMMENT '掉落物品',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_bl_player_id`   (`player_id`),
    INDEX `idx_bl_monster_key` (`monster_key`),
    INDEX `idx_bl_result`     (`result`),
    INDEX `idx_bl_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='战斗日志表';

-- ============================================================
-- Phase 5C (combat) 初始化完成
-- ============================================================

-- ============================================================
-- 21. PK挑战表 (pk_challenges)
-- ============================================================
CREATE TABLE IF NOT EXISTS `pk_challenges` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `attacker_id`   BIGINT       NOT NULL,
    `defender_id`   BIGINT       NOT NULL,
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending / accepted / rejected / expired',
    `mode`          VARCHAR(20)  NOT NULL DEFAULT 'spar'    COMMENT 'spar / deathmatch',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `resolved_at`   DATETIME     DEFAULT NULL,

    PRIMARY KEY (`id`),
    INDEX `idx_pkc_attacker`   (`attacker_id`),
    INDEX `idx_pkc_defender`   (`defender_id`),
    INDEX `idx_pkc_status`     (`status`),
    INDEX `idx_pkc_created`    (`created_at`),

    CONSTRAINT `fk_pkc_attacker`
        FOREIGN KEY (`attacker_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pkc_defender`
        FOREIGN KEY (`defender_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PK挑战表';

-- ============================================================
-- 22. PK记录表 (pk_records)
-- ============================================================
CREATE TABLE IF NOT EXISTS `pk_records` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `attacker_id`         BIGINT       NOT NULL,
    `defender_id`         BIGINT       NOT NULL,
    `winner_id`           BIGINT       NOT NULL,
    `loser_id`            BIGINT       NOT NULL,
    `battle_data_json`    JSON         DEFAULT NULL COMMENT '战斗数据 {rounds, attackerHpRemaining, defenderHpRemaining}',
    `rating_change_json`  JSON         DEFAULT NULL COMMENT '评分变化 {attacker_change, defender_change}',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_pkr_attacker` (`attacker_id`),
    INDEX `idx_pkr_defender` (`defender_id`),
    INDEX `idx_pkr_winner`   (`winner_id`),
    INDEX `idx_pkr_loser`    (`loser_id`),
    INDEX `idx_pkr_created`  (`created_at`),

    CONSTRAINT `fk_pkr_attacker`
        FOREIGN KEY (`attacker_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pkr_defender`
        FOREIGN KEY (`defender_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pkr_winner`
        FOREIGN KEY (`winner_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_pkr_loser`
        FOREIGN KEY (`loser_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PK战斗记录表';

-- ============================================================
-- 23. 排行榜表 (rankings)
-- ============================================================
CREATE TABLE IF NOT EXISTS `rankings` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT,
    `player_id`      BIGINT   NOT NULL,
    `rating`         INT      NOT NULL DEFAULT 1000,
    `wins`           INT      NOT NULL DEFAULT 0,
    `losses`         INT      NOT NULL DEFAULT 0,
    `highest_rating` INT      NOT NULL DEFAULT 1000,
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rankings_player` (`player_id`),
    INDEX      `idx_rankings_rating` (`rating`),

    CONSTRAINT `fk_rankings_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家排行榜表';

-- ============================================================
-- Phase 6A/6B (pk + ranking) 初始化完成
-- ============================================================

-- ============================================================
-- 24. 星流放送表 (broadcast_events)
-- ============================================================
CREATE TABLE IF NOT EXISTS `broadcast_events` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `event_key`                VARCHAR(100) NOT NULL,
    `title`                    VARCHAR(100) NOT NULL,
    `description`              TEXT         DEFAULT NULL,
    `type`                     VARCHAR(50)  NOT NULL COMMENT 'explore / combat / pk / mixed',
    `status`                   VARCHAR(30)  NOT NULL DEFAULT 'draft' COMMENT 'draft / active / completed / expired / cancelled',
    `target_value`             INT          NOT NULL DEFAULT 100,
    `current_value`            INT          NOT NULL DEFAULT 0,
    `start_at`                 DATETIME     DEFAULT NULL,
    `end_at`                   DATETIME     DEFAULT NULL,
    `rewards_json`             JSON         DEFAULT NULL COMMENT '全服完成奖励',
    `personal_rewards_json`    JSON         DEFAULT NULL COMMENT '个人参与奖励 {minContribution, coins, storyFragments, channelHeat, items}',
    `worldline_effects_json`   JSON         DEFAULT NULL COMMENT '完成后世界线效果 {worldline_shift, starstream_attention, scenario_pressure}',
    `conditions_json`          JSON         DEFAULT NULL COMMENT '激活条件',
    `metadata_json`            JSON         DEFAULT NULL,
    `created_by`               BIGINT       DEFAULT NULL,
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_be_event_key`   (`event_key`),
    INDEX      `idx_be_type`       (`type`),
    INDEX      `idx_be_status`     (`status`),
    INDEX      `idx_be_start_at`   (`start_at`),
    INDEX      `idx_be_end_at`     (`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星流放送事件表';

-- ============================================================
-- 25. 星流放送贡献表 (broadcast_contributions)
-- ============================================================
CREATE TABLE IF NOT EXISTS `broadcast_contributions` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `event_key`            VARCHAR(100) NOT NULL,
    `player_id`            BIGINT      NOT NULL,
    `contribution_value`   INT         NOT NULL DEFAULT 0,
    `contribution_type`    VARCHAR(50) DEFAULT NULL,
    `reward_claimed`       TINYINT     NOT NULL DEFAULT 0,
    `last_contributed_at`  DATETIME    DEFAULT NULL,
    `metadata_json`        JSON        DEFAULT NULL,
    `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_bc_event_player`   (`event_key`, `player_id`),
    INDEX      `idx_bc_event_key`     (`event_key`),
    INDEX      `idx_bc_player_id`     (`player_id`),
    INDEX      `idx_bc_contribution`  (`contribution_value`),
    INDEX      `idx_bc_claimed`       (`reward_claimed`),

    CONSTRAINT `fk_bc_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星流放送贡献表';

-- ============================================================
-- 26. 世界状态表 (world_states)
-- ============================================================
CREATE TABLE IF NOT EXISTS `world_states` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `state_key`             VARCHAR(100) NOT NULL,
    `name`                  VARCHAR(100) NOT NULL,
    `description`           TEXT         DEFAULT NULL,
    `value_json`            JSON         DEFAULT NULL,
    `level`                 INT          NOT NULL DEFAULT 0,
    `status`                VARCHAR(30)  NOT NULL DEFAULT 'normal',
    `updated_by_event_key`  VARCHAR(100) DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ws_state_key` (`state_key`),
    INDEX      `idx_ws_status`   (`status`),
    INDEX      `idx_ws_level`    (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界状态表';

-- ============================================================
-- Phase 7 (broadcast + worldline) 初始化完成

-- ============================================================
-- 27. 聊天消息表 (chat_messages)
-- Phase 8A: WebSocket + 在线状态 + 聊天系统
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `channel`           VARCHAR(50)  NOT NULL DEFAULT 'world'      COMMENT 'world / system',
    `sender_player_id`  BIGINT       NULL,
    `sender_name`       VARCHAR(100) NULL,
    `message_type`      VARCHAR(50)  NOT NULL DEFAULT 'player'     COMMENT 'player / system / broadcast / worldline',
    `content`           TEXT         NOT NULL,
    `metadata_json`     JSON         NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_chat_channel`    (`channel`),
    INDEX `idx_chat_sender`     (`sender_player_id`),
    INDEX `idx_chat_type`       (`message_type`),
    INDEX `idx_chat_created`    (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ============================================================
-- Phase 8A (WebSocket + chat + online) 初始化完成
-- ============================================================

-- ============================================================
-- 28. 好友关系表 (friends)
-- Phase 8B: 好友系统（双向关系，逻辑删除）
-- ============================================================
CREATE TABLE IF NOT EXISTS `friends` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,
    `player_id`     BIGINT   NOT NULL,
    `friend_id`     BIGINT   NOT NULL,
    `deleted`       TINYINT  NOT NULL DEFAULT 0,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_friends_pair` (`player_id`, `friend_id`),
    INDEX      `idx_friends_player` (`player_id`),
    INDEX      `idx_friends_friend` (`friend_id`),
    INDEX      `idx_friends_deleted` (`deleted`),

    CONSTRAINT `fk_friends_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_friends_friend`
        FOREIGN KEY (`friend_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表（双向）';

-- ============================================================
-- 29. 好友申请表 (friend_requests)
-- ============================================================
CREATE TABLE IF NOT EXISTS `friend_requests` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `from_player_id`  BIGINT      NOT NULL,
    `to_player_id`    BIGINT      NOT NULL,
    `status`          VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending / accepted / rejected',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_fr_pair`       (`from_player_id`, `to_player_id`),
    INDEX      `idx_fr_from`      (`from_player_id`),
    INDEX      `idx_fr_to`        (`to_player_id`),
    INDEX      `idx_fr_status`    (`status`),

    CONSTRAINT `fk_fr_from`
        FOREIGN KEY (`from_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_fr_to`
        FOREIGN KEY (`to_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';

-- ============================================================
-- Phase 8B (friend) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 8C: player_equipment 增加 listed 列
-- ============================================================
-- ALTER TABLE `player_equipment` ADD COLUMN IF NOT EXISTS `listed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已上架交易';

-- ============================================================
-- 30. 交易挂单表 (trade_listings)
-- Phase 8C: 交易市场
-- ============================================================
CREATE TABLE IF NOT EXISTS `trade_listings` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_no`        VARCHAR(100) NOT NULL,
    `seller_player_id`  BIGINT       NOT NULL,
    `listing_type`      VARCHAR(30)  NOT NULL COMMENT 'item / equipment',
    `item_key`          VARCHAR(100) NULL,
    `equipment_key`     VARCHAR(100) NULL,
    `quantity`          INT          NOT NULL DEFAULT 1,
    `unit_price`        INT          NOT NULL,
    `total_price`       INT          NOT NULL,
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'active' COMMENT 'active / sold / cancelled',
    `metadata_json`     JSON         NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `sold_at`           DATETIME     NULL,
    `cancelled_at`      DATETIME     NULL,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tl_listing_no`         (`listing_no`),
    INDEX      `idx_tl_seller`            (`seller_player_id`),
    INDEX      `idx_tl_type`              (`listing_type`),
    INDEX      `idx_tl_status`            (`status`),
    INDEX      `idx_tl_status_type_price` (`status`, `listing_type`, `unit_price`),
    INDEX      `idx_tl_seller_status`     (`seller_player_id`, `status`),
    INDEX      `idx_tl_item_key`          (`item_key`),
    INDEX      `idx_tl_equip_key`         (`equipment_key`),
    INDEX      `idx_tl_created_at`        (`created_at`),

    CONSTRAINT `fk_tl_seller`
        FOREIGN KEY (`seller_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易挂单表';

-- ============================================================
-- 31. 交易记录表 (trade_records)
-- ============================================================
CREATE TABLE IF NOT EXISTS `trade_records` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_no`        VARCHAR(100) NOT NULL,
    `seller_player_id`  BIGINT       NOT NULL,
    `buyer_player_id`   BIGINT       NOT NULL,
    `listing_type`      VARCHAR(30)  NOT NULL COMMENT 'item / equipment',
    `item_key`          VARCHAR(100) NULL,
    `equipment_key`     VARCHAR(100) NULL,
    `quantity`          INT          NOT NULL DEFAULT 1,
    `total_price`       INT          NOT NULL,
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'success',
    `metadata_json`     JSON         NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_tr_listing_no`        (`listing_no`),
    INDEX `idx_tr_seller_created`    (`seller_player_id`, `created_at`),
    INDEX `idx_tr_buyer_created`     (`buyer_player_id`, `created_at`),
    INDEX `idx_tr_type`              (`listing_type`),
    INDEX `idx_tr_created_at`        (`created_at`),

    CONSTRAINT `fk_tr_seller`
        FOREIGN KEY (`seller_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_tr_buyer`
        FOREIGN KEY (`buyer_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易记录表';

-- ============================================================
-- Phase 8C (trade) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 8D: 组队系统
-- ============================================================

-- 32. 队伍表 (parties)
CREATE TABLE IF NOT EXISTS `parties` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `party_no`          VARCHAR(100) NOT NULL,
    `leader_player_id`  BIGINT       NOT NULL,
    `name`              VARCHAR(100) NOT NULL,
    `description`       VARCHAR(255) NULL,
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'active' COMMENT 'active / disbanded',
    `max_members`       INT          NOT NULL DEFAULT 3,
    `target_type`       VARCHAR(50)  NULL COMMENT 'explore / combat / worldBoss',
    `target_key`        VARCHAR(100) NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `disbanded_at`      DATETIME     NULL,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_party_no`          (`party_no`),
    INDEX `idx_p_leader`                 (`leader_player_id`),
    INDEX `idx_p_status`                 (`status`),
    INDEX `idx_p_target_type`            (`target_type`),
    INDEX `idx_p_created_at`             (`created_at`),

    CONSTRAINT `fk_p_leader`
        FOREIGN KEY (`leader_player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队伍表';

-- 33. 队伍成员表 (party_members)
CREATE TABLE IF NOT EXISTS `party_members` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `party_no`          VARCHAR(100) NOT NULL,
    `player_id`         BIGINT       NOT NULL,
    `role`              VARCHAR(30)  NOT NULL DEFAULT 'member' COMMENT 'leader / member',
    `status`            VARCHAR(30)  NOT NULL DEFAULT 'active' COMMENT 'active / left / kicked',
    `joined_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `left_at`           DATETIME     NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_pm_party_player`    (`party_no`, `player_id`),
    INDEX `idx_pm_party_no`               (`party_no`),
    INDEX `idx_pm_player_id`              (`player_id`),
    INDEX `idx_pm_role`                   (`role`),
    INDEX `idx_pm_status`                 (`status`),

    CONSTRAINT `fk_pm_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='队伍成员表';

-- ============================================================
-- Phase 8D (party) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 8E: 调度引擎
-- ============================================================

-- 34. 调度任务日志表 (scheduled_task_logs)
CREATE TABLE IF NOT EXISTS `scheduled_task_logs` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `task_name`       VARCHAR(100) NOT NULL,
    `status`          VARCHAR(30)  NOT NULL DEFAULT 'success' COMMENT 'success / failed / skipped',
    `started_at`      DATETIME     DEFAULT NULL,
    `finished_at`     DATETIME     DEFAULT NULL,
    `duration_ms`     BIGINT       DEFAULT NULL,
    `affected_count`  INT          NOT NULL DEFAULT 0,
    `message`         TEXT         DEFAULT NULL,
    `error_message`   TEXT         DEFAULT NULL,
    `metadata_json`   JSON         DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_stl_task_name`   (`task_name`),
    INDEX `idx_stl_status`      (`status`),
    INDEX `idx_stl_created_at`  (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度任务执行日志表';

-- ============================================================
-- Phase 8E (scheduler) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 8F: 世界Boss系统
-- ============================================================

-- 35. 世界Boss表 (world_bosses)
CREATE TABLE IF NOT EXISTS `world_bosses` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `boss_key`                VARCHAR(100) NOT NULL,
    `boss_no`                 VARCHAR(100) NOT NULL,
    `name`                    VARCHAR(100) NOT NULL,
    `description`             TEXT         DEFAULT NULL,
    `status`                  VARCHAR(30)  NOT NULL DEFAULT 'draft' COMMENT 'draft / active / killed / expired / settled / cancelled',
    `level`                   INT          NOT NULL DEFAULT 1,
    `max_hp`                  BIGINT       NOT NULL,
    `current_hp`              BIGINT       NOT NULL,
    `attack`                  INT          NOT NULL DEFAULT 10,
    `defense`                 INT          NOT NULL DEFAULT 5,
    `speed`                   INT          NOT NULL DEFAULT 10,
    `start_at`                DATETIME     DEFAULT NULL,
    `end_at`                  DATETIME     DEFAULT NULL,
    `killed_at`               DATETIME     DEFAULT NULL,
    `rewards_json`            JSON         DEFAULT NULL COMMENT '参与奖励',
    `rank_rewards_json`       JSON         DEFAULT NULL COMMENT '排名奖励',
    `worldline_effects_json`  JSON         DEFAULT NULL COMMENT '世界线效果',
    `metadata_json`           JSON         DEFAULT NULL,
    `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_wb_boss_no`     (`boss_no`),
    INDEX `idx_wb_boss_key`           (`boss_key`),
    INDEX `idx_wb_status`             (`status`),
    INDEX `idx_wb_start_at`           (`start_at`),
    INDEX `idx_wb_end_at`             (`end_at`),
    INDEX `idx_wb_killed_at`          (`killed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界Boss表';

-- 36. 世界Boss参与表 (world_boss_participation)
CREATE TABLE IF NOT EXISTS `world_boss_participation` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `boss_no`           VARCHAR(100) NOT NULL,
    `player_id`         BIGINT       NOT NULL,
    `party_no`          VARCHAR(100) DEFAULT NULL,
    `damage`            BIGINT       NOT NULL DEFAULT 0,
    `attack_count`      INT          NOT NULL DEFAULT 0,
    `last_attack_at`    DATETIME     DEFAULT NULL,
    `reward_claimed`    TINYINT      NOT NULL DEFAULT 0,
    `rank_no`           INT          DEFAULT NULL,
    `metadata_json`     JSON         DEFAULT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_wbp_boss_player`  (`boss_no`, `player_id`),
    INDEX `idx_wbp_boss_no`             (`boss_no`),
    INDEX `idx_wbp_player_id`           (`player_id`),
    INDEX `idx_wbp_party_no`            (`party_no`),
    INDEX `idx_wbp_damage`              (`damage`),
    INDEX `idx_wbp_reward_claimed`      (`reward_claimed`),
    INDEX `idx_wbp_last_attack_at`      (`last_attack_at`),

    CONSTRAINT `fk_wbp_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='世界Boss参与记录表';

-- ============================================================
-- Phase 8F (worldBoss) 初始化完成
-- ============================================================

-- 37. 星座阵营表 (constellation_factions)
CREATE TABLE IF NOT EXISTS `constellation_factions` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `faction_key`          VARCHAR(100) NOT NULL,
    `name`                 VARCHAR(100) NOT NULL,
    `constellation_name`   VARCHAR(100) DEFAULT NULL,
    `description`          TEXT         DEFAULT NULL,
    `alignment`            VARCHAR(50)  DEFAULT NULL COMMENT 'order/chaos/neutral/starstream/abyss',
    `ideology`             VARCHAR(100) DEFAULT NULL,
    `level`                INT          NOT NULL DEFAULT 1,
    `exp`                  BIGINT       NOT NULL DEFAULT 0,
    `member_count`         INT          NOT NULL DEFAULT 0,
    `total_contribution`   BIGINT       NOT NULL DEFAULT 0,
    `buffs_json`           JSON         DEFAULT NULL,
    `unlocks_json`         JSON         DEFAULT NULL,
    `metadata_json`        JSON         DEFAULT NULL,
    `enabled`              TINYINT      NOT NULL DEFAULT 1,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_cf_faction_key`   (`faction_key`),
    INDEX `idx_cf_enabled`              (`enabled`),
    INDEX `idx_cf_level`                (`level`),
    INDEX `idx_cf_total_contribution`   (`total_contribution`),
    INDEX `idx_cf_member_count`         (`member_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星座阵营表';

-- 38. 玩家阵营归属表 (player_factions)
CREATE TABLE IF NOT EXISTS `player_factions` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`           BIGINT       NOT NULL,
    `faction_key`         VARCHAR(100) NOT NULL,
    `role`                VARCHAR(30)  NOT NULL DEFAULT 'member' COMMENT 'member/elite/leader',
    `reputation`          BIGINT       NOT NULL DEFAULT 0,
    `contribution_total`  BIGINT       NOT NULL DEFAULT 0,
    `joined_at`           DATETIME     NOT NULL,
    `left_at`             DATETIME     DEFAULT NULL,
    `status`              VARCHAR(30)  NOT NULL DEFAULT 'active' COMMENT 'active/left/banned',
    `metadata_json`       JSON         DEFAULT NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_pf_player_id`    (`player_id`),
    INDEX `idx_pf_faction_key`         (`faction_key`),
    INDEX `idx_pf_role`                (`role`),
    INDEX `idx_pf_reputation`          (`reputation`),
    INDEX `idx_pf_contribution_total`  (`contribution_total`),
    INDEX `idx_pf_status`              (`status`),

    CONSTRAINT `fk_pf_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家阵营归属表';

-- 39. 阵营贡献明细表 (faction_contributions)
CREATE TABLE IF NOT EXISTS `faction_contributions` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `faction_key`       VARCHAR(100) NOT NULL,
    `player_id`         BIGINT       NOT NULL,
    `contribution_type` VARCHAR(50)  NOT NULL COMMENT 'manual/explore/combat/pk/worldBoss/broadcast',
    `value`             BIGINT       NOT NULL DEFAULT 0,
    `source`            VARCHAR(100) DEFAULT NULL,
    `related_id`        VARCHAR(100) DEFAULT NULL,
    `metadata_json`     JSON         DEFAULT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_fc_faction_key`       (`faction_key`),
    INDEX `idx_fc_player_id`         (`player_id`),
    INDEX `idx_fc_contribution_type` (`contribution_type`),
    INDEX `idx_fc_source`            (`source`),
    INDEX `idx_fc_created_at`        (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阵营贡献明细表';

-- 40. 阵营战表 (faction_wars) — 基础占位
CREATE TABLE IF NOT EXISTS `faction_wars` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `war_no`                  VARCHAR(100) NOT NULL,
    `attacker_faction_key`    VARCHAR(100) NOT NULL,
    `defender_faction_key`    VARCHAR(100) NOT NULL,
    `status`                  VARCHAR(30)  NOT NULL DEFAULT 'draft' COMMENT 'draft/active/finished/cancelled',
    `start_at`                DATETIME     DEFAULT NULL,
    `end_at`                  DATETIME     DEFAULT NULL,
    `attacker_score`          BIGINT       NOT NULL DEFAULT 0,
    `defender_score`          BIGINT       NOT NULL DEFAULT 0,
    `winner_faction_key`      VARCHAR(100) DEFAULT NULL,
    `rewards_json`            JSON         DEFAULT NULL,
    `metadata_json`           JSON         DEFAULT NULL,
    `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_fw_war_no`            (`war_no`),
    INDEX `idx_fw_attacker_faction_key`     (`attacker_faction_key`),
    INDEX `idx_fw_defender_faction_key`     (`defender_faction_key`),
    INDEX `idx_fw_status`                   (`status`),
    INDEX `idx_fw_start_at`                 (`start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阵营战表';

-- 41. 阵营技能定义表 (faction_skills) — 基础占位
CREATE TABLE IF NOT EXISTS `faction_skills` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `faction_key`     VARCHAR(100) NOT NULL,
    `skill_key`       VARCHAR(100) NOT NULL,
    `name`            VARCHAR(100) NOT NULL,
    `description`     TEXT         DEFAULT NULL,
    `unlock_level`    INT          NOT NULL DEFAULT 1,
    `effects_json`    JSON         DEFAULT NULL,
    `cost_json`       JSON         DEFAULT NULL,
    `enabled`         TINYINT      NOT NULL DEFAULT 1,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_fs_faction_skill` (`faction_key`, `skill_key`),
    INDEX `idx_fs_faction_key`          (`faction_key`),
    INDEX `idx_fs_skill_key`            (`skill_key`),
    INDEX `idx_fs_enabled`              (`enabled`),
    INDEX `idx_fs_unlock_level`         (`unlock_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阵营技能定义表';

-- constellation_factions seed data (8 ORV constellations)
INSERT IGNORE INTO `constellation_factions` (`faction_key`, `name`, `constellation_name`, `description`, `alignment`, `ideology`, `level`, `exp`, `member_count`, `total_contribution`, `buffs_json`, `unlocks_json`, `enabled`) VALUES
('nebula_abyss',      '深渊观测所',   '深渊观测所',   '深渊中观测星流的古老组织。成员在暗处守望世界线的流动，洞悉故事背后的真相。攻击加成由每日贡献决定。',            'abyss',   '深渊洞察',     1, 0, 0, 0, '{"atk":20}', '{"skill_1":"abyss_gaze","skill_2":"truth_sight"}', 1),
('nebula_eden',       '伊甸星云',     '伊甸星云',     '追寻星流起源的理想者聚集地。相信在某条世界线中存在完美的故事结局。攻击加成由每日贡献决定。',                  'order',   '均衡守护',     1, 0, 0, 0, '{"atk":20}', '{"skill_1":"eden_light","skill_2":"ancient_might"}', 1),
('nebula_vagrant',    '流浪者星云',   '流浪者星云',   '无拘无束的星流漫游者。不被固定星座束缚，自由穿梭于世界线之间。攻击加成由每日贡献决定。',              'neutral', '自由漫游',     1, 0, 0, 0, '{"atk":20}', '{"skill_1":"vagrant_step","skill_2":"lucky_strike"}', 1),
('nebula_starstream', '星流档案馆',   '星流档案馆',   '记录星流一切故事的古老档案馆。馆员们将无数世界线的故事存档、分类、守护。攻击加成由每日贡献决定。',              'starstream', '知识守护',   1, 0, 0, 0, '{"atk":20}', '{"skill_1":"archive_blessing","skill_2":"watcher_grace"}', 1);

-- ============================================================
-- Phase 8G (faction) 初始化完成
-- ============================================================

-- ============================================================
-- 34. 任务定义表 (quests)
-- 所有任务的静态定义，日常/周常/成就
-- ============================================================
CREATE TABLE IF NOT EXISTS `quests` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `quest_key`         VARCHAR(100) NOT NULL,
    `title`             VARCHAR(200) NOT NULL,
    `description`       TEXT         DEFAULT NULL,
    `quest_type`        VARCHAR(20)  NOT NULL DEFAULT 'daily' COMMENT 'daily / weekly / achievement',
    `category`          VARCHAR(50)  DEFAULT NULL COMMENT 'explore / combat / pk / world_boss / trade / faction / growth',
    `target_type`       VARCHAR(50)  NOT NULL COMMENT 'event 类型键: explore_count, combat_win_count, pk_participate_count, pk_win_count, world_boss_attack_count, trade_buy_count, trade_sell_count, faction_contribution_value, level_reach, avatar_rank_reach',
    `target_value`      INT          NOT NULL DEFAULT 1,
    `conditions_json`   JSON         DEFAULT NULL COMMENT '额外条件 JSON',
    `rewards_json`      JSON         DEFAULT NULL COMMENT '奖励 JSON',
    `reset_cycle`       VARCHAR(20)  DEFAULT NULL COMMENT 'daily / weekly / none',
    `sort_order`        INT          NOT NULL DEFAULT 0,
    `enabled`           TINYINT      NOT NULL DEFAULT 1,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quests_quest_key`  (`quest_key`),
    INDEX      `idx_quests_type`      (`quest_type`),
    INDEX      `idx_quests_category`  (`category`),
    INDEX      `idx_quests_enabled`   (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务定义表';

-- ============================================================
-- 35. 玩家任务实例表 (player_quests)
-- 每个玩家每个周期每条任务一条记录
-- ============================================================
CREATE TABLE IF NOT EXISTS `player_quests` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`         BIGINT       NOT NULL,
    `quest_key`         VARCHAR(100) NOT NULL,
    `quest_type`        VARCHAR(20)  NOT NULL COMMENT 'daily / weekly / achievement',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active / completed / claimed / expired',
    `progress`          INT          NOT NULL DEFAULT 0,
    `target_value`      INT          NOT NULL DEFAULT 1,
    `reward_claimed`    TINYINT      NOT NULL DEFAULT 0,
    `cycle_key`         VARCHAR(50)  NOT NULL DEFAULT 'none' COMMENT 'yyyy-MM-dd / yyyy-Www / none',
    `accepted_at`       DATETIME     DEFAULT NULL,
    `completed_at`      DATETIME     DEFAULT NULL,
    `claimed_at`        DATETIME     DEFAULT NULL,
    `metadata_json`     JSON         DEFAULT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_quest_cycle` (`player_id`, `quest_key`, `cycle_key`),
    INDEX      `idx_pq_player_id`      (`player_id`),
    INDEX      `idx_pq_quest_key`      (`quest_key`),
    INDEX      `idx_pq_status`         (`status`),
    INDEX      `idx_pq_cycle_key`      (`cycle_key`),

    CONSTRAINT `fk_pq_player`
        FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家任务实例表';

-- ============================================================
-- 36. 任务进度日志表 (quest_progress_logs)
-- 审计追踪：记录每次进度推进的delta和来源
-- ============================================================
CREATE TABLE IF NOT EXISTS `quest_progress_logs` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`         BIGINT       NOT NULL,
    `quest_key`         VARCHAR(100) NOT NULL,
    `delta`             INT          NOT NULL DEFAULT 0,
    `progress_before`   INT          NOT NULL DEFAULT 0,
    `progress_after`    INT          NOT NULL DEFAULT 0,
    `source`            VARCHAR(50)  DEFAULT NULL COMMENT 'explore / combat / pk / worldBoss / trade / faction / growth',
    `related_id`        VARCHAR(100) DEFAULT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    INDEX `idx_qpl_player_id`     (`player_id`),
    INDEX `idx_qpl_quest_key`     (`quest_key`),
    INDEX `idx_qpl_source`        (`source`),
    INDEX `idx_qpl_created`       (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务进度日志表';

-- ============================================================
-- Phase 8H seed: 7 quest definitions
-- ============================================================
INSERT IGNORE INTO `quests` (`quest_key`, `title`, `description`, `quest_type`, `category`, `target_type`, `target_value`, `rewards_json`, `reset_cycle`, `sort_order`) VALUES
('daily_explore_3',    '探索世界',     '进行3次探索',           'daily',  'explore',    'explore_count',          3,  '{"coins":50,"exp":20}',                'daily',  1),
('daily_combat_3',     '战斗修行',     '赢得3场战斗',           'daily',  'combat',     'combat_win_count',        3,  '{"coins":60,"exp":25}',                'daily',  2),
('daily_trade_1',      '市场交易',     '完成1次购买',           'daily',  'trade',      'trade_buy_count',         1,  '{"coins":30,"exp":10}',                'daily',  3),
('weekly_pk_5',        '擂台争霸',     '参与5次PK',             'weekly', 'pk',         'pk_participate_count',    5,  '{"coins":200,"exp":80,"storyFragments":3}',  'weekly', 1),
('weekly_boss_3',      '世界Boss挑战', '参与3次世界Boss攻击',   'weekly', 'world_boss', 'world_boss_attack_count', 3,  '{"coins":250,"exp":100,"storyFragments":5}', 'weekly', 2),
('achv_level_5',       '初露锋芒',     '达到5级',               'achievement', 'growth', 'level_reach',         5,  '{"coins":500,"exp":150,"titleKey":"growth_novice"}',        'none',   1),
('achv_rank_C',        '位阶晋升',     '达到Avatar Rank C',     'achievement', 'growth', 'avatar_rank_reach',   1,  '{"coins":1000,"exp":300,"storyFragments":10}',              'none',   2);

-- ============================================================
-- Phase 8H (quest) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 5D: Synthesis — 物品合成系统
-- ============================================================
CREATE TABLE IF NOT EXISTS `synthesis_recipes` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `recipe_key`       VARCHAR(100) NOT NULL,
    `name`             VARCHAR(200) DEFAULT NULL,
    `description`      TEXT         DEFAULT NULL,
    `result_item_key`  VARCHAR(100) NOT NULL,
    `result_quantity`  INT          NOT NULL DEFAULT 1,
    `ingredients_json` JSON         DEFAULT NULL,
    `cost_coins`       INT          NOT NULL DEFAULT 0,
    `enabled`          TINYINT      NOT NULL DEFAULT 1,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sr_recipe_key` (`recipe_key`),
    INDEX `idx_sr_enabled` (`enabled`),
    INDEX `idx_sr_result_item_key` (`result_item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合成配方表';

-- Seed: 基础合成配方
INSERT IGNORE INTO `synthesis_recipes` (`recipe_key`, `name`, `description`, `result_item_key`, `result_quantity`, `ingredients_json`, `cost_coins`, `enabled`) VALUES
('craft_medium_hp', '合成中瓶生命药剂', '用2个小瓶生命药剂合成1个中瓶', 'medium_hp_potion', 1, '{"small_hp_potion": 2}', 30, 1),
('craft_large_hp', '合成大瓶生命药剂', '用3个中瓶生命药剂合成1个大瓶', 'large_hp_potion', 1, '{"medium_hp_potion": 3}', 80, 1),
('craft_stamina_pill', '合成活力丹', '用1瓶圣水+1个魔物骨骸合成活力丹', 'stamina_pill', 1, '{"purification_water": 1, "monster_bone": 1}', 50, 1),
('craft_story_scroll', '合成故事卷轴', '用5个故事碎片合成1个故事卷轴', 'story_scroll', 1, '{"story_fragment": 5}', 100, 1),
('craft_abyss_elixir', '合成深渊秘药', '用深渊碎片+故事卷轴合成', 'abyss_elixir', 1, '{"abyss_shard": 1, "story_scroll": 1}', 200, 1),
('craft_hunter_blade', '合成猎人短刃', '用生锈短刀+猎人的戒指合成', 'hunter_blade', 1, '{"rusty_dagger": 1, "hunters_ring": 1}', 150, 1);

-- ============================================================
-- Phase 5D (synthesis) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 9D: Narrative — 碎片化叙事系统
-- ============================================================

CREATE TABLE IF NOT EXISTS `item_memories` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `item_key`      VARCHAR(100) NOT NULL,
    `memory_text`   TEXT         NOT NULL,
    `narrator`      VARCHAR(100) NOT NULL DEFAULT 'system',
    PRIMARY KEY (`id`),
    INDEX `idx_im_item_key` (`item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物品记忆表';

CREATE TABLE IF NOT EXISTS `location_echoes` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `location_key`  VARCHAR(100) NOT NULL,
    `echo_text`     TEXT         NOT NULL,
    `narrator`      VARCHAR(100) NOT NULL DEFAULT 'location',
    `weight`        DOUBLE       NOT NULL DEFAULT 1.0,
    PRIMARY KEY (`id`),
    INDEX `idx_le_location_key` (`location_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地点回响表';

CREATE TABLE IF NOT EXISTS `npc_ghosts` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `ghost_key`           VARCHAR(100) NOT NULL,
    `name`                VARCHAR(200) NOT NULL,
    `description`         TEXT         NOT NULL,
    `dialogue_tree_json`  JSON         NOT NULL,
    `location_keys_json`  JSON         NOT NULL,
    `encounter_weight`    DOUBLE       NOT NULL DEFAULT 0.05,
    `is_unique`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ng_ghost_key` (`ghost_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='NPC残影表';

CREATE TABLE IF NOT EXISTS `player_npc_encounters` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`   BIGINT       NOT NULL,
    `ghost_key`   VARCHAR(100) NOT NULL,
    `choice_made` VARCHAR(500) DEFAULT NULL,
    `outcome`     TEXT         DEFAULT NULL,
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_pne_player_id`  (`player_id`),
    INDEX `idx_pne_ghost_key`  (`ghost_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家NPC遭遇记录表';

-- Item memory seed data (10 items)
INSERT IGNORE INTO `item_memories` (`item_key`, `memory_text`, `narrator`) VALUES
('rusty_dagger', '这把生锈的短刀上刻着微小的字迹：「给最爱的儿子——金独子」。刀柄磨损严重，似乎被握过无数次。', '物品记忆'),
('station_guard_coat', '外套内侧口袋有一张褪色的车票，目的地是"终点站"。检票员的印章已经模糊不清。这件外套曾属于一位守卫地铁最后防线的年轻士兵。', '物品记忆'),
('old_reader_badge', '这本应是一枚普通的读者证。但它曾被一个人带到了世界的尽头，上面的编号409已经无法在系统中查到对应的借阅记录了。', '物品记忆'),
('small_hp_potion', '廉价的再生药剂。瓶身上贴着"星流制药"的标签。有人说过："只要还有一口气，就能活下来——这不是祝福，是诅咒。"', '物品记忆'),
('story_fragment', '一段被撕裂的故事残片。你可以隐约听到一个声音在低声诵读："这是全知读者视角所说的...这个世界只有三种存活方式。而我知道第四种。"', '碎片回响'),
('monster_bone', '魔物的骨骸。早在"场景"开始之前，这个世界就充满了这些异形。它们不是入侵者——它们本就属于这里。是故事让它们变得可怕。', '学者笔记'),
('hunters_ring', '这枚戒指属于"猎人"刘众赫的第41次轮回。在无数次轮回中，它吸收了每一任持有者的悔恨与执念。佩戴时会感到一阵刺骨的寒意。', '轮回回响'),
('fallen_star_powder', '背后的星座陨落时留下的粉末。在星流观测站中，每天都有星座无声熄灭。它们不是神——它们只是比我们更早开始阅读这个故事的读者。', '星流日志'),
('abyss_shard', '深渊的碎片。透过它看到的不是黑暗，而是一个倒悬的世界——一个与星流平行的、由被遗忘的故事构成的深渊。那里沉睡着不存在的结局。', '深渊低语'),
('purification_water', '圣水散发着微弱的金色光芒。传说这是从最初的故事之泉中取出的水，能洗涤"场景"中的污染。但有人质疑：凭什么由别人来决定什么是污染？', '圣殿记录');

-- Location echo seed data (ruined_station)
INSERT IGNORE INTO `location_echoes` (`location_key`, `echo_text`, `narrator`, `weight`) VALUES
('ruined_station', '断壁残垣之间，你听到列车进站的播报声——但那铁轨已断，列车永不会到来。这里是故事的开端，也是无数条世界线的起点。', '地点回响', 3),
('ruined_station', '一个模糊的身影坐在断裂的长椅上，手里捧着一本厚厚的书。当你走近时，他消失了。地上只留下一页撕下的稿纸。', '残影', 4),
('ruined_station', '铁轨的尽头有一本被遗弃的日记。前三页写满了计划——"第二天去便利店"、"周三交房租"、"周末带妈妈去医院"——字迹在第四页突然中断。世界并非在喧哗中终结——它在一个普通的星期二变成了"场景"。', '遗留日记', 3),
('ruined_station', '长椅下方用金属碎片刻着一行字："第847次，我还会再来。"字迹很新——但长椅的其他部分早已锈迹斑斑。时间在此处的流动从来不是线性的。', '残影', 2),

('underground_city', '这座地下城市曾是人类最后的庇护所。墙壁上的涂鸦记录着"场景"降临那天的恐慌。有人写下了希望，有人写下了遗言，还有人画了一只微笑的猫。', '城市记忆', 3),
('underground_city', '空气中飘过一段音乐——巴赫的G小调赋格。没人知道是谁在演奏，但在地底的黑暗中，这琴声从未停歇。', '回响', 3),
('underground_city', '地下广场中央有一座用废铁焊成的雕像——不是英雄，是一个捧书的女人。底座上刻着："给所有母亲。她们在黑暗中读故事给孩子们听，让他们知道——即使在最深的地底，也会有人翻页。"', '城市雕像', 4),
('underground_city', '墙壁上的涂鸦在重新排列。每盏灯管闪烁一次，涂鸦就变化一次——第一秒是求救信号，第二秒是末日倒计时，第三秒开始排列成一首诗："我生于此 / 死于彼 / 书页之间 / 你在哪里。"', '墙语', 3),
('underground_city', '地下深处的一间教室里，黑板上还留着粉笔字。不是逃生指南——是一道数学题。最后一行写着："如果X=世界线数量，Y=存活人数，Z=时间——求X、Y、Z的最大公约数。提示：答案是1。只有一项是绝对的。"', '废弃教室', 3),

('sky_castle', '高耸入云的古堡中，一个声音反复呢喃着："王座之上，读者即王。但王也会翻页，也会合上书...然后一切化为虚无。"', '古堡低语', 3),
('sky_castle', '走廊的镜子里，你看到的不是自己的倒影，而是一个白发男子。他用口型说了一句话："继续读下去。"', '镜中映像', 4),
('sky_castle', '城堡最高塔楼的窗台上，一根燃烧的羽毛仍然散发着温暖。它不是被遗弃的——是一封正在等待回复的信。羽毛根部系着一根金色的线，在风中轻轻颤动，指向某个遥远的方向。', '天使的信', 3),
('sky_castle', '城堡中最低矮的一扇门——需要弯腰才能通过——门后是一个孩子的房间。墙上贴着《SSSSS级无限回归者》的漫画页。床头的电子钟永远停留在3:12 AM。窗外不是夜空，而是一页接一页无声翻动的书页。', '孩子房间', 4),
('sky_castle', '女墙上的战旗在风中猎猎作响。每一面旗上都绣着一个星座的标记——但这些星座已经全部陨落了。它们的名字早已被遗忘，只剩下这些褪色的旗帜仍在固执地飘扬。', '陨落星座旗', 2),

('creature_forest', '密林深处的古树上有刻痕。那不是动物爪印，而是一行韩文："独子啊，妈妈在这个世界线终于找到你了。别停下，继续读。"', '树纹', 4),
('creature_forest', '一只发光的蝴蝶停在你手上。它的翅膀上闪烁着闪烁的星座图案。有人说过：蝴蝶效应可以改变世界线——但代价是一个人的整个人生。', '星流蝴蝶', 3),
('creature_forest', '古树渗出的琥珀中封存着一片纸张——不是树叶，而是一页撕下的书页。书页上的字迹清晰可辨："第479章——读者意识到了。他不是在阅读故事——他正在成为故事本身。"', '琥珀书页', 3),
('creature_forest', '一只双目失明的老狼盘踞在祭坛般的石堆上。它不能说话，但当你走近时，它抬头朝月亮的残骸嚎叫——那嚎声在空气中震出了一串可见的文字："我们也是读者。我们读了月亮三百万年。"', '盲眼老狼', 4),
('creature_forest', '密林深处有一棵倒下的世界树——它的根系仍在发光，每一根悬空的根须上都挂着一个小小的书签。每个书签上用不同的语言写着同一句话："活下去。"', '世界树残骸', 3),

('abyss_rift', '深渊裂缝的边缘，时间流动的速度与外部不同。你看到一个人影在这里站了千年，手里捧着一本被翻阅过无数遍的旧书。每一次翻到最后一页，他就重新开始。', '深渊观测', 4),
('abyss_rift', '裂缝深处传来孩童的笑声，然后是哭声，然后是沉默。传说深渊底部是"故事的墓地"——所有未被写完的、被遗忘的故事都在那里安息。', '深渊回响', 3),
('abyss_rift', '你发现了一道裂隙中的裂隙——比深渊更深的裂缝。从那里传来的不是声音，而是静默——一种古老的、含意深远的静默，像合上的最后一页书，像醒来前的最后一个梦。', '裂隙观察', 3),
('abyss_rift', '一个悬浮的瓶子在深渊边缘缓慢飘荡——漂流瓶。瓶内是一卷写满了字的纸条。开头第一句话是："我不知道这封信能不能飘到任何地方——甚至不知道飘出去的方向是不是「未来」。但如果你读到了——告诉你一个秘密：这个世界是一个梦。但也正因如此——你可以让它变成任何样子。"', '深渊漂流瓶', 5),
('abyss_rift', '深渊的某处——你无法确定方位——传来了打字机的声音。那不是机械的噪音——那是故事被创造出来的声音。每当击键声响起，深渊中就亮起一颗新的星辰；每当删除键被按下，一颗星星黯然熄灭。', '创造与毁灭', 4),

('broken_market', '商场广播中传来一段很久以前的促销广告："欢迎光临！今日特价——一切物品，全部免费！请保持冷静，有序取用——"广播戛然而止，然后从头循环。已经循环了多少年？没有人知道。', '商场广播', 3),
('broken_market', '在翻倒的售书机下方，你找到了一本被压皱的《SSSSS级无限回归者》第27卷。封面上有一张贴纸："全知读者视角网编辑部 推荐阅读。"贴纸下的书页已被翻得起了毛边。', '售书机', 2),

('silent_library', '图书馆深处的档案室中，所有文件的标题都在持续变化——"人物档案：金独子"变成了"人物档案：第2049号读者"，然后又变成了"人物档案：███"。描述栏里的文字正在被删除，一个字一个字地消失。', '档案记录', 3),
('silent_library', '一本无人翻阅的书自动打开了。书页上只印着一行字："你正在读这句话。而此刻——有人也在读着你。"当你试图合上书时，封面上浮现出了你的名字。', '无尽之书', 4),

('collapsed_bridge', '在黑水中，你看到了一条逆流而上的鱼——一条发光的金色锦鲤。它跃出水面时，身上的鳞片拼成了一句话："终点不是终点——是另一个起点。"', '黑水映像', 3),
('collapsed_bridge', '桥梁断裂处残留着一截完好的栏杆，上面刻满了韩文名字——不是签名，是一份名单。最下面用小字写着："如果有人在读这个，记住他们。只要被记住——就还没有真正死亡。"', '纪念碑', 3),

('black_channel_zone', '导播室的控制台上，一盏红灯疯狂闪烁。屏幕左上角显示着——"观众数：1"。唯一的观众叫"最古之梦"。他发送了一条弹幕："别停下——第2049章以后的部分我还没有读到。"', '直播系统', 4),
('black_channel_zone', '一个废弃的星座频道仍在播放——但星座本身已经陨落了。画面上只有雪花点和一个不断重复的闭台画面："本频道即将关闭。感谢三千年的陪伴。再见。"然后从头开始重复。', '关闭频道', 2),

('final_scenario_gate', '门扉上的文字不是刻上去的——它们在流动，在不断被重写。你看到一条关于你自己的描述："第██章——选择。读者站在门前，面对——"后面的文字在不断变化，每一条都是不同的结局。', '命运扉页', 4),
('final_scenario_gate', '门的一角贴着一张泛黄的便利贴——在这个庄严的终章之门上，它显得突兀而温暖。便利贴上用圆珠笔写着："不管选了哪扇门——推就是了。别回头看。"', '匿名留言', 2);

-- ============================================================
-- Phase 9D (narrative) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 9E: Help Bounty — 悬赏求助系统
-- ============================================================

CREATE TABLE IF NOT EXISTS `help_bounties` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`              BIGINT       NOT NULL,
    `monster_key`           VARCHAR(100) NOT NULL,
    `location_key`          VARCHAR(100) NOT NULL DEFAULT '',
    `monster_name`          VARCHAR(200) NOT NULL DEFAULT '',
    `share_percent`         INT          NOT NULL DEFAULT 50,
    `status`                VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending / accepted / resolved / expired / cancelled',
    `helper_id`             BIGINT       DEFAULT NULL,
    `bounty_rewards_json`   JSON         DEFAULT NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `resolved_at`           DATETIME     DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_hb_status`   (`status`),
    INDEX `idx_hb_owner`    (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='悬赏求助表';

-- daily_help_count / daily_assist_count / help_date columns added to players table
-- using stored procedure to safely add columns if not exists
DROP PROCEDURE IF EXISTS add_help_cols;
DELIMITER //
CREATE PROCEDURE add_help_cols()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'reader_game' AND TABLE_NAME = 'players' AND COLUMN_NAME = 'daily_help_count') THEN
        ALTER TABLE `players` ADD COLUMN `daily_help_count` INT NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'reader_game' AND TABLE_NAME = 'players' AND COLUMN_NAME = 'daily_assist_count') THEN
        ALTER TABLE `players` ADD COLUMN `daily_assist_count` INT NOT NULL DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'reader_game' AND TABLE_NAME = 'players' AND COLUMN_NAME = 'help_date') THEN
        ALTER TABLE `players` ADD COLUMN `help_date` VARCHAR(20) NOT NULL DEFAULT '';
    END IF;
END //
DELIMITER ;
CALL add_help_cols();
DROP PROCEDURE IF EXISTS add_help_cols;

-- ============================================================
-- Phase 9E (help bounty) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 9F: Ending System — 结局系统
-- ============================================================

CREATE TABLE IF NOT EXISTS `endings` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `ending_key`       VARCHAR(100) NOT NULL,
    `name`             VARCHAR(200) NOT NULL,
    `description`      TEXT         NOT NULL,
    `priority`         INT          NOT NULL DEFAULT 1,
    `conditions_json`  JSON         DEFAULT NULL,
    `is_hidden`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_endings_key` (`ending_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结局定义表';

-- 7 seed endings (ORV adaptation)
INSERT IGNORE INTO `endings` (`ending_key`, `name`, `description`, `priority`, `conditions_json`, `is_hidden`) VALUES
('end_survivor', '普通幸存者结局',
 '你活了下来。不是作为魔王，不是作为救世主，不是作为任何特殊的存在——仅仅是作为一个幸存者。你在废墟中找到了其他活下来的人，一起在残破的首尔重建家园。夜晚，你偶尔仰望星空，知道那些星座仍在注视着什么。但你已经不再是他们的焦点。活着本身，就是最好的剧本。也许有一天，会有另一个读者翻开你的故事。',
 1, '{"flags":{"entered_castle":true}}', 0),

('end_collapse', '世界线崩坏结局',
 '你的行动撕裂了星之流的剧本。愤怒的鬼怪们试图关闭你的频道，但太迟了——整个频道系统开始崩溃。剧本叠加在剧本之上，时间线交织在一起。你看到了刘众赫的无数次轮回同时在你眼前展开。然后——一切归于空白。星之流安静了下来。你听到了一个声音，从很远很远的地方传来：「也许下一个读者会做出不同的选择。」你闭上了眼睛。这本书从未被合上。它只是在等待下一个人来翻开它。',
 2, '{"permanent_flags":{"绝对王座拥有者":true},"stats":{"cruelty":{"min":4}}}', 0),

('end_lonely_reader', '孤独读者结局',
 '你选择了继续读下去——不顾一切地，孤注一掷地。你拒绝了魔王之路，拒绝了救世主之名，拒绝了与刘众赫并肩的机会。你独自走向那道墙。墙上写着每一个人的故事——还有你自己的。你看到了第3150章。那不是结局。那是一面镜子。镜子里的人不是金独子——是正在读这段文字的你。你终于懂得了：你不是故事中的角色，但你是故事所以存在的理由。你在图书馆的最深处坐了下来，拿起一本空白的书。是时候写下你自己的故事了。',
 3, '{"titles":["title_lonely_reader"],"stats":{"insight":{"min":10}}}', 0),

('end_demon_king', '魔王结局',
 '你成为了新的魔王。在星之流所有频道的注视下，你走上了那座由黑暗力量构成的高塔。但你坐下的时候，不是为了统治——而是为了守护。魔王的力量从你身上流过，每一道暗影都在你的意志下化为壁垒。刘众赫站在你身后，一言不发。韩秀英在笔记本上飞快地写着什么——「他原本只是一个读者。但他选择了成为故事的一部分。不是为了成为主角——而是为了改变主角无法改变的东西。」星座频道一个接一个地安静下来。不是因为恐惧，而是因为敬畏。你是魔王——不是毁灭者，而是守护者。',
 4, '{"titles":["title_demon_king"],"flags":{"merged_dream":true}}', 0),

('end_savior', '救世主结局',
 '你没有成为魔王，没有走孤独的读者之路，也没有与主角并肩作战。你选择了最不可能的路——唤醒那个最古老的梦。你走到了做梦的少年面前。那个在病床上躺了数千年的少年。你让他看到了另一种可能性。不是一个结局，不是一个剧本——而是成千上万个故事，每一个都因为读者的选择而不同。「醒来吧，」你说，「你的梦已经够长了。」少年睁开了眼睛。星之流崩塌了。化作星座们化为流星坠入天际。而你回到了你所爱的人身边。刘众赫放下了他的剑。「你是怎么做到的？」他问。「我没有做到什么，」你说，「我只是读到了终点，然后翻了一页。」',
 5, '{"flags":{"woke_dreamer":true},"stats":{"bond":{"min":6}}}', 0),

('end_companion', '永恒回歸结局',
 '你和刘众赫一起走到了最后。不是作为追随者和主角，而是作为同伴。在最后的墙面前，你做了一个选择：不是拯救世界，而是拯救彼此。刘众赫看着你。「活了这么多次轮回，我从来没有见过像你这样的人。」「因为我读了你的故事，」你说，「3149章。每一章。比任何人都了解你。」他沉默了很久。然后他做了一个在无数次轮回中从未做过的事——他笑了。不是苦笑，不是冷笑——是真的笑了。星之流崩塌了。但你们还在。一个读者和他的主角，走在回家的路上。也许这就是结局该有的样子。不是惊天动地的，不是拯救一切的。只是两个人，和一个没有尽头的故事。',
 5, '{"titles":["title_story_bearer"],"flags":{"accepted_fate":true},"stats":{"bond":{"min":8}}}', 0),

('end_true', '真结局：全知读者视角',
 '你翻开了第3150章。内容不是文字。不是一个结局。甚至不是一个故事。它是一面镜子——干净、明亮、完整。镜子里照出的不是你。镜子里照出的是——正在阅读这段文字的「你」本人。你终于理解了。「全知读者视角」从来不是金独子的能力。它是你的能力。你抬起头。韩秀英、刘众赫、金独子——他们都在看着你。跨越了故事与现实的边界，他们朝你微笑。韩秀英的笔停了。刘众赫的剑放下了。金独子向你点了点头。然后你翻到了最后一页。那页上只有一行字：「谢谢你的阅读。现在，请写下属于你自己的故事。」\n\n你深吸一口气。窗外是阳光。地铁到站了。但你没有合上书。因为你终于懂了——故事没有尽头。只要你还在阅读，它就一直活着。',
 10, '{"titles":["title_true_reader","title_final_chapter_reader"],"permanent_flags":{"终章读者":true},"stats":{"insight":{"min":12}}}', 1);

-- ============================================================
-- Phase 9F (ending) 初始化完成
-- ============================================================

-- ============================================================
-- Phase 10: 探索系统增强 — 故事日志 + choices_json 迁移
-- ============================================================

-- 10a. 玩家故事日志表
CREATE TABLE IF NOT EXISTS `player_story_logs` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `player_id`        BIGINT       NOT NULL,
    `event_key`        VARCHAR(100) NOT NULL,
    `location_key`     VARCHAR(100) NOT NULL,
    `location_name`    VARCHAR(200) DEFAULT NULL,
    `event_name`       VARCHAR(200) DEFAULT NULL,
    `choice_index`     INT          DEFAULT NULL COMMENT '玩家选择的选项索引(0-based)',
    `choice_label`     VARCHAR(500) DEFAULT NULL COMMENT '选项文本',
    `consequence_text` TEXT         DEFAULT NULL COMMENT '后果描述',
    `rewards_snapshot` JSON         DEFAULT NULL COMMENT '奖励快照',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_psl_player`    (`player_id`),
    INDEX `idx_psl_location`  (`location_key`),
    INDEX `idx_psl_created`   (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家故事日志表';

-- 10b. exploration_events 加 choices_json 字段（已存在的 DB 迁移用）
DROP PROCEDURE IF EXISTS add_choices_json_col;
DELIMITER //
CREATE PROCEDURE add_choices_json_col()
BEGIN
    IF NOT EXISTS (SELECT * FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'reader_game' AND TABLE_NAME = 'exploration_events' AND COLUMN_NAME = 'choices_json') THEN
        ALTER TABLE `exploration_events` ADD COLUMN `choices_json` JSON DEFAULT NULL COMMENT '分支选项 JSON' AFTER `progress_effects_json`;
    END IF;
END //
DELIMITER ;
CALL add_choices_json_col();
DROP PROCEDURE IF EXISTS add_choices_json_col;

-- ============================================================
-- Phase 10 初始化完成
-- ============================================================
