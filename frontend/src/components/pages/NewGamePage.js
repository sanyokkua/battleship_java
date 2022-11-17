import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import ProgressBar from "react-bootstrap/ProgressBar";
import {createGameSession, createPlayerInSession, getGameEditions} from "../../services/PromiseGameService";
import {isValidString} from "../../utils/StringUtils";

class NewGamePage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValid: false,
            gameEdition: null,
            playerName: null,

            isLoading: true,
            gameEditions: [],

            gameIsCreated: false,
            isErrorHappen: false,

            createdSessionId: null
        };
    }

    handleNewGameClick(e) {
        e.preventDefault();
        this.setState({
            isLoading: true
        });
        createGameSession(this.state.gameEdition)
            .then(data => {
                if (data && data.gameSessionId && data.gameSessionId.length) {
                    const sessionId = data.gameSessionId;
                    this.setState({
                        createdSessionId: sessionId
                    });
                    const playerName = this.state.playerName;
                    return createPlayerInSession(sessionId, playerName);
                } else {
                    throw new Error("Data is not valid");
                }
            })
            .then(playerData => {
                if (playerData) {
                    const playerId = playerData.playerId;

                    this.props.onNewGameSessionCreated({
                        sessionId: this.state.createdSessionId,
                        playerId: playerId
                    });

                    this.setState({
                        isLoading: false,
                        gameIsCreated: true
                    });

                    return playerData;
                } else {
                    throw new Error("Player data is not valid");
                }
            })
            .catch(error => this.setState({isErrorHappen: true}));
    }

    handleOnGameEditionChange(e) {
        e.preventDefault();
        this.setState({
            gameEdition: e.target.value
        });
    }

    validateName(e) {
        const nameValue = e.target.value;
        const isValid = isValidString(nameValue) && isValidString(this.state.gameEdition);
        this.setState({
            isValid: isValid,
            playerName: nameValue
        });
    }

    componentDidMount() {
        getGameEditions()
            .then(data => {
                const allEditions = data.gameEditions;
                if (allEditions && allEditions.length) {
                    this.setState({
                        gameEditions: allEditions,
                        gameEdition: allEditions[0],
                        isLoading: false
                    });
                }
                return data;
            })
            .catch(error => this.setState({isErrorHappen: true}));
    }

    renderForm() {
        return (
            <>
                <Form>
                    <Form.Select aria-label="Default select example"
                                 onChange={(e) => this.handleOnGameEditionChange(e)}
                                 value={this.state.gameEdition}>
                        {this.state.gameEditions.map((element) => {
                            return <option key={element} value={element}>{element}</option>;
                        })}
                    </Form.Select>
                    <Form.Group>
                        <Form.Label>Player Name</Form.Label>
                        <Form.Control onChange={(e) => this.validateName(e)} type="text" placeholder="Enter your name"/>
                    </Form.Group>
                    <br/>
                    <Button variant="primary"
                            type="submit"
                            disabled={!this.state.isValid}
                            onClick={(e) => this.handleNewGameClick(e)}>
                        Submit
                    </Button>
                </Form>
            </>
        );
    }

    render() {
        if (this.state.gameIsCreated) {
            const navigate = useNavigate();
            navigate("wait");
            return;
        }
        const progressBarLoading = <ProgressBar animated now={100}/>;
        const errorHappen = <Alert variant="danger">Error happened</Alert>;
        const form = this.renderForm();

        let toRender = null;

        if (this.state.isLoading) {
            toRender = progressBarLoading;
        } else if (this.state.isErrorHappen) {
            toRender = errorHappen;
        } else {
            toRender = form;
        }
        return (
            <>
                {toRender}
            </>
        );
    }
}

NewGamePage.propTypes = {
    onNewGameSessionCreated: PropTypes.func.isRequired
};

export default NewGamePage;