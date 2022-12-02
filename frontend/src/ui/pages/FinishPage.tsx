import React from "react";
import Badge from "react-bootstrap/Badge";
import Button from "react-bootstrap/Button";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import {Navigate} from "react-router-dom";
import {ResponseCreatedPlayerDto, ResponseGameplayStateDto} from "../../logic/ApplicationTypes";
import {getGameStateForPlayer} from "../../services/BackendRequestService";
import GameplayField from "../elements/gameplay/GameplayField";

type FinishPageProps = {
    player: ResponseCreatedPlayerDto | null,
    sessionId: string | null,
    onPageOpened: () => void
};

type FinishPageState = {
    isLoading: boolean,
    isErrorHappened: boolean,
    isNeedToRedirect: boolean,
    gameplayStatus: ResponseGameplayStateDto | null
};

class FinishPage extends React.Component<FinishPageProps, FinishPageState> {

    constructor(props: FinishPageProps) {
        super(props);
        this.state = {
            isLoading: true,
            isErrorHappened: false,
            isNeedToRedirect: false,
            gameplayStatus: null
        };
    }

    async componentDidMount() {
        try {
            if (this.props.sessionId && this.props.player) {
                const gameplayStatus = await getGameStateForPlayer(this.props.sessionId, this.props.player.playerId);
                if (gameplayStatus && gameplayStatus.hasWinner) {
                    this.setState({
                                      isLoading: false,
                                      isErrorHappened: false,
                                      gameplayStatus: gameplayStatus
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
                    {this.state.gameplayStatus?.isPlayerWinner ? <h1>You win!</h1> : <h1>You lose!</h1>}
                    <p>
                        Player <Badge bg="success"> {this.state.gameplayStatus?.winnerPlayerName}</Badge> has win this
                        game.
                    </p>
                </Row>
                <Row>
                    <p>Field of <b>{this.state.gameplayStatus?.opponentName || ""}</b></p>
                    <GameplayField field={this.state.gameplayStatus?.opponentField || []}
                                   isReadOnly={true}
                                   onCellClick={(cell) => console.log(cell)}/>
                </Row>
                <Row>
                    <br/>
                </Row>
                <Row>
                    <p>Field of <b>{this.state.gameplayStatus?.playerName || ""}</b></p>
                    <GameplayField field={this.state.gameplayStatus?.playerField || []}
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