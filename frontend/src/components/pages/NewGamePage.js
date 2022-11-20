import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import {Navigate} from "react-router-dom";
import {createGameSession, createPlayerInSession, getGameEditions} from "../../services/PromiseGameService";
import NewGameForm from "../common/NewGameForm";

class NewGamePage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isLoading: true,
            gameEditions: [],

            gameEdition: null,
            isValid: false,
            playerName: null,

            isErrorHappen: false
        };
    }

    async handleNewGameClick({gameEdition, playerName}) {
        this.setState({
            isLoading: true
        });

        try {
            const gameSessionDto = await createGameSession(gameEdition);
            const sessionId = gameSessionDto.gameSessionId;
            const playerDto = await createPlayerInSession(sessionId, playerName);

            console.log(sessionId);
            console.log(playerDto);

            this.props.onNewGameSessionCreated({
                sessionId: sessionId,
                playerId: playerDto.playerId
            });
        } catch (error) {
            this.setState({isErrorHappen: true});
        }

        this.setState({
            isLoading: false
        });

    }

    async componentDidMount() {
        try {
            const gameEditionsDto = await getGameEditions();
            this.setState({
                gameEditions: gameEditionsDto.gameEditions,
                gameEdition: gameEditionsDto.gameEditions[0],
                isLoading: false
            });
        } catch (error) {
            this.setState({isErrorHappen: true});
        }
    }

    render() {
        const navigate = <Navigate to="/game/wait" replace={true}/>;
        const progressBarLoading = <ProgressBar animated now={100}/>;
        const errorHappen = <Alert variant="danger">Error happened</Alert>;
        const form = <NewGameForm onSubmitClicked={(data) => this.handleNewGameClick(data)}
                                  gameEditions={this.state.gameEditions}/>;

        let toRender = null;
        if (this.props.gameIsCreated) {
            toRender = navigate;
        } else if (this.state.isLoading) {
            toRender = progressBarLoading;
        } else if (this.state.isErrorHappen) {
            toRender = errorHappen;
        } else {
            toRender = form;
        }

        return (
            <>
                <Container className="d-grid gap-4 w-75-ns p-3">
                    {toRender}
                </Container>
            </>
        );
    }
}

NewGamePage.propTypes = {
    onNewGameSessionCreated: PropTypes.func.isRequired,
    gameIsCreated: PropTypes.bool
};

export default NewGamePage;