import PropTypes from "prop-types";
import React from "react";
import Button from "react-bootstrap/Button";
import Form from "react-bootstrap/Form";
import {isValidString} from "../../utils/StringUtils";

class JoinGameForm extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValidName: false,
            isValidSessionId: false,
            playerName: null,
            sessionId: null
        };
    }

    handleOnNameChange(e) {
        e.preventDefault();

        const tempValue = e.target.value;
        const isValid = isValidString(tempValue);

        this.setState({
            isValidName: isValid,
            playerName: tempValue
        });
    }

    handleOnGameIdChange(e) {
        e.preventDefault();

        const tempValue = e.target.value;
        const isValid = isValidString(tempValue) && tempValue.length === 36;

        this.setState({
            isValidSessionId: isValid,
            sessionId: tempValue
        });
    }

    handleOnSubmit(e) {
        e.preventDefault();

        this.props.onSubmitClicked({sessionId: this.state.sessionId, playerName: this.state.playerName});
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

JoinGameForm.propTypes = {
    onSubmitClicked: PropTypes.func.isRequired
};

export default JoinGameForm;