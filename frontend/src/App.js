import React from "react";
import {getPlayer} from "./services/PromiseGameService";


class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            currentSession: null,
            lastGameStage: null,
            player: null
        };
    }

    onGameSessionStarted({sessionId, playerId}) {
        getPlayer(sessionId, playerId)
            .then(playerData => {
                if (playerData) {
                    this.setState({
                        currentSession: sessionId,
                        player: playerData
                    });
                }
            });
    }

    render() {
        return (
            <>
            </>
        );
    }
}

export default App;
