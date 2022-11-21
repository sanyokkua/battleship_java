import PropTypes from "prop-types";
import React from "react";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import {isValidString} from "../../utils/StringUtils";

class NewGameForm extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValid: false,
            playerName: null,
            gameEdition: props.gameEditions[0]
        };
    }

    handleGameEditionChange(e) {
        e.preventDefault();

        this.setState({
            gameEdition: e.target.value
        });
    }

    handlePlayerNameChange(e) {
        e.preventDefault();
        const nameValue = e.target.value;
        const isValid = isValidString(nameValue) && isValidString(this.state.gameEdition);

        this.setState({
            isValid: isValid,
            playerName: nameValue
        });
    }

    handleOnSubmit(e) {
        e.preventDefault();

        this.props.onSubmitClicked({gameEdition: this.state.gameEdition, playerName: this.state.playerName});
    }

    render() {
        const editionsList = this.props.gameEditions.map(
            (gameEdition, index) => {
                return <option key={index} value={`${gameEdition}`}>{gameEdition}</option>;
            });
        return (
            <>
                <Form>
                    <Form.Select onChange={(e) => this.handleGameEditionChange(e)} value={this.state.gameEdition}>
                        {editionsList}
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

NewGameForm.propTypes = {
    onSubmitClicked: PropTypes.func.isRequired,
    gameEditions: PropTypes.array.isRequired
};

export default NewGameForm;