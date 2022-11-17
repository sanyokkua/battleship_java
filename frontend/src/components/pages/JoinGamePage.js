import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import ProgressBar from "react-bootstrap/ProgressBar";
import {createPlayerInSession} from "../../services/PromiseGameService";
import {isValidString} from "../../utils/StringUtils";

class JoinGamePage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValidName: false,
            isValidSessionId: false,
            playerName: null,
            sessionId: null,
            isLoading: false,
            isJoinedToGame: false
        };
    }

    handleJoinGameClick(e) {
        e.preventDefault();

        this.setState({
            isLoading: true
        });

        createPlayerInSession(this.state.sessionId, this.state.playerName)
            .then(playerData => {
                if (playerData) {
                    const playerId = playerData.playerId;

                    this.props.onPlayerIsJoined({
                        sessionId: this.state.sessionId,
                        playerId: playerId
                    });

                    this.setState({
                        isLoading: false,
                        isJoinedToGame: true
                    });
                }
            });
    }

    validateName(e) {
        const tempValue = e.target.value;
        const isValid = isValidString(tempValue);

        this.setState({
            isValidName: isValid,
            playerName: tempValue
        });
    }

    validateSessionId(e) {
        const tempValue = e.target.value;
        const isValid = isValidString(tempValue);

        this.setState({
            isValidSessionId: isValid,
            sessionId: tempValue
        });
    }

    render() {
        const isDisabled = !(this.state.isValidName && this.state.isValidSessionId);
        let toRender = null;
        if (this.state.isJoinedToGame) {
            toRender = <Alert variant="success">
                Joined to game!
            </Alert>;
        } else {
            toRender = <Form>
                <Form.Group>
                    <Form.Label>Player Name</Form.Label>
                    <Form.Control onChange={(e) => this.validateName(e)} type="text" placeholder="Enter your name"/>
                </Form.Group>
                <Form.Group>
                    <Form.Label>Game ID</Form.Label>
                    <Form.Control onChange={(e) => this.validateSessionId(e)} type="text"
                                  placeholder="Enter session ID"/>
                </Form.Group>
                <br/>
                <Button variant="primary"
                        type="submit"
                        disabled={isDisabled}
                        onClick={(e) => this.handleJoinGameClick(e)}>
                    Submit
                </Button>
            </Form>;
        }
        return (
            <>
                {this.state.isLoading ? <ProgressBar animated now={100}/> : toRender}
            </>

        );
    }
}

JoinGamePage.propTypes = {
    onPlayerIsJoined: PropTypes.func.isRequired
};

export default JoinGamePage;