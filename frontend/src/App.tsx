import React from "react";
import {Alert} from "react-bootstrap";
import {Route, Routes} from "react-router-dom";
import {GameStage, PlayerDto} from "./logic/GameTypes";
import * as gameStorage from "./services/GameStorage";
import {getStage} from "./services/PromiseGameService";
import ApplicationNavigationBar from "./ui/ApplicationNavigationBar";
import {GameCreatedOrJoinedResult} from "./ui/pages/common/PagesCommonTypes";
import FinishPage from "./ui/pages/FinishPage";
import GameplayPage from "./ui/pages/GameplayPage";
import HomePage from "./ui/pages/HomePage";
import JoinGamePage from "./ui/pages/JoinGamePage";
import NewGamePage from "./ui/pages/NewGamePage";
import PreparationPage from "./ui/pages/PreparationPage";
import WaitForPlayersPage from "./ui/pages/WaitForPlayersPage";
import * as gameUtils from "./utils/GameUtils";

type AppState = {
    sessionId: string | null,
    playerDto: PlayerDto | null,
    gameStage: GameStage | null,
    hasPreparation: boolean,
    hasGameplay: boolean,
    hasHasResults: boolean
};

class App extends React.Component<any, AppState> {

    constructor(props: any) {
        super(props);
        this.state = {
            sessionId: null,
            playerDto: null,
            gameStage: null,
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
                gameStorage.saveStage(gameStageDto.gameStage);
            });
        }
    }

    async onNewPageOpened() {
        await this.updateStage();
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
                    <Route path="/" element={
                        <ApplicationNavigationBar hasPreparation={this.state.hasPreparation}
                                                  hasGameplay={this.state.hasGameplay}
                                                  hasHasResults={this.state.hasHasResults}/>}>

                        <Route index element={<HomePage/>}/>

                        <Route path="/new" element={
                            <NewGamePage onNewGameSessionCreated={(data) => this.onGameSessionStarted(data)}/>}/>

                        <Route path="/join"
                               element={<JoinGamePage onPlayerIsJoined={(data) => this.onGameSessionStarted(data)}/>}/>

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/wait" element={
                                <WaitForPlayersPage sessionId={this.state.sessionId} player={this.state.playerDto}
                                                    onPageOpened={() => this.onNewPageOpened()}/>}/>}

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/preparation" element={
                                <PreparationPage sessionId={this.state.sessionId} player={this.state.playerDto}
                                                 onPageOpened={() => this.onNewPageOpened()}/>}/>}

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/gameplay" element={
                                <GameplayPage sessionId={this.state.sessionId} player={this.state.playerDto}
                                              onPageOpened={() => this.onNewPageOpened()}/>}/>}

                        {this.state.sessionId && this.state.playerDto
                            && <Route path="/game/results" element={
                                <FinishPage player={this.state.playerDto} sessionId={this.state.sessionId}
                                            onPageOpened={() => this.onNewPageOpened()}/>}/>}

                        <Route path="*" element={
                            <Alert variant="danger">
                                <Alert.Heading>The error happen!</Alert.Heading>
                                <p>
                                    During your last action happened an error. Please return to the
                                    home page.
                                </p>
                            </Alert>}/>
                    </Route>
                </Routes>
            </>
        );
    }
}

export default App;
