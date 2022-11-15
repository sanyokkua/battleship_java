import React from 'react';
import "../../app.css";
import Button from "react-bootstrap/Button";
import Status from "../common/Status";
import Field from "../field/Field";
import Row from "react-bootstrap/Row";
import Badge from "react-bootstrap/Badge";

class Finish extends React.Component {
    constructor(props) {
        super(props);
        this.state = {};
    }

    render() {
        const playerName = "Player Name";
        return (
            <>
                <Row>
                    <h1>Game is finished!</h1>
                    <p>
                        Player <Badge bg="success"> {playerName}</Badge> has win this game.
                    </p>
                    <Status badgeColor="warning"
                            badgeText="Player Name"
                            textInTheMiddle="Field of:"/>
                    <Field/>
                    <Status badgeColor="warning"
                            badgeText="Player Name"
                            textInTheMiddle="Field of:"/>
                    <Field/>
                    <Button variant="outline-primary" disabled={!this.state.isReady}
                            onClick={() => this.handleReady()}>Return to main page</Button>
                </Row>
            </>
        );
    }
}

export default Finish;