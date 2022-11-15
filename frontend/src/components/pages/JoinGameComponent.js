import React from 'react';
import Form from 'react-bootstrap/Form';
import "../../app.css";
import Button from "react-bootstrap/Button";
import {isValidString} from "../../utils/StringUtils";

class JoinGameComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValidName: false,
            isValidSessionId: false,
            playerName: null,
            sessionId: null
        };
    }

    handleJoinGameClick(e) {
        e.preventDefault();
        this.setState({});
    }

    validateName(e) {
        const tempValue = e.target.value;
        const isValid = isValidString(tempValue)

        this.setState({
            isValidName: isValid,
            playerName: tempValue
        })
    }

    validateSessionId(e) {
        const tempValue = e.target.value;
        const isValid = isValidString(tempValue)

        this.setState({
            isValidSessionId: isValid,
            sessionId: tempValue
        })
    }

    render() {
        const isDisabled = !(this.state.isValidName && this.state.isValidSessionId)
        return (
            <Form>
                <Form.Group>
                    <Form.Label>Player Name</Form.Label>
                    <Form.Control onChange={(e) => this.validateName(e)} type="text" placeholder="Enter your name"/>
                </Form.Group>
                <Form.Group>
                    <Form.Label>Game ID</Form.Label>
                    <Form.Control onChange={(e) => this.validateSessionId(e)} type="text"
                                  placeholder="Enter session ID"/>
                </Form.Group>
                <br/>
                <Button variant="primary"
                        type="submit"
                        disabled={isDisabled}
                        onClick={(e) => this.handleJoinGameClick(e)}>
                    Submit
                </Button>
            </Form>
        );
    }
}

export default JoinGameComponent;