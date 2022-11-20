import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import {Navigate} from "react-router-dom";
import {createPlayerInSession} from "../../services/PromiseGameService";
import JoinGameForm from "../common/JoinGameForm";

class JoinGamePage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isJoinedToGame: false,
            isLoading: false,
            isErrorHappen: false
        };
    }

    async handleJoinGameClick({sessionId, playerName}) {
        this.setState({
            isLoading: true
        }, async () => {
            try {
                const playerDto = await createPlayerInSession(sessionId, playerName);

                console.log(sessionId);
                console.log(playerDto);

                this.props.onPlayerIsJoined({
                    sessionId: sessionId,
                    playerId: playerDto.playerId
                });
            } catch (e) {
                this.setState({isErrorHappen: true});
            }
            this.setState({
                isLoading: false,
                isJoinedToGame: true
            });
        });
    }

    render() {
        const navigate = <Navigate to="/game/preparation" replace={true}/>;
        const progressBarLoading = <ProgressBar animated now={100}/>;
        const errorHappen = <Alert variant="danger">Error happened</Alert>;
        const form = <JoinGameForm onSubmitClicked={(data) => this.handleJoinGameClick(data)}/>;

        let toRender = null;
        if (this.props.isJoinedToGame) {
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

JoinGamePage.propTypes = {
    onPlayerIsJoined: PropTypes.func.isRequired,
    isJoinedToGame: PropTypes.bool
};

export default JoinGamePage;