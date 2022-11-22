import React from "react";
import {Route, Routes} from "react-router-dom";
import ApplicationNavigationBar from "./ui/ApplicationNavigationBar";
import HomePage from "./ui/pages/HomePage";
import JoinGamePage from "./ui/pages/JoinGamePage";
import NewGamePage from "./ui/pages/NewGamePage";
import WaitForPlayersPage from "./ui/pages/WaitForPlayersPage";
import * as gameStorage from "./services/GameStorage";
import * as gameUtils from "./utils/GameUtils";
import {getStage} from "./services/PromiseGameService";
import {GameStage, PlayerDto} from "./logic/GameTypes";
import {GameCreatedOrJoinedResult} from "./ui/pages/common/PagesCommonTypes";
import PreparationPage from "./ui/pages/PreparationPage";
import GameplayPage from "./ui/pages/GameplayPage";

type AppState = {
    sessionId: string | null,
    playerDto: PlayerDto | null,
    gameStage: GameStage | null,
    gameEditions: string[] | [],
    hasPreparation: boolean,
    hasGameplay: boolean,
    hasHasResults: boolean
};

class App extends React.Component<any, AppState> {
    // state:AppState = {};
    constructor(props: any) {
        super(props);
        this.state = {
            sessionId: null,
            playerDto: null,
            gameStage: null,
            gameEditions: [],
            hasPreparation: false,
            hasGameplay: false,
            hasHasResults: false
        };
    }

    async componentDidMount() {
        const initialData = await gameUtils.loadInitialDataAsync();
        this.setState({
            sessionId: initialData.sessionId,
            playerDto: initialData.player,
            gameStage: initialData.stage,
            gameEditions: initialData.gameEditions
        }, () => this.updateStage());
    }

    async updateStage() {
        if (this.state.sessionId && this.state.playerDto) {
            const gameStageDto = await getStage(this.state.sessionId);
            this.setState({gameStage: gameStageDto.gameStage}, () => {
                //INITIALIZED, WAITING_FOR_PLAYERS, PREPARATION, IN_GAME, FINISHED
                const hasPreparation: boolean = "PREPARATION" === gameStageDto.gameStage;
                const hasGameplay: boolean = "IN_GAME" === gameStageDto.gameStage;
                const hasHasResults: boolean = "FINISHED" === gameStageDto.gameStage;
                this.setState({
                    hasPreparation: hasPreparation,
                    hasGameplay: hasGameplay,
                    hasHasResults: hasHasResults
                });
            });
        }
    }

    async onGameSessionStarted(sessionStarted: GameCreatedOrJoinedResult) {
        gameStorage.saveSession(sessionStarted.sessionId);
        gameStorage.savePlayer(sessionStarted.player);
        this.setState({
            sessionId: sessionStarted.sessionId,
            playerDto: sessionStarted.player
        });
    }

    render() {
        return (
            <>
                <Routes>
                    <Route path="/" element={<ApplicationNavigationBar hasPreparation={this.state.hasPreparation}
                                                                       hasGameplay={this.state.hasGameplay}
                                                                       hasHasResults={this.state.hasHasResults}/>}>
                        <Route index element={<HomePage
                            isDataLoaded={this.state.gameEditions && this.state.gameEditions.length > 0}/>}/>
                        <Route path="/new" element={<NewGamePage
                            onNewGameSessionCreated={(data) => this.onGameSessionStarted(data)}/>}/>
                        <Route path="/join"
                               element={<JoinGamePage onPlayerIsJoined={(data) => this.onGameSessionStarted(data)}/>}/>

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/wait" element={
                                <WaitForPlayersPage sessionId={this.state.sessionId} player={this.state.playerDto}/>}/>}

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/preparation" element={
                                <PreparationPage sessionId={this.state.sessionId} player={this.state.playerDto}/>}/>}

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/gameplay" element={
                                <GameplayPage sessionId={this.state.sessionId} player={this.state.playerDto}/>}/>}

                        {/*<Route path="/game/results" element={<FinishPage/>}/>*/}
                        <Route path="*" element={<div>Error</div>}/>
                    </Route>
                </Routes>
            </>
        );
    }
}

export default App;
