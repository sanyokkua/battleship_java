import React from "react";
import Row from "react-bootstrap/Row";
import {CellDto, Coordinate, GamePlayState, PlayerDto} from "../../logic/GameTypes";
import {loadGameplayData} from "../../utils/GameUtils";
import {getPlayer, makeShot} from "../../services/PromiseGameService";
import Status from "../elements/preparation/Status";
import GameplayField from "../elements/gameplay/GameplayField";
import {Navigate} from "react-router-dom";
import ProgressBar from "react-bootstrap/ProgressBar";


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
    private updateInterval: NodeJS.Timer | null;

    constructor(props: GameplayPageProps) {
        super(props);
        this.updateInterval = null;

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

    async timerTick() {
        try {
            const currentPlayer = await getPlayer(this.props.sessionId, this.props.player.playerId);
            if (currentPlayer) {
                if (this.props.player.isActive) {
                    if (this.updateInterval) {
                        clearInterval(this.updateInterval);
                    }
                } else {
                    await this.updateGameplayInformation();
                }
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    async componentDidMount() {
        try {
            const currentPlayer = await getPlayer(this.props.sessionId, this.props.player.playerId);
            if (currentPlayer) {
                if (!this.props.player.isActive) {
                    this.setUpdateInterval();
                } else {
                    await this.updateGameplayInformation();
                }
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
    }

    componentWillUnmount() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
    }

    async updateGameplayInformation() {
        this.setState({isLoading: true}, async () => {
            try {
                const stateData = await loadGameplayData(this.props.sessionId, this.props.player.playerId);
                if (stateData) {
                    this.setState({gameState: stateData, isLoading: false});
                }
            } catch (e) {
                this.setState({isErrorHappened: true});
            }
        });
    }

    setUpdateInterval() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
        this.updateInterval = setInterval(this.timerTick, 5000);
    }

    async onCellButtonClick(cell: CellDto) {
        try {

        } catch (e) {
            this.setState({isErrorHappened: true});
        }
        const coordinate: Coordinate = {row: cell.row, column: cell.col};
        const result = await makeShot(this.props.sessionId, this.props.player.playerId, coordinate);
        if (result && (result.shotResult === "HIT" || result.shotResult === "DESTROYED")) {
            this.setState({hasHit: true});
        } else {
            this.setUpdateInterval();
        }
    }

    render() {
        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                <Row hidden={this.state.isLoading}>
                    {this.state.isWaiting && <ProgressBar animated now={100}/>}

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
                                   isReadOnly={this.state.gameState?.opponent.isActive || this.state.isWaiting}
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
                    {this.state.isNeedToRedirect && <Navigate to="/game/results" replace={true}/>}
                </Row>
            </>
        );
    }
}

export default GameplayPage;