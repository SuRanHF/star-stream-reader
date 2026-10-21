package com.huazhenghai.readergame.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huazhenghai.readergame.entity.Ranking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RankingMapper extends BaseMapper<Ranking> {

    @Select("""
            SELECT
                p.id AS playerId,
                p.player_name AS playerName,
                story_counts.discoveryCount AS discoveryCount
            FROM players p
            INNER JOIN (
                SELECT first_discoveries.player_id,
                    COUNT(*) AS discoveryCount
                FROM (
                    SELECT player_id, event_key, MIN(created_at) AS first_discovered_at
                    FROM player_story_logs
                    WHERE choice_index IS NOT NULL
                    GROUP BY player_id, event_key
                ) first_discoveries
                WHERE first_discoveries.first_discovered_at >= #{startAt}
                    AND first_discoveries.first_discovered_at < #{endAt}
                GROUP BY first_discoveries.player_id
            ) story_counts ON story_counts.player_id = p.id
            ORDER BY story_counts.discoveryCount DESC, p.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectStoryDiscoveryLeaderboard(@Param("startAt") LocalDateTime startAt,
                                                               @Param("endAt") LocalDateTime endAt,
                                                               @Param("limit") int limit);

    @Select("""
            WITH first_discoveries AS (
                SELECT player_id, event_key, MIN(created_at) AS first_discovered_at
                FROM player_story_logs
                WHERE choice_index IS NOT NULL
                GROUP BY player_id, event_key
            ), weekly_counts AS (
                SELECT player_id, COUNT(*) AS discovery_count
                FROM first_discoveries
                WHERE first_discovered_at >= #{startAt}
                    AND first_discovered_at < #{endAt}
                GROUP BY player_id
            )
            SELECT
                CASE WHEN MAX(own.player_id) IS NULL THEN 0
                    ELSE COUNT(DISTINCT CASE
                        WHEN other_players.discovery_count > own.discovery_count
                        THEN other_players.player_id END) + 1
                END AS playerRank,
                COALESCE(MAX(own.discovery_count), 0) AS discoveryCount,
                COUNT(DISTINCT other_players.player_id) AS participantCount,
                MIN(CASE
                    WHEN own.player_id IS NULL THEN other_players.discovery_count
                    WHEN other_players.discovery_count > own.discovery_count
                        THEN other_players.discovery_count
                    ELSE NULL
                END) AS nextRankDiscoveryCount
            FROM weekly_counts other_players
            LEFT JOIN weekly_counts own ON own.player_id = #{playerId}
            """)
    Map<String, Object> selectStoryDiscoveryPosition(@Param("playerId") Long playerId,
                                                     @Param("startAt") LocalDateTime startAt,
                                                     @Param("endAt") LocalDateTime endAt);

    @Select("""
            WITH match_changes AS (
                SELECT attacker_id AS player_id,
                    CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rating_change_json, '$.attacker_change')), '0') AS SIGNED) AS rating_delta,
                    CASE WHEN winner_id = attacker_id THEN 1 ELSE 0 END AS win_count
                FROM pk_records
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND rating_change_json IS NOT NULL
                UNION ALL
                SELECT defender_id AS player_id,
                    CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rating_change_json, '$.defender_change')), '0') AS SIGNED) AS rating_delta,
                    CASE WHEN winner_id = defender_id THEN 1 ELSE 0 END AS win_count
                FROM pk_records
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND rating_change_json IS NOT NULL
            ), weekly_scores AS (
                SELECT player_id, SUM(rating_delta) AS rating_delta,
                    COUNT(*) AS match_count, SUM(win_count) AS win_count
                FROM match_changes
                GROUP BY player_id
            )
            SELECT p.id AS playerId, p.player_name AS playerName,
                scores.rating_delta AS ratingDelta,
                scores.match_count AS matchCount,
                scores.win_count AS winCount
            FROM weekly_scores scores
            INNER JOIN players p ON p.id = scores.player_id
            ORDER BY scores.rating_delta DESC, scores.win_count DESC, p.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectPkPerformanceLeaderboard(@Param("startAt") LocalDateTime startAt,
                                                              @Param("endAt") LocalDateTime endAt,
                                                              @Param("limit") int limit);

    @Select("""
            WITH match_changes AS (
                SELECT attacker_id AS player_id,
                    CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rating_change_json, '$.attacker_change')), '0') AS SIGNED) AS rating_delta,
                    CASE WHEN winner_id = attacker_id THEN 1 ELSE 0 END AS win_count
                FROM pk_records
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND rating_change_json IS NOT NULL
                UNION ALL
                SELECT defender_id AS player_id,
                    CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(rating_change_json, '$.defender_change')), '0') AS SIGNED) AS rating_delta,
                    CASE WHEN winner_id = defender_id THEN 1 ELSE 0 END AS win_count
                FROM pk_records
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND rating_change_json IS NOT NULL
            ), weekly_scores AS (
                SELECT player_id, SUM(rating_delta) AS rating_delta,
                    COUNT(*) AS match_count, SUM(win_count) AS win_count
                FROM match_changes
                GROUP BY player_id
            )
            SELECT
                CASE WHEN MAX(own.player_id) IS NULL THEN 0
                    ELSE COUNT(DISTINCT CASE
                        WHEN other_scores.rating_delta > own.rating_delta
                        THEN other_scores.player_id END) + 1
                END AS playerRank,
                COUNT(DISTINCT other_scores.player_id) AS participantCount,
                COALESCE(MAX(own.rating_delta), 0) AS ratingDelta,
                COALESCE(MAX(own.match_count), 0) AS matchCount,
                COALESCE(MAX(own.win_count), 0) AS winCount,
                MIN(CASE
                    WHEN own.player_id IS NULL THEN CASE
                        WHEN other_scores.rating_delta > 0 THEN other_scores.rating_delta
                        ELSE NULL END
                    WHEN other_scores.rating_delta > own.rating_delta THEN other_scores.rating_delta
                    ELSE NULL
                END) AS nextRankRatingDelta
            FROM weekly_scores other_scores
            LEFT JOIN weekly_scores own ON own.player_id = #{playerId}
            """)
    Map<String, Object> selectPkPerformancePosition(@Param("playerId") Long playerId,
                                                    @Param("startAt") LocalDateTime startAt,
                                                    @Param("endAt") LocalDateTime endAt);

    @Select("""
            WITH weekly_actions AS (
                SELECT player_id,
                    COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.requestKey'))) AS action_count
                FROM player_logs
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.kind')) = 'exploration_result'
                    AND JSON_EXTRACT(metadata_json, '$.requestKey') IS NOT NULL
                    AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.requestKey')) <> ''
                GROUP BY player_id
            )
            SELECT p.id AS playerId, p.player_name AS playerName,
                actions.action_count AS actionCount
            FROM weekly_actions actions
            INNER JOIN players p ON p.id = actions.player_id
            ORDER BY actions.action_count DESC, p.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectExploreActionLeaderboard(@Param("startAt") LocalDateTime startAt,
                                                              @Param("endAt") LocalDateTime endAt,
                                                              @Param("limit") int limit);

    @Select("""
            WITH weekly_actions AS (
                SELECT player_id,
                    COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.requestKey'))) AS action_count
                FROM player_logs
                WHERE created_at >= #{startAt} AND created_at < #{endAt}
                    AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.kind')) = 'exploration_result'
                    AND JSON_EXTRACT(metadata_json, '$.requestKey') IS NOT NULL
                    AND JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.requestKey')) <> ''
                GROUP BY player_id
            )
            SELECT
                CASE WHEN MAX(own.player_id) IS NULL THEN 0
                    ELSE COUNT(DISTINCT CASE
                        WHEN other_actions.action_count > own.action_count
                        THEN other_actions.player_id END) + 1
                END AS playerRank,
                COUNT(DISTINCT other_actions.player_id) AS participantCount,
                COALESCE(MAX(own.action_count), 0) AS actionCount,
                MIN(CASE
                    WHEN own.player_id IS NULL THEN other_actions.action_count
                    WHEN other_actions.action_count > own.action_count THEN other_actions.action_count
                    ELSE NULL
                END) AS nextRankActionCount
            FROM weekly_actions other_actions
            LEFT JOIN weekly_actions own ON own.player_id = #{playerId}
            """)
    Map<String, Object> selectExploreActionPosition(@Param("playerId") Long playerId,
                                                    @Param("startAt") LocalDateTime startAt,
                                                    @Param("endAt") LocalDateTime endAt);

    @Select("""
            SELECT
                p.id AS playerId,
                p.player_name AS playerName,
                CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS UNSIGNED) AS level,
                CASE WHEN r.player_id IS NULL THEN 0 ELSE 1 END AS ranked,
                COALESCE(r.rating, 1000) AS rating,
                COALESCE(r.wins, 0) AS wins,
                COALESCE(r.losses, 0) AS losses,
                COALESCE(r.highest_rating, 1000) AS highestRating
            FROM players p
            LEFT JOIN rankings r ON r.player_id = p.id
            ORDER BY CASE WHEN r.player_id IS NULL THEN 0 ELSE 1 END DESC,
                COALESCE(r.rating, 1000) DESC,
                COALESCE(r.wins, 0) DESC,
                COALESCE(r.losses, 0) ASC,
                p.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectEloLeaderboard(@Param("limit") int limit);

    @Select("SELECT * FROM rankings WHERE player_id = #{playerId} LIMIT 1")
    Ranking selectByPlayerId(@Param("playerId") Long playerId);

    @Select("SELECT COUNT(*) FROM rankings")
    long countRankedPlayers();

    @Select("""
            SELECT COUNT(*)
            FROM rankings other
            WHERE other.rating > #{rating}
                OR (other.rating = #{rating} AND other.wins > #{wins})
                OR (other.rating = #{rating} AND other.wins = #{wins} AND other.losses < #{losses})
                OR (other.rating = #{rating} AND other.wins = #{wins}
                    AND other.losses = #{losses} AND other.player_id < #{playerId})
            """)
    long countRankedPlayersAhead(@Param("rating") int rating,
                                 @Param("wins") int wins,
                                 @Param("losses") int losses,
                                 @Param("playerId") Long playerId);

    @Select("""
            SELECT
                p.id AS playerId,
                p.player_name AS playerName,
                CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS UNSIGNED) AS level,
                r.rating AS rating,
                r.wins AS wins,
                r.losses AS losses,
                r.highest_rating AS highestRating
            FROM rankings r
            INNER JOIN players p ON p.id = r.player_id
            ORDER BY r.rating DESC, r.wins DESC, r.losses ASC, r.player_id ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectRankedLeaderboardWindow(@Param("limit") int limit,
                                                              @Param("offset") int offset);

    @Select("""
            SELECT
                p.id AS playerId,
                p.player_name AS playerName,
                COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.avatarRank')), 'F') AS avatarRank,
                CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS SIGNED) AS level,
                COALESCE(r.rating, 1000) AS rating
            FROM players p
            LEFT JOIN avatar_rank_configs arc
                ON arc.rank_key = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.avatarRank')), 'F')
                AND arc.enabled = 1
            LEFT JOIN rankings r ON r.player_id = p.id
            ORDER BY COALESCE(arc.order_num, 0) DESC,
                CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS SIGNED) DESC,
                COALESCE(r.rating, 1000) DESC,
                p.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectAvatarRankLeaderboard(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM players")
    long countAvatarRankPlayers();

    @Select("""
            SELECT COUNT(*)
            FROM players p
            LEFT JOIN avatar_rank_configs arc
                ON arc.rank_key = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.avatarRank')), 'F')
                AND arc.enabled = 1
            LEFT JOIN rankings r ON r.player_id = p.id
            WHERE COALESCE(arc.order_num, 0) > #{rankOrder}
                OR (COALESCE(arc.order_num, 0) = #{rankOrder}
                    AND CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS SIGNED) > #{level})
                OR (COALESCE(arc.order_num, 0) = #{rankOrder}
                    AND CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS SIGNED) = #{level}
                    AND COALESCE(r.rating, 1000) > #{rating})
                OR (COALESCE(arc.order_num, 0) = #{rankOrder}
                    AND CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(p.stats_json, '$.level')), '1') AS SIGNED) = #{level}
                    AND COALESCE(r.rating, 1000) = #{rating}
                    AND p.id < #{playerId})
            """)
    long countAvatarRankPlayersAhead(@Param("rankOrder") int rankOrder,
                                     @Param("level") int level,
                                     @Param("rating") int rating,
                                     @Param("playerId") Long playerId);
}
