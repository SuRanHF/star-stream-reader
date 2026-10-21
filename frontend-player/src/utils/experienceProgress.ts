export interface ExperienceProgress {
  current: number;
  required: number;
  percent: number;
}

/** Convert lifetime experience into progress within the player's current level. */
export function getExperienceProgress(totalExperience: unknown, rawLevel: unknown): ExperienceProgress {
  const experienceValue = Number(totalExperience);
  const levelValue = Number(rawLevel);
  const total = Number.isFinite(experienceValue) ? Math.max(0, Math.floor(experienceValue)) : 0;
  const level = Number.isFinite(levelValue) ? Math.max(1, Math.floor(levelValue)) : 1;

  const levelStart = 100 * (level - 1) ** 2;
  const levelTarget = 100 * level ** 2;
  const required = levelTarget - levelStart;
  const current = Math.min(required, Math.max(0, total - levelStart));

  return {
    current,
    required,
    percent: Math.round((current / required) * 100),
  };
}
