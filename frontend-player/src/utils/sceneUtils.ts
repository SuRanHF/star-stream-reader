export function dangerTier(level: number): { label: string; color: string } {
  if (level >= 10) return { label: 'S', color: '#c85aef' };
  if (level >= 9) return { label: 'A', color: '#e05050' };
  if (level >= 7) return { label: 'B', color: '#4a9fef' };
  if (level >= 6) return { label: 'C', color: '#7b5ecc' };
  if (level >= 5) return { label: 'D', color: '#4a8fcc' };
  if (level >= 3) return { label: 'E', color: '#7a8aaa' };
  return { label: 'F', color: '#788899' };
}
