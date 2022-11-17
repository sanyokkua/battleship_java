import copy from "copy-to-clipboard";
import PropTypes from "prop-types";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Spinner from "react-bootstrap/Spinner";
import Toast from "react-bootstrap/Toast";


class WaitForPlayersPage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isPlayerJoined: false,
            opponentName: null,
            isCopied: false
        };
    }

    handleStart(e) {
        e.preventDefault();
        this.setState({});
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
        const isDisabled = !(this.state.isPlayerJoined);
        const waitingElement = <Row>
            <p>Waiting for your friend (opponent)...</p>
        </Row>;

        const opponentElement = <Row>
            <p>{this.state.opponentName} has joined.</p>
        </Row>;

        const waitingTag = this.state.isPlayerJoined ? opponentElement : waitingElement;

        return (
            <Container>
                <Row>
                    <h3>Hello {this.props.player.playerName}!</h3>
                </Row>
                <br/>
                <Row>
                    <h5>Share Game ID with other player</h5>
                    <Alert variant="success">
                        {this.props.sessionId}
                    </Alert>
                    <Button variant="success" onClick={() => this.handleCopy()}>
                        Copy to clipboard
                    </Button>
                    <Toast onClose={() => this.disablePopup()} show={this.state.isCopied} delay={3000} autohide>
                        <Toast.Body>Game ID is copied!</Toast.Body>
                    </Toast>
                </Row>
                <br/>
                {waitingTag}
                <br/>
                <Row>
                    <Button variant="primary"
                            type="submit"
                            disabled={isDisabled}
                            onClick={() => this.handleStart()}>
                        <Spinner
                            as="span"
                            animation="border"
                            size="sm"
                            role="status"
                            aria-hidden="true"
                        /> {this.state.isPlayerJoined ? "Start" : "Waiting"}
                    </Button>
                </Row>
            </Container>
        );
    }
}

WaitForPlayersPage.propTypes = {
    player: PropTypes.shape({
        playerId: PropTypes.string.isRequired,
        playerName: PropTypes.string.isRequired,
        field: PropTypes.array.isRequired,
        shipsNotOnTheField: PropTypes.array.isRequired,
        allPlayerShips: PropTypes.array.isRequired,
        isActive: PropTypes.bool.isRequired,
        isWinner: PropTypes.bool.isRequired,
        isReady: PropTypes.bool.isRequired
    }).isRequired,
    sessionId: PropTypes.string.isRequired
};

export default WaitForPlayersPage;