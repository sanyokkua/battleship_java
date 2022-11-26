import React from "react";
import Alert from "react-bootstrap/Alert";
import Badge from "react-bootstrap/Badge";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import Spinner from "react-bootstrap/Spinner";
import Table from "react-bootstrap/Table";
import Toast from "react-bootstrap/Toast";
import ToastContainer from "react-bootstrap/ToastContainer";
import {Navigate} from "react-router-dom";
import {CellDto, Coordinate, GameplayStateDto, PlayerDto} from "../../logic/GameTypes";
import {getGameplayState, getLastUpdate, makeShot} from "../../services/PromiseGameService";
import GameplayField from "../elements/gameplay/GameplayField";


function getCellsColor(numberOfCells: number | null | undefined): string {
    if (!numberOfCells) {
        return "dark";
    }
    if (numberOfCells >= 70) {
        return "success";
    } else if (numberOfCells > 40 && numberOfCells < 70) {
        return "warning";
    } else {
        return "danger";
    }
}

function getShipsColor(numberOfShips: number | null | undefined): string {
    if (!numberOfShips) {
        return "dark";
    }
    if (numberOfShips >= 8) {
        return "success";
    } else if (numberOfShips > 3 && numberOfShips < 8) {
        return "warning";
    } else {
        return "danger";
    }
}

type GameplayPageProps = {
    sessionId: string,
    player: PlayerDto,
    onPageOpened: () => void
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
            if (gameplayStatus && gameplayStatus.isPlayerActive && gameplayStatus.isOpponentReady) {
                await this.updateGameplayInformation();
            } else {
                this.setUpdateInterval();
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
        this.props.onPageOpened();
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
                    if (stateData.isPlayerActive && stateData.isOpponentReady) {
                        this.removeAllIntervals();
                    } else {
                        this.removeAllIntervals();
                        this.setUpdateInterval();
                    }
                    if (stateData.hasWinner) {
                        this.setState({isNeedToRedirect: true});
                        this.removeAllIntervals();
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
                {(this.state.isLoading || this.state.isWaiting) && <ProgressBar animated now={100}/>}
                <div hidden={this.state.isWaiting}>
                    <Row>
                        <Table bordered size="sm">
                            <thead>
                            <tr>
                                <th>Player Name</th>
                                <th>Number Of Cells</th>
                                <th>Number Of Ships</th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                <td>{this.state.gameState?.playerName}</td>
                                <td>
                                    <Badge bg={getCellsColor(this.state.gameState?.playerNumberOfAliveCells)}>
                                        {this.state.gameState?.playerNumberOfAliveCells}
                                    </Badge>
                                </td>
                                <td>
                                    <Badge bg={getShipsColor(this.state.gameState?.playerNumberOfAliveShips)}>
                                        {this.state.gameState?.playerNumberOfAliveShips}
                                    </Badge>
                                </td>
                            </tr>
                            <tr>
                                <td>{this.state.gameState?.opponentName}</td>
                                <td>
                                    <Badge bg={getCellsColor(this.state.gameState?.opponentNumberOfAliveCells)}>
                                        {this.state.gameState?.opponentNumberOfAliveCells}
                                    </Badge>
                                </td>
                                <td>
                                    <Badge bg={getShipsColor(this.state.gameState?.opponentNumberOfAliveShips)}>
                                        {this.state.gameState?.opponentNumberOfAliveShips}
                                    </Badge>
                                </td>
                            </tr>
                            </tbody>
                        </Table>
                    </Row>
                    <Row className="text-center">
                        <Alert variant={this.state.gameState?.isPlayerActive ? "success" : "danger"}>
                            Now is a turn of the player: <b>{activePlayerName || ""}</b> <Spinner
                            hidden={this.state.gameState?.isPlayerActive} animation="grow" size="sm"/>
                        </Alert>
                    </Row>
                    <Row className="text-center">
                        <p>Field of <b>{this.state.gameState?.opponentName || ""}</b></p>
                        <GameplayField field={this.state.gameState?.opponentField || []}
                                       isReadOnly={isFieldDisabled}
                                       onCellClick={(cell) => this.onCellButtonClick(cell)}/>

                    </Row>
                    <Row>
                        <ToastContainer position="top-center">
                            <Toast onClose={() => this.setState({hasHit: false})}
                                   show={this.state.hasHit}
                                   delay={3000} autohide
                                   bg={"warning"}>
                                <Toast.Header>
                                    <strong className="me-auto">Hit</strong>
                                </Toast.Header>
                                <Toast.Body>You have made shot by ship!</Toast.Body>
                            </Toast>
                        </ToastContainer>
                    </Row>
                    <Row>
                        <br/>
                    </Row>
                    <Row className="text-center">
                        <p>Field of <b>{this.props.player.playerName}</b></p>
                        <GameplayField field={this.state.gameState?.playerField || []}
                                       isReadOnly={true}
                                       onCellClick={(cell) => console.log(cell)}/>
                    </Row>
                    <Row>
                        {this.state.isNeedToRedirect && <Navigate to="/game/results" replace={true}/>}
                    </Row>
                </div>
            </>
        );
    }
}

export default GameplayPage;