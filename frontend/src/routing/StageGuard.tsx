import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useSessionGuard } from '../hooks/useSessionGuard';

/**
 * Maps a persisted (localStorage) GameStage to the route that represents it.
 * Stages not present here (null, "INITIALIZED", or anything unrecognized)
 * fall back to "/" — see stageToRoute below.
 */
const STAGE_TO_ROUTE: Record<string, string> = {
  WAITING_FOR_PLAYERS: '/game/wait',
  PREPARATION: '/game/preparation',
  IN_GAME: '/game/gameplay',
  FINISHED: '/game/results',
};

function stageToRoute(stage: string | null): string {
  if (stage && STAGE_TO_ROUTE[stage]) {
    return STAGE_TO_ROUTE[stage];
  }
  return '/';
}

export type StageGuardProps = {
  requiredStage?: string;
  children: ReactNode;
};

/**
 * Gates a routed screen on the *persisted* session/player/stage snapshot
 * (via useSessionGuard) — not a live network call. Screens are responsible
 * for their own polling of live server state once mounted.
 *
 * - No sessionId or no player -> redirect to "/".
 * - requiredStage given and doesn't match the persisted stage -> redirect
 *   to whichever route matches the persisted stage (or "/" if unrecognized).
 * - Otherwise render children.
 */
export function StageGuard({ requiredStage, children }: StageGuardProps) {
  const { sessionId, player, stage } = useSessionGuard();

  if (!sessionId || !player) {
    return <Navigate to="/" replace />;
  }

  if (requiredStage && stage !== requiredStage) {
    return <Navigate to={stageToRoute(stage)} replace />;
  }

  return <>{children}</>;
}
