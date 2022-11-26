import React from "react";
import Row from "react-bootstrap/Row";
import {CellDto, Coordinate, GameplayStateDto, PlayerDto} from "../../logic/GameTypes";
import {getGameplayState, getLastUpdate, makeShot} from "../../services/PromiseGameService";
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
    lastUpdate: string,
    hasHit: boolean,
    gameState: GameplayStateDto | null
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
            lastUpdate: "",
            gameState: null
        };
        this.timerTick = this.timerTick.bind(this);
    }

    async componentDidMount() {
        try {
            const gameplayStatus = await getGameplayState(this.props.sessionId, this.props.player.playerId);
            if (gameplayStatus && gameplayStatus.isPlayerActive) {
                await this.updateGameplayInformation();
            } else {
                this.setUpdateInterval();
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
                const stateData = await getGameplayState(this.props.sessionId, this.props.player.playerId);
                if (stateData) {
                    this.setState({gameState: stateData, isLoading: false, isWaiting: !stateData.isOpponentReady});
                    if (stateData.isPlayerActive) {
                        this.removeAllIntervals();
                    } else {
                        this.removeAllIntervals();
                        this.setUpdateInterval();
                    }
                    if (stateData.hasWinner) {
                        this.setState({isNeedToRedirect: true});
                    }
                }
            } catch (e) {
                this.setState({isErrorHappened: true});
            }
        });
    }

    async timerTick() {
        try {
            const lastUpdate = await getLastUpdate(this.props.sessionId, this.props.player.playerId);
            if (lastUpdate && lastUpdate.lastId !== this.state.lastUpdate) {
                this.setState({lastUpdate: lastUpdate.lastId});
                await this.updateGameplayInformation();
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    componentWillUnmount() {
        this.removeAllIntervals();
    }

    async onCellButtonClick(cell: CellDto) {
        this.setState({isLoading: true});
        try {
            const coordinate: Coordinate = {row: cell.row, column: cell.col};
            const result = await makeShot(this.props.sessionId, this.props.player.playerId, coordinate);
            if (result && (result.shotResult === "HIT" || result.shotResult === "DESTROYED")) {
                await this.updateGameplayInformation();
                this.setState({hasHit: true});
            } else {
                await this.updateGameplayInformation();
                this.setUpdateInterval();
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    render() {
        const activePlayerName = this.state.gameState?.isPlayerActive ? this.state.gameState?.playerName :
            this.state.gameState?.opponentName;

        const isFieldDisabled = !this.state.gameState?.isPlayerActive || this.state.isLoading;

        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                <Row hidden={this.state.isWaiting}>

                    <Status badgeColor="danger"
                            badgeText={activePlayerName || ""}
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
                            highlightedText={this.state.gameState?.opponentName || ""}/>

                    <Status badgeColor="warning"
                            badgeText="In progress"
                            textInTheMiddle={"Field of: " + this.state.gameState?.opponentName || ""}/>

                    <GameplayField field={this.state.gameState?.opponentField || []}
                                   isReadOnly={isFieldDisabled}
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