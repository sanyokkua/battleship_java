import React from "react";
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import {Navigate} from "react-router-dom";
import {getAvailableGameEditions} from "../../services/BackendRequestService";
import * as gameUtils from "../../utils/GameUtils";
import {NewGameFormResultDto} from "../elements/forms/common/FormTypes";
import NewGameForm from "../elements/forms/NewGameForm";
import {GameCreatedOrJoinedResult} from "./common/PagesCommonTypes";

type NewGamePageProps = {
    onNewGameSessionCreated: (result: GameCreatedOrJoinedResult) => void
};
type NewGamePageState = {
    isLoading: boolean,
    pageState: {
        gameEditions: string[]
    },
    formSubmitResult: {
        success?: boolean,
        failure?: boolean
    }
};

class NewGamePage extends React.Component<NewGamePageProps, NewGamePageState> {
    constructor(props: NewGamePageProps) {
        super(props);
        this.state = {
            isLoading: true,
            pageState: {
                gameEditions: []
            },
            formSubmitResult: {
                success: false,
                failure: false
            }
        };
    }

    async componentDidMount() {
        try {
            const gameEditionsDto = await getAvailableGameEditions();
            if (!gameEditionsDto || !gameEditionsDto.gameEditions || gameEditionsDto.gameEditions.length === 0) {
                this.setState({formSubmitResult: {failure: true}});
                return;
            }
            this.setState({
                              isLoading: false,
                              pageState: {
                                  gameEditions: gameEditionsDto.gameEditions
                              }
                          });
        } catch (e) {
            this.setState({formSubmitResult: {failure: true}});
        }
    }

    async handleNewGameClick(newGameFormResult: NewGameFormResultDto) {
        this.setState({
                          isLoading: true
                      });
        try {
            const sessionId = await gameUtils.createSessionAsync(newGameFormResult.gameEdition);
            const playerDto = await gameUtils.createPlayerAsync(sessionId, newGameFormResult.playerName);

            this.props.onNewGameSessionCreated({
                                                   sessionId: sessionId,
                                                   player: playerDto
                                               });

            this.setState({isLoading: false, formSubmitResult: {success: true}});
        } catch (error) {
            this.setState({formSubmitResult: {failure: true}});
        }
    }

    render() {
        const gameEditions = this.state.pageState.gameEditions;
        const isHidden = this.state.isLoading || gameEditions.length === 0;

        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                {this.state.formSubmitResult.success && <Navigate to="/game/wait" replace={true}/>}
                {this.state.formSubmitResult.failure && <Alert variant="danger">Error happened</Alert>}
                {!isHidden &&
                    <Container className="d-grid gap-4 w-75-ns p-3">
                        <NewGameForm onSubmitClicked={(data) => this.handleNewGameClick(data)}
                                     gameEditions={gameEditions}/>
                    </Container>}
            </>
        );
    }
}

export default NewGamePage;