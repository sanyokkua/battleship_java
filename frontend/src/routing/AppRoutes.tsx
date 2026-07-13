import {Navigate, Route, Routes} from 'react-router-dom';
import {StageGuard} from './StageGuard';
import {HomeScreen} from '../screens/HomeScreen';
import {NewGameScreen} from '../screens/NewGameScreen';
import {JoinGameScreen} from '../screens/JoinGameScreen';
import {WaitScreen} from '../screens/WaitScreen';
import {PreparationScreen} from '../screens/PreparationScreen';
import {GameplayScreen} from '../screens/GameplayScreen';
import {ResultsScreen} from '../screens/ResultsScreen';

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
