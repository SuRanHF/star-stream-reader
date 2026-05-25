package com.huazhenghai.readergame.common;

/**
 * 统一错误码常量.
 * <p>
 * 所有业务异常均使用此处的字符串常量作为错误码,
 * 方便前端统一处理和国际化.
 * </p>
 */
public final class ErrorCode {

    private ErrorCode() {
        // 工具类, 禁止实例化
    }

    // ==================== 认证 & 授权 (1xx) ====================

    /** 未登录 / 未提供 Token */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    /** Token 过期或格式无效 */
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    /** 账号或密码错误 */
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    /** 无权限操作 */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** 请求参数错误 */
    public static final String BAD_REQUEST = "BAD_REQUEST";

    // ==================== 用户 & 玩家 (2xx) ====================

    /** 用户名已存在 */
    public static final String DUPLICATE_USER = "DUPLICATE_USER";

    /** 玩家角色不存在 */
    public static final String PLAYER_NOT_FOUND = "PLAYER_NOT_FOUND";

    /** 该用户已有角色, 不可重复创建 */
    public static final String ALREADY_HAS_PLAYER = "ALREADY_HAS_PLAYER";

    /** 密码强度不足 */
    public static final String WEAK_PASSWORD = "WEAK_PASSWORD";

    /** 邮箱格式无效 */
    public static final String INVALID_EMAIL = "INVALID_EMAIL";

    // ==================== 参数校验 (3xx) ====================

    /** 缺少必填字段 */
    public static final String MISSING_FIELDS = "MISSING_FIELDS";

    /** 字段值超出合法范围 */
    public static final String INVALID_FIELD_VALUE = "INVALID_FIELD_VALUE";

    /** 数据格式不正确 (e.g. JSON 解析失败) */
    public static final String INVALID_FORMAT = "INVALID_FORMAT";

    // ==================== 游戏业务 (4xx) ====================

    /** 章节不存在 */
    public static final String CHAPTER_NOT_FOUND = "CHAPTER_NOT_FOUND";

    /** 选项不合法 (不在当前章节可选项中) */
    public static final String INVALID_CHOICE = "INVALID_CHOICE";

    /** 已选过的路线不可重复 */
    public static final String ROUTE_ALREADY_TAKEN = "ROUTE_ALREADY_TAKEN";

    /** 地点不存在 */
    public static final String LOCATION_NOT_FOUND = "LOCATION_NOT_FOUND";

    /** 地点未解锁 */
    public static final String LOCATION_LOCKED = "LOCATION_LOCKED";

    /** 无可用探索事件 */
    public static final String NO_AVAILABLE_EVENT = "NO_AVAILABLE_EVENT";

    /** 事件不存在 */
    public static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";

    /** 玩家正在休息 */
    public static final String PLAYER_RESTING = "PLAYER_RESTING";

    /** 已经处于休息状态 */
    public static final String ALREADY_RESTING = "ALREADY_RESTING";

    /** 未处于休息状态 */
    public static final String NOT_RESTING = "NOT_RESTING";

    /** 体力不足 */
    public static final String STAMINA_NOT_ENOUGH = "STAMINA_NOT_ENOUGH";

    /** 物品数量不足 */
    public static final String ITEM_NOT_ENOUGH = "ITEM_NOT_ENOUGH";

    /** 物品不存在 */
    public static final String ITEM_NOT_FOUND = "ITEM_NOT_FOUND";

    /** 物品不可消耗/使用 */
    public static final String ITEM_NOT_CONSUMABLE = "ITEM_NOT_CONSUMABLE";

    /** 物品不可出售 */
    public static final String ITEM_CANNOT_SELL = "ITEM_CANNOT_SELL";

    /** 物品已被禁用 */
    public static final String ITEM_NOT_ENABLED = "ITEM_NOT_ENABLED";

    /** 装备不存在 */
    public static final String EQUIP_NOT_FOUND = "EQUIP_NOT_FOUND";

    /** 装备未拥有 */
    public static final String EQUIP_NOT_OWNED = "EQUIP_NOT_OWNED";

    /** 装备已禁用 */
    public static final String EQUIP_NOT_ENABLED = "EQUIP_NOT_ENABLED";

    /** 装备耐久不足 */
    public static final String EQUIP_BROKEN = "EQUIP_BROKEN";

    /** 装备栏位已占用 */
    public static final String EQUIP_SLOT_OCCUPIED = "EQUIP_SLOT_OCCUPIED";

    /** 装备已拥有 */
    public static final String EQUIP_ALREADY_OWNED = "EQUIP_ALREADY_OWNED";

    /** 技能冷却中 */
    public static final String SKILL_ON_COOLDOWN = "SKILL_ON_COOLDOWN";

    /** 不满足技能解锁条件 */
    public static final String SKILL_NOT_UNLOCKED = "SKILL_NOT_UNLOCKED";

    /** 技能不存在 */
    public static final String SKILL_NOT_FOUND = "SKILL_NOT_FOUND";

    /** 技能已学习 */
    public static final String SKILL_ALREADY_LEARNED = "SKILL_ALREADY_LEARNED";

    /** 技能已被禁用 */
    public static final String SKILL_DISABLED = "SKILL_DISABLED";

    /** 资源不足 */
    public static final String RESOURCE_NOT_ENOUGH = "RESOURCE_NOT_ENOUGH";

    /** 怪物不存在 */
    public static final String MONSTER_NOT_FOUND = "MONSTER_NOT_FOUND";

    /** 怪物已被禁用 */
    public static final String MONSTER_DISABLED = "MONSTER_DISABLED";

    /** 玩家已死亡 */
    public static final String PLAYER_DEAD = "PLAYER_DEAD";

    /** 尚未选择背后星 */
    public static final String CONSTELLATION_REQUIRED = "CONSTELLATION_REQUIRED";

    /** PK 对手不存在或不在线 */
    public static final String PK_TARGET_UNAVAILABLE = "PK_TARGET_UNAVAILABLE";

    /** 存档不存在 */
    public static final String SAVE_NOT_FOUND = "SAVE_NOT_FOUND";

    /** 称号未拥有 */
    public static final String TITLE_NOT_OWNED = "TITLE_NOT_OWNED";

    /** 称号已解锁 */
    public static final String TITLE_ALREADY_UNLOCKED = "TITLE_ALREADY_UNLOCKED";

    /** 位阶升阶条件不满足 */
    public static final String RANK_REQUIREMENTS_NOT_MET = "RANK_REQUIREMENTS_NOT_MET";

    /** 已达最高位阶 */
    public static final String MAX_RANK = "MAX_RANK";

    /** 不能挑战自己 */
    public static final String SELF_CHALLENGE = "SELF_CHALLENGE";

    /** 已有待处理的挑战 */
    public static final String DUPLICATE_CHALLENGE = "DUPLICATE_CHALLENGE";

    /** 挑战不存在或已过期 */
    public static final String CHALLENGE_NOT_FOUND = "CHALLENGE_NOT_FOUND";

    /** 这不是发给你的挑战 */
    public static final String NOT_YOUR_CHALLENGE = "NOT_YOUR_CHALLENGE";

    /** 无效的PK模式 */
    public static final String INVALID_PK_MODE = "INVALID_PK_MODE";

    /** 阶段未完成 */
    public static final String CHAPTER_NOT_COMPLETED = "CHAPTER_NOT_COMPLETED";

    /** 无法推进阶段 */
    public static final String CANNOT_ADVANCE = "CANNOT_ADVANCE";

    // ==================== 通用 (9xx) ====================

    /** 服务器内部错误 (未预期的异常) */
    public static final String SERVER_ERROR = "SERVER_ERROR";

    /** 请求过于频繁 (限流触发) */
    public static final String RATE_LIMITED = "RATE_LIMITED";

    /** 资源不存在 (泛用) */
    public static final String NOT_FOUND = "NOT_FOUND";
}
