import React from "react";
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import {Navigate} from "react-router-dom";
import * as gameUtils from "../../utils/GameUtils";
import {JoinGameFormResultDto} from "../elements/forms/common/FormTypes";
import JoinGameForm from "../elements/forms/JoinGameForm";
import {GameCreatedOrJoinedResult} from "./common/PagesCommonTypes";

type JoinGamePageProps = {
    onPlayerIsJoined: (result: GameCreatedOrJoinedResult) => void
};
type JoinGamePageState = {
    isLoading: boolean,
    formSubmitResult: {
        success?: boolean,
        failure?: boolean
    }
};

class JoinGamePage extends React.Component<JoinGamePageProps, JoinGamePageState> {
    constructor(props: JoinGamePageProps) {
        super(props);
        this.state = {
            isLoading: false,
            formSubmitResult: {
                success: false,
                failure: false
            }
        };
    }

    async handleJoinGameClick(joinGameFormResult: JoinGameFormResultDto) {
        this.setState({
                          isLoading: true
                      }, async () => {
            try {
                const playerDto = await gameUtils.createPlayerAsync(joinGameFormResult.sessionId,
                                                                    joinGameFormResult.playerName);

                this.props.onPlayerIsJoined({
                                                sessionId: joinGameFormResult.sessionId,
                                                player: playerDto
                                            });

                this.setState({isLoading: false, formSubmitResult: {success: true}});
            } catch (error) {
                this.setState({formSubmitResult: {failure: true}});
            }
        });
    }

    render() {
        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                {this.state.formSubmitResult.success && <Navigate to="/game/preparation" replace={true}/>}
                {this.state.formSubmitResult.failure && <Alert variant="danger">Error happened</Alert>}
                {!this.state.isLoading &&
                    <Container className="d-grid gap-4 w-75-ns p-3">
                        <JoinGameForm onSubmitClicked={(data) => this.handleJoinGameClick(data)}/>
                    </Container>
                }
            </>
        );
    }
}

export default JoinGamePage;