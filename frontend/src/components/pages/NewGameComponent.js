import React from 'react';
import Form from 'react-bootstrap/Form';
import "../../app.css";
import Button from "react-bootstrap/Button";
import {isValidString} from "../../utils/StringUtils";

class NewGameComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isValid: false,
            playerName: null
        };
    }

    handleNewGameClick(e) {
        e.preventDefault();
        this.setState({});
    }

    validateName(e) {
        const tempValue = e.target.value;
        let isValid = isValidString(tempValue);
        this.setState({
            isValid: isValid,
            playerName: tempValue
        })
    }

    render() {
        return (
            <Form>
                <Form.Group>
                    <Form.Label>Player Name</Form.Label>
                    <Form.Control onChange={(e) => this.validateName(e)} type="text" placeholder="Enter your name"/>
                </Form.Group>
                <br/>
                <Button variant="primary"
                        type="submit"
                        disabled={!this.state.isValid}
                        onClick={(e) => this.handleNewGameClick(e)}>
                    Submit
                </Button>
            </Form>
        );
    }
}

export default NewGameComponent;