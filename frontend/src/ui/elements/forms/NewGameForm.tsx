import React, {ChangeEvent, MouseEvent} from "react";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import {isValidString} from "../../../utils/StringUtils";
import {NewGameFormResultDto} from "./FormTypes";

export type NewGameFormProps = {
    gameEditions: string[]
    onSubmitClicked: (formResults: NewGameFormResultDto) => void,
};

export type NewGameFormState = {
    isValid: boolean,
    playerName: string | null,
    gameEdition: string | null
};

class NewGameForm extends React.Component<NewGameFormProps, NewGameFormState> {
    constructor(props: NewGameFormProps) {
        super(props);
        const gameEdition = props.gameEditions.length > 0 ? props.gameEditions[0] : null;
        this.state = {
            isValid: false,
            playerName: null,
            gameEdition: gameEdition
        };
    }

    handleGameEditionChange(event: ChangeEvent<HTMLSelectElement>) {
        event.preventDefault();

        this.setState({gameEdition: event.target.value});
    }

    handlePlayerNameChange(event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
        event.preventDefault();

        const nameValue: string = event.target.value;
        const isValid: boolean = isValidString(nameValue) && isValidString(this.state.gameEdition);

        this.setState({isValid: isValid, playerName: nameValue});
    }

    handleOnSubmit(event: MouseEvent<HTMLButtonElement>) {
        event.preventDefault();

        const formResults: NewGameFormResultDto = {
            gameEdition: this.state.gameEdition || "",
            playerName: this.state.playerName || ""
        };
        this.props.onSubmitClicked(formResults);
    }

    render() {
        return (
            <>
                <Form>
                    <Form.Select onChange={(e) => this.handleGameEditionChange(e)}
                                 value={this.state.gameEdition || undefined}>
                        {this.props.gameEditions.map((gameEdition, index) => <option key={index}
                                                                                     value={`${gameEdition}`}>{gameEdition}</option>)}
                    </Form.Select>

                    <br/>

                    <Form.Group>
                        <Form.Label>Player Name</Form.Label>
                        <Form.Control onChange={(e) => this.handlePlayerNameChange(e)} type="text"
                                      placeholder="Enter your name"/>
                    </Form.Group>

                    <br/>

                    <Button variant="primary" type="submit" disabled={!this.state.isValid}
                            onClick={(e) => this.handleOnSubmit(e)}>
                        Start new game
                    </Button>
                </Form>
            </>
        );
    }
}

export default NewGameForm;