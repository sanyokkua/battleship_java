import React from "react";
import {Route, Routes} from "react-router-dom";
import ApplicationNavigationBar from "./components/ApplicationNavigationBar";
import FinishPage from "./components/pages/FinishPage";
import GameplayPage from "./components/pages/GameplayPage";
import HomePage from "./components/pages/HomePage";
import JoinGamePage from "./components/pages/JoinGamePage";
import NewGamePage from "./components/pages/NewGamePage";
import NewPreparationPage from "./components/pages/NewPreparationPage";
import WaitForPlayersPage from "./components/pages/WaitForPlayersPage";
import * as gameStorage from "./services/GameStorage";
import {getPlayer, getStage} from "./services/PromiseGameService";

class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            currentSession: gameStorage.loadSession(),
            player: gameStorage.loadPlayer(),
            currentStage: null,

            gameIsReadyForPreparation: false,

            hasPreparation: false,
            hasGameplay: false,
            hasHasResults: false
        };
        this.timerTick = this.timerTick.bind(this);
    }

    timerTick() {
        if (this.state.currentSession) {
            getStage(this.state.currentSession)
                .then(stage => stage ? stage : "")
                .then(stage => this.setState({currentStage: stage}, () => {
                    //INITIALIZED, WAITING_FOR_PLAYERS, PREPARATION, IN_GAME, FINISHED
                    const hasPreparation = "PREPARATION" === stage;
                    const hasGameplay = "IN_GAME" === stage;
                    const hasHasResults = "FINISHED" === stage;
                    this.setState({
                        hasPreparation: hasPreparation,
                        hasGameplay: hasGameplay,
                        hasHasResults: hasHasResults
                    });
                }))
                .catch(e => console.warn(e));
        }
    }

    componentDidMount() {
        this.updateInterval = setInterval(this.timerTick, 5000);
        this.timerTick();
    }

    componentWillUnmount() {
        clearInterval(this.updateInterval);
    }

    isGameCreated(sessionId, player) {
        const isValidSession = sessionId && typeof sessionId === "string" && sessionId.length;
        const isValidPlayer = player && typeof player === "object" && player.playerId && typeof player.playerId === "string" && player.playerId.length;
        return Boolean(isValidSession && isValidPlayer);
    }

    async onGameSessionStarted({sessionId, playerId}) {
        const player = await getPlayer(sessionId, playerId);
        const isGameAvailable = this.isGameCreated(sessionId, player);
        gameStorage.saveSession(sessionId);
        gameStorage.savePlayer(player);
        this.setState({
            currentSession: gameStorage.loadSession(),
            player: gameStorage.loadPlayer(),
            isGameAvailable: isGameAvailable
        });
    }

    render() {
        return (
            <>
                <Routes>
                    <Route path="/" element={<ApplicationNavigationBar hasPreparation={this.state.hasPreparation}
                                                                       hasGameplay={this.state.hasGameplay}
                                                                       hasHasResults={this.state.hasHasResults}/>}>
                        <Route index element={<HomePage/>}/>

                        <Route path="/new"
                               element={<NewGamePage onNewGameSessionCreated={(data) => this.onGameSessionStarted(data)}
                                                     gameIsCreated={this.state.isGameAvailable}/>}/>
                        <Route path="/join"
                               element={<JoinGamePage onPlayerIsJoined={(data) => this.onGameSessionStarted(data)}
                                                      isJoinedToGame={this.state.isGameAvailable}/>}/>

                        <Route path="/game/wait" element={<WaitForPlayersPage sessionId={this.state.currentSession}
                                                                              player={this.state.player}/>}/>

                        <Route path="/game/preparation"
                               element={<NewPreparationPage sessionId={this.state.currentSession}
                                                            player={this.state.player}/>}/>

                        <Route path="/game/gameplay" element={<GameplayPage/>}/>
                        <Route path="/game/results" element={<FinishPage/>}/>
                        <Route path="*" element={<div>Error</div>}/>
                    </Route>
                </Routes>
            </>
        );
    }
}

export default App;
