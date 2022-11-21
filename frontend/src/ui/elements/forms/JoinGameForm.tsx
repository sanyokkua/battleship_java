import React, {ChangeEvent, MouseEvent} from "react";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import {isValidString} from "../../../utils/StringUtils";
import {JoinGameFormResultDto} from "./FormTypes";

export type JoinGameFormProps = {
    onSubmitClicked: (formResults: JoinGameFormResultDto) => void,
};

export type JoinGameFormState = {
    isValidName: boolean,
    playerName: string | null,
    isValidSessionId: boolean,
    sessionId: string | null
};

class JoinGameForm extends React.Component<JoinGameFormProps, JoinGameFormState> {
    constructor(props: JoinGameFormProps) {
        super(props);
        this.state = {
            isValidName: false,
            playerName: null,
            isValidSessionId: false,
            sessionId: null
        };
    }

    handleOnNameChange(event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
        event.preventDefault();

        const playerName = event.target.value;
        const isValidName = isValidString(playerName);

        this.setState({
            isValidName: isValidName,
            playerName: playerName
        });
    }

    handleOnGameIdChange(event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
        event.preventDefault();

        const sessionId = event.target.value;
        const isValidSessionId = isValidString(sessionId) && sessionId.length === 36; //36 => UUID length

        this.setState({
            isValidSessionId: isValidSessionId,
            sessionId: sessionId
        });
    }

    handleOnSubmit(event: MouseEvent<HTMLButtonElement>) {
        event.preventDefault();

        const formResults: JoinGameFormResultDto = {
            sessionId: this.state.sessionId || "",
            playerName: this.state.playerName || ""
        };
        this.props.onSubmitClicked(formResults);
    }

    render() {
        const isValidForm = this.state.isValidName && this.state.isValidSessionId;

        return (
            <>
                <Form>
                    <Form.Group>
                        <Form.Label>Player Name</Form.Label>
                        <Form.Control onChange={(e) => this.handleOnNameChange(e)} type="text"
                                      placeholder="Enter your name"/>
                    </Form.Group>

                    <br/>

                    <Form.Group>
                        <Form.Label>Game ID</Form.Label>
                        <Form.Control onChange={(e) => this.handleOnGameIdChange(e)} type="text"
                                      placeholder="Enter game ID"/>
                    </Form.Group>

                    <br/>

                    <Button variant="primary" type="submit" disabled={!isValidForm}
                            onClick={(e) => this.handleOnSubmit(e)}>
                        Join to the game
                    </Button>
                </Form>
            </>
        );
    }
}

export default JoinGameForm;