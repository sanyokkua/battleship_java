import React from "react";
import Alert from "react-bootstrap/Alert";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import {Navigate} from "react-router-dom";
import * as gameUtils from "../../utils/GameUtils";
import JoinGameForm from "../elements/forms/JoinGameForm";
import {JoinGameFormResultDto} from "../elements/forms/FormTypes";
import {GameCreatedOrJoinedResult} from "./PagesCommonTypes";

export type JoinGamePageProps = {
    onPlayerIsJoined: (result: GameCreatedOrJoinedResult) => void
};
export type JoinGamePageState = {
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
            isLoading: true,
            formSubmitResult: {
                success: false,
                failure: false
            }
        };
    }

    async handleJoinGameClick(joinGameFormResult: JoinGameFormResultDto) {
        this.setState({
            isLoading: true
        });
        try {
            const playerDto = await gameUtils.createPlayer(joinGameFormResult.sessionId, joinGameFormResult.playerName);

            this.props.onPlayerIsJoined({
                sessionId: joinGameFormResult.sessionId,
                player: playerDto
            });

            this.setState({isLoading: false, formSubmitResult: {success: true}});
        } catch (error) {
            this.setState({formSubmitResult: {failure: true}});
        }
    }

    render() {
        const formSubmitResult = this.state.formSubmitResult;

        const isHidden = (!this.state.isLoading && !formSubmitResult.success && !formSubmitResult.failure);

        const form = isHidden ? null :
            <Container className="d-grid gap-4 w-75-ns p-3">
                <JoinGameForm onSubmitClicked={(data) => this.handleJoinGameClick(data)}/>
            </Container>;
        return (
            <>
                {this.state.isLoading && <ProgressBar animated now={100}/>}
                {this.state.formSubmitResult.success && <Navigate to="/game/preparation" replace={true}/>}
                {this.state.formSubmitResult.failure && <Alert variant="danger">Error happened</Alert>}
                {form}
            </>
        );
    }
}

export default JoinGamePage;