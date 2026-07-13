import {Navigate, Route, Routes} from 'react-router-dom';
import {StageGuard} from './StageGuard';
import {HomeScreen} from '../screens/HomeScreen';
import {NewGameScreen} from '../screens/NewGameScreen';
import {JoinGameScreen} from '../screens/JoinGameScreen';
import {WaitScreen} from '../screens/WaitScreen';
import {PreparationScreen} from '../screens/PreparationScreen';
import {GameplayScreen} from '../screens/GameplayScreen';
import {ResultsScreen} from '../screens/ResultsScreen';

/**
 * Top-level route table for the app, rendered inside the router provider in
 * the app shell (alongside AppBar). Maps each screen to its path:
 *
 * - "/" -> HomeScreen, "/new" -> NewGameScreen, "/join" -> JoinGameScreen —
 *   unguarded, reachable regardless of session state.
 * - "/game/wait", "/game/preparation", "/game/gameplay", "/game/results" ->
 *   the corresponding in-game screen, each wrapped in a StageGuard whose
 *   `requiredStage` matches the GameStage the route represents
 *   (WAITING_FOR_PLAYERS, PREPARATION, IN_GAME, FINISHED respectively).
 *   StageGuard redirects to "/" (no session/player) or to the route matching
 *   the persisted stage (stage mismatch) before rendering the screen — see
 *   StageGuard for the redirect rules.
 * - Any other path -> redirected to "/".
 */
export function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<HomeScreen/>}/>
            <Route path="/new" element={<NewGameScreen/>}/>
            <Route path="/join" element={<JoinGameScreen/>}/>
            <Route
                path="/game/wait"
                element={
                    <StageGuard requiredStage="WAITING_FOR_PLAYERS">
                        <WaitScreen/>
                    </StageGuard>
                }
            />
            <Route
                path="/game/preparation"
                element={
                    <StageGuard requiredStage="PREPARATION">
                        <PreparationScreen/>
                    </StageGuard>
                }
            />
            <Route
                path="/game/gameplay"
                element={
                    <StageGuard requiredStage="IN_GAME">
                        <GameplayScreen/>
                    </StageGuard>
                }
            />
            <Route
                path="/game/results"
                element={
                    <StageGuard requiredStage="FINISHED">
                        <ResultsScreen/>
                    </StageGuard>
                }
            />
            <Route path="*" element={<Navigate to="/" replace/>}/>
        </Routes>
    );
}
