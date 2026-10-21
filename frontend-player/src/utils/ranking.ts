const ADVANCED_AVATAR_RANKS = new Set(['S', 'SS', 'SSS']);

export type StoryDiscoveryPositionState = 'unavailable' | 'ranked' | 'unranked' | 'no-discoveries';
export type PkPerformancePositionState = 'unavailable' | 'participated' | 'no-matches';
export type ExploreActionPositionState = 'unavailable' | 'participated' | 'no-actions';

export function getExploreActionPositionState(
  position: Record<string, unknown>,
  unavailable = false,
): ExploreActionPositionState {
  if (unavailable) return 'unavailable';
  const participated = position.participated === true || position.participated === 1
    || position.participated === '1' || String(position.participated ?? '').toLowerCase() === 'true';
  return participated ? 'participated' : 'no-actions';
}

export function exploreActionEmptyStateMessage(unavailable: boolean): string {
  return unavailable
    ? '近 7 日探索行动榜暂时无法读取，请稍后重试。'
    : '近 7 日还没有已接受的探索行动。';
}

export function getPkPerformancePositionState(
  position: Record<string, unknown>,
  unavailable = false,
): PkPerformancePositionState {
  if (unavailable) return 'unavailable';
  const participated = position.participated === true || position.participated === 1
    || position.participated === '1' || String(position.participated ?? '').toLowerCase() === 'true';
  return participated ? 'participated' : 'no-matches';
}

export function formatRatingDelta(value: unknown): string {
  const delta = Number(value);
  if (!Number.isFinite(delta)) return '0';
  return delta > 0 ? `+${delta}` : String(delta);
}

export function pkPerformanceEmptyStateMessage(unavailable: boolean): string {
  return unavailable
    ? '近 7 日 PK 榜暂时无法读取，请稍后重试。'
    : '近 7 日还没有已结算的 PK 战报。';
}

export function getStoryDiscoveryPositionState(
  position: Record<string, unknown>,
  unavailable = false,
): StoryDiscoveryPositionState {
  if (unavailable) return 'unavailable';

  const ranked = position.ranked === true || position.ranked === 1
    || position.ranked === '1' || String(position.ranked ?? '').toLowerCase() === 'true';
  if (ranked) return 'ranked';

  const discoveryCount = Number(position.discoveryCount ?? 0);
  return Number.isFinite(discoveryCount) && discoveryCount > 0 ? 'unranked' : 'no-discoveries';
}

export function storyDiscoveryEmptyStateMessage(unavailable: boolean): string {
  return unavailable
    ? '故事发现榜暂时无法读取，请稍后重试。'
    : '没有符合条件的榜单记录。';
}

export function withLeaderboardPositions<T extends Record<string, unknown>>(
  entries: readonly T[],
): Array<T & { rank: number }> {
  return entries.map((entry, index) => {
    const existingRank = Number(entry.rank);
    return {
      ...entry,
      rank: Number.isSafeInteger(existingRank) && existingRank > 0 ? existingRank : index + 1,
    };
  });
}

export function isHighAvatarRank(value: unknown): boolean {
  const rank = String(value ?? '').trim().toUpperCase();
  return ADVANCED_AVATAR_RANKS.has(rank)
    || /IMMORTAL|MYTHIC|神话|超越/.test(rank);
}

export function formatRankingProximity(
  playerId: number,
  ownPlayerId: number,
  rank: number,
  ownRank: number,
  rating: number,
  ownRating: number,
): string {
  if (playerId === ownPlayerId) return '你当前的位置';

  const positionGap = rank - ownRank;
  const ratingGap = rating - ownRating;
  const positionLabel = positionGap < 0
    ? `领先 ${Math.abs(positionGap)} 位`
    : `落后 ${positionGap} 位`;
  const ratingLabel = ratingGap === 0
    ? '同分'
    : ratingGap > 0
      ? `高 ${ratingGap} 分`
      : `低 ${Math.abs(ratingGap)} 分`;

  return `${positionLabel} · ${ratingLabel}`;
}
