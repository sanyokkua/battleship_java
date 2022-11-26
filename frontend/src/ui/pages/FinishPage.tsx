import React from "react";
import Badge from "react-bootstrap/Badge";
import Button from "react-bootstrap/Button";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import {Navigate} from "react-router-dom";
import {GameplayStateDto, PlayerBaseInfoDto, PlayerDto} from "../../logic/GameTypes";
import {getGameplayState, getWinner} from "../../services/PromiseGameService";
import GameplayField from "../elements/gameplay/GameplayField";

type FinishPageProps = {
    player: PlayerDto | null,
    sessionId: string | null,
    onPageOpened: () => void
};

type FinishPageState = {
    isLoading: boolean,
    isErrorHappened: boolean,
    isNeedToRedirect: boolean,
    gameState: GameplayStateDto | null
    winner: PlayerBaseInfoDto | null
};

class FinishPage extends React.Component<FinishPageProps, FinishPageState> {

    constructor(props: FinishPageProps) {
        super(props);
        this.state = {
            isLoading: true,
            isErrorHappened: false,
            isNeedToRedirect: false,
            gameState: null,
            winner: null
        };
    }

    async componentDidMount() {
        try {
            if (this.props.sessionId && this.props.player) {
                const gameplayStatus = await getGameplayState(this.props.sessionId, this.props.player.playerId);
                const winnerInfo: PlayerBaseInfoDto = await getWinner(this.props.sessionId);
                if (gameplayStatus && winnerInfo) {
                    this.setState({
                                      isLoading: false,
                                      isErrorHappened: false,
                                      gameState: gameplayStatus,
                                      winner: winnerInfo
                                  });
                } else {
                    this.setState({isErrorHappened: true});
                }
            } else {
                this.setState({isErrorHappened: true});
            }
        } catch (e) {
            this.setState({isErrorHappened: true});
        }
        this.props.onPageOpened();
    }

    handleOnButtonClick() {
        this.setState({isNeedToRedirect: true});
    }

    render() {
        return (
            <>
                <Row>
                    {this.state.isLoading && <ProgressBar animated now={100}/>}
                </Row>
                <Row hidden={this.state.isLoading} className="text-center">
                    <h1>Game is finished!</h1>
                    <p>
                        Player <Badge bg="success"> {this.state.winner?.playerName}</Badge> has win this game.
                    </p>
                </Row>
                <Row>
                    <p>Field of <b>{this.state.gameState?.opponentName || ""}</b></p>
                    <GameplayField field={this.state.gameState?.opponentField || []}
                                   isReadOnly={true}
                                   onCellClick={(cell) => console.log(cell)}/>
                </Row>
                <Row>
                    <br/>
                </Row>
                <Row>
                    <p>Field of <b>{this.state.gameState?.playerName || ""}</b></p>
                    <GameplayField field={this.state.gameState?.playerField || []}
                                   isReadOnly={true}
                                   onCellClick={(cell) => console.log(cell)}/>

                </Row>
                <Row>
                    <Button variant="outline-primary"
                            onClick={() => this.handleOnButtonClick()}>Return to main page</Button>
                </Row>
                <Row>
                    {this.state.isNeedToRedirect && <Navigate to="/" replace={true}/>}
                </Row>
            </>
        );
    }
}

export default FinishPage;