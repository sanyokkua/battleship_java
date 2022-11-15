import React from 'react';
import "../../app.css";
import Button from "react-bootstrap/Button";
import copy from 'copy-to-clipboard';
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Spinner from "react-bootstrap/Spinner";


class WaitForPlayersComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isPlayerJoined: false,
            opponentName: null
        };
    }

    handleStart(e) {
        e.preventDefault();
        this.setState({});
    }

    handleCopy() {
        copy(this.props.sessionId);
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

export default WaitForPlayersComponent;