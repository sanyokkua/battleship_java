import React from 'react';
import AppNavBar from "./components/AppNavBar"
import HomeComponent from "./components/pages/HomeComponent"
import NewGameComponent from "./components/pages/NewGameComponent";
import JoinGameComponent from "./components/pages/JoinGameComponent";
import WaitForPlayersComponent from "./components/pages/WaitForPlayersComponent";
import PreparationComponent from "./components/pages/PreparationComponent";
import Gameplay from "./components/pages/Gameplay";
import Finish from "./components/pages/Finish";
import {BrowserRouter as Router, Route, Routes} from 'react-router-dom';

// const PAGE_NAME_MAPPING = {
//     "": "Battleship",
//     "home": "Battleship",
//     "new": "Create new game",
//     "join": "Join to the game",
//     "WAITING_FOR_PLAYERS": "Wait for players",
//     "PREPARATION": "Wait for players",
//     "IN_GAME": "Wait for players",
//     "FINISHED": "Wait for players",
// };

class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            currentSession: null,
            lastGameStage: null,
            player: {
                playerId: "temp-player-id-1",
                playerName: "player 1"
            }
        }
    }

    render() {
        return (
            <>
                <Router>
                    <AppNavBar/>
                    <Routes>
                        <Route path='/' exact={true} element={<HomeComponent/>}/>
                        <Route path='/new' exact={true} element={<NewGameComponent/>}/>
                        <Route path='/join/' exact={true} element={<JoinGameComponent/>}/>
                        <Route path='/game/:id/wait' element={<WaitForPlayersComponent/>}/>
                        <Route path='/game/:id/preparation' element={<PreparationComponent/>}/>
                        <Route path='/game/:id/gameplay' element={<Gameplay/>}/>
                        <Route path='/game/:id/finish' element={<Finish/>}/>
                    </Routes>
                </Router>
            </>

        )
    }
}

export default App;
