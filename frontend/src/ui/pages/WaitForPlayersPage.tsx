import copy from "copy-to-clipboard";
import React from "react";
import Alert from "react-bootstrap/Alert";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import Toast from "react-bootstrap/Toast";
import {Navigate} from "react-router-dom";
import {getOpponent} from "../../services/PromiseGameService";
import {PlayerDto} from "../../logic/GameTypes";

type WaitForPlayersPageProps = {
    player: PlayerDto,
    sessionId: string
};

type WaitForPlayersPageState = {
    isCopied: boolean,
    isPlayerJoined: boolean,
    isNeedToRedirect: boolean
    opponentName: string | null,
};

class WaitForPlayersPage extends React.Component<WaitForPlayersPageProps, WaitForPlayersPageState> {
    private updateIntervals: NodeJS.Timer[];

    constructor(props: WaitForPlayersPageProps) {
        super(props);
        this.updateIntervals = [];

        this.state = {
            isCopied: false,
            isPlayerJoined: false,
            isNeedToRedirect: false,
            opponentName: null
        };

        this.timerTick = this.timerTick.bind(this);
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
                this.removeAllIntervals();
            }
        } catch (e) {
            console.warn(e);
        }
    }

    removeAllIntervals() {
        for (let i = 0; i < this.updateIntervals.length; i++) {
            clearInterval(this.updateIntervals[i]);
        }
        this.updateIntervals = [];
    }

    setUpdateInterval() {
        this.removeAllIntervals();
        this.updateIntervals.push(setInterval(this.timerTick, 3000));
    }

    componentDidMount() {
        this.setUpdateInterval();
    }

    componentWillUnmount() {
        this.removeAllIntervals();
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
        const playerJoined = this.state.isPlayerJoined;

        const opponent = <p><b>{this.state.opponentName}</b> has joined.</p>;
        const waiting = <p>Waiting for your friend (opponent)...</p>;
        const elementToRender = playerJoined ? opponent : waiting;

        if (playerJoined) {
            setTimeout(() => this.setState({isNeedToRedirect: true}), 3000);
        }

        return (
            <>
                <Container className="text-center d-grid gap-4 w-75-ns p-3">
                    <Row>
                        <h3>Hello <b>{this.props.player.playerName}!</b></h3>
                    </Row>
                    <Row></Row>
                    <Row hidden={playerJoined}>
                        <h5>Share Game ID with other player:</h5>
                        <Container>
                            <Alert variant="success">{this.props.sessionId}</Alert>
                            <Toast onClose={() => this.disablePopup()} show={this.state.isCopied} delay={2000} autohide>
                                <Toast.Body>Game ID is copied!</Toast.Body>
                            </Toast>
                            <Button variant="success" onClick={() => this.handleCopy()}>Copy to clipboard</Button>
                        </Container>
                    </Row>
                    <Row>
                        {elementToRender}
                    </Row>
                </Container>

                {this.state.isNeedToRedirect && <Navigate to="/game/preparation" replace={true}/>}
            </>
        );
    }
}

export default WaitForPlayersPage;