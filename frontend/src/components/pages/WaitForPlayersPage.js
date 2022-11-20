import copy from "copy-to-clipboard";
import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Toast from "react-bootstrap/Toast";
import {Navigate} from "react-router-dom";
import {getOpponent} from "../../services/PromiseGameService";


class WaitForPlayersPage extends React.Component {
    constructor(props) {
        super(props);
        this.timerTick = this.timerTick.bind(this);

        this.state = {
            isPlayerJoined: false,
            opponentName: null,
            isCopied: false,
            isNeedToRedirect: false
        };
    }

    async timerTick() {
        try {
            const opponentDto = await getOpponent(this.props.sessionId, this.props.player.playerId);
            if (opponentDto && opponentDto.playerName && opponentDto.playerName.length) {
                const playerName = opponentDto.playerName;
                this.setState({
                    isPlayerJoined: true,
                    opponentName: playerName
                });
                clearInterval(this.updateInterval);
            }
        } catch (e) {
            console.warn(e);
        }
    }

    componentDidMount() {
        this.updateInterval = setInterval(this.timerTick, 3000);
    }

    componentWillUnmount() {
        clearInterval(this.updateInterval);
    }

    handleCopy() {
        copy(this.props.sessionId);
        this.setState({
            isCopied: true
        });
    }

    disablePopup() {
        this.setState({
            isCopied: false
        });
    }

    render() {
        const opponent = <Row><p><b>{this.state.opponentName}</b> has joined.</p></Row>;
        const waiting = <Row><p>Waiting for your friend (opponent)...</p></Row>;
        const navigate = <Navigate to="/game/preparation" replace={true}/>;
        const elementToRender = this.state.isPlayerJoined ? opponent : waiting;

        if (this.state.isPlayerJoined) {
            setTimeout(() => this.setState({isNeedToRedirect: true}), 3000);
        }

        return (
            <>
                <Container className="text-center d-grid gap-4 w-75-ns p-3">
                    <Row>
                        <h3>Hello <b>{this.props.player.playerName}!</b></h3>
                    </Row>
                    <Row></Row>
                    <Row hidden={this.state.isPlayerJoined}>
                        <h5>Share Game ID with other player:</h5>
                        <Container>
                            <Alert variant="success">{this.props.sessionId}</Alert>
                            <Toast onClose={() => this.disablePopup()} show={this.state.isCopied} delay={2000} autohide>
                                <Toast.Body>Game ID is copied!</Toast.Body>
                            </Toast>
                            <Button variant="success" onClick={() => this.handleCopy()}>Copy to clipboard</Button>
                        </Container>
                    </Row>
                    {elementToRender}
                </Container>
                {this.state.isNeedToRedirect && navigate}
            </>
        );
    }
}

WaitForPlayersPage.propTypes = {
    player: PropTypes.object.isRequired,
    sessionId: PropTypes.string.isRequired
};

export default WaitForPlayersPage;