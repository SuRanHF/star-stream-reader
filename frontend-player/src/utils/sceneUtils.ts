export function dangerTier(level: number): { label: string; color: string } {
  if (level >= 10) return { label: 'S', color: 'var(--color-purple)' };
  if (level >= 9) return { label: 'A', color: 'var(--color-danger)' };
  if (level >= 7) return { label: 'B', color: 'var(--color-star)' };
  if (level >= 6) return { label: 'C', color: 'var(--color-purple)' };
  if (level >= 5) return { label: 'D', color: 'var(--color-system)' };
  if (level >= 3) return { label: 'E', color: 'var(--color-text-dim)' };
  return { label: 'F', color: 'var(--color-muted)' };
}
