export const queryKeys = {
  gameBootstrap: (userId?: number | null) => ['game-bootstrap', userId ?? 'unknown-user'] as const,
};
