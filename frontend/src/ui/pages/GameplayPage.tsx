import React from "react";
import Row from "react-bootstrap/Row";
import {CellDto, Coordinate, GamePlayState, PlayerDto} from "../../logic/GameTypes";
import {loadGameplayData} from "../../utils/GameUtils";
import {getPlayer, makeShot} from "../../services/PromiseGameService";
import Status from "../elements/preparation/Status";
import GameplayField from "../elements/gameplay/GameplayField";
import {Navigate} from "react-router-dom";
import ProgressBar from "react-bootstrap/ProgressBar";
import Toast from "react-bootstrap/Toast";


type GameplayPageProps = {
    sessionId: string,
    player: PlayerDto,
}

type GameplayPageState = {
    isLoading: boolean,
    isWaiting: boolean,
    isErrorHappened: boolean,
    isNeedToRedirect: boolean,
    hasHit: boolean,
    gameState: GamePlayState | null
}


class GameplayPage extends React.Component<GameplayPageProps, GameplayPageState> {
    private updateIntervals: NodeJS.Timer[];

    constructor(props: GameplayPageProps) {
        super(props);
        this.updateIntervals = [];

        this.state = {
            isLoading: true,
            isWaiting: true,
            isErrorHappened: false,
            isNeedToRedirect: false,
            hasHit: false,
            gameState: null
        };
        this.timerTick = this.timerTick.bind(this);
    }

    async componentDidMount() {
        try {
            const currentPlayer = await getPlayer(this.props.sessionId, this.props.player.playerId);
            if (currentPlayer) {
                if (currentPlayer.isActive) {
                    await this.updateGameplayInformation();
                } else {
                    this.setUpdateInterval();
                }
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
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
        this.updateIntervals.push(setInterval(this.timerTick, 5000));
    }

    async updateGameplayInformation() {
        this.setState({isLoading: true}, async () => {
            try {
                const stateData = await loadGameplayData(this.props.sessionId, this.props.player.playerId);
                if (stateData) {
                    this.setState({gameState: stateData, isLoading: false, isWaiting: !stateData.opponent.isReady});
                }
            } catch (e) {
                this.setState({isErrorHappened: true});
            }
        });
    }

    async timerTick() {
        try {
            const currentPlayer = await getPlayer(this.props.sessionId, this.props.player.playerId);
            if (currentPlayer) {
                if (currentPlayer.isActive) {
                    this.removeAllIntervals();
                } else {
                    await this.updateGameplayInformation();
                }
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    componentWillUnmount() {
        this.removeAllIntervals();
    }

    async onCellButtonClick(cell: CellDto) {
        try {
            const coordinate: Coordinate = {row: cell.row, column: cell.col};
            const result = await makeShot(this.props.sessionId, this.props.player.playerId, coordinate);
            if (result && (result.shotResult === "HIT" || result.shotResult === "DESTROYED")) {
                this.setState({hasHit: true});
            } else {
                this.setUpdateInterval();
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    render() {
        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                <Row hidden={this.state.isWaiting}>

                    <Status badgeColor="danger"
                            badgeText={this.state.gameState?.activePlayer?.playerName || ""}
                            textInTheMiddle="Now is a turn of the player:"/>

                    <Status badgeColor="primary"
                            badgeText={this.state.gameState?.playerNumberOfAliveCells + "" || "0"}
                            textInTheMiddle="cells:"
                            highlightedTextColor="success"
                            highlightedText={this.props.player.playerName}/>

                    <Status badgeColor="primary"
                            badgeText={this.state.gameState?.opponentNumberOfAliveCells + "" || "0"}
                            textInTheMiddle="cells:"
                            highlightedTextColor="success"
                            highlightedText={this.state.gameState?.opponent?.playerName || ""}/>

                    <Status badgeColor="warning"
                            badgeText="In progress"
                            textInTheMiddle={"Field of: " + this.state.gameState?.opponent?.playerName || ""}/>

                    <GameplayField field={this.state.gameState?.opponentField || []}
                                   isReadOnly={this.state.gameState?.opponent.isActive || false}
                                   onCellClick={(cell) => this.onCellButtonClick(cell)}/>

                    <Status badgeColor="warning"
                            badgeText="In progress"
                            textInTheMiddle="status:"
                            highlightedTextColor="primary"
                            highlightedText={this.props.player.playerName}/>

                    <GameplayField field={this.state.gameState?.playerField || []}
                                   isReadOnly={true}
                                   onCellClick={(cell) => console.log(cell)}/>
                </Row>
                <Row>
                    <Toast onClose={() => this.setState({
                        hasHit: false
                    })} show={this.state.hasHit} delay={2000} autohide bg={"warning"}>
                        <Toast.Header>
                            <strong className="me-auto">Hit</strong>
                        </Toast.Header>
                        <Toast.Body>You have made shot by ship!</Toast.Body>
                    </Toast>
                </Row>
                <Row>
                    {this.state.isNeedToRedirect && <Navigate to="/game/results" replace={true}/>}
                </Row>
            </>
        );
    }
}

export default GameplayPage;