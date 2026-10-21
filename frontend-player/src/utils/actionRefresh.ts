interface PanelRefetchResult {
  isError: boolean;
}

export interface ActionRefreshState {
  panelRefreshed: boolean;
  bootstrapRefreshed: boolean;
  refreshed: boolean;
}

export async function refreshActionState(
  refetchPanel: () => Promise<PanelRefetchResult>,
  refreshBootstrap: () => Promise<boolean>,
): Promise<ActionRefreshState> {
  const [panelResult, bootstrapResult] = await Promise.all([
    Promise.resolve().then(refetchPanel).then((result) => !result.isError).catch(() => false),
    Promise.resolve().then(refreshBootstrap).catch(() => false),
  ]);

  return {
    panelRefreshed: panelResult,
    bootstrapRefreshed: bootstrapResult,
    refreshed: panelResult && bootstrapResult,
  };
}
