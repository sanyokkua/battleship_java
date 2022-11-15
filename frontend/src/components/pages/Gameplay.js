import React from 'react';
import "../../app.css";
import Button from "react-bootstrap/Button";
import Status from "../common/Status";
import Field from "../field/Field";
import Row from "react-bootstrap/Row";

class Gameplay extends React.Component {
    constructor(props) {
        super(props);
        this.state = {};
    }

    render() {
        return (
            <>
                <Row>
                    <Status badgeColor="danger"
                            badgeText="Player Name Who's turn"
                            textInTheMiddle="Now is a turn of the player:"/>
                    <Status badgeColor="primary"
                            badgeText="Number of cell"
                            textInTheMiddle="cells:"
                            highlightedTextColor="success"
                            highlightedText="PLAYER name"/>
                    <Status badgeColor="primary"
                            badgeText="Number of cell"
                            textInTheMiddle="cells:"
                            highlightedTextColor="success"
                            highlightedText="Opponent name"/>
                    <Status badgeColor="warning"
                            badgeText="In progress"
                            textInTheMiddle="Field of:"/>
                    <Field/>
                    <Status badgeColor="warning"
                            badgeText="In progress"
                            textInTheMiddle="status:"
                            highlightedTextColor="primary"
                            highlightedText="PLAYER"/>
                    <Field/>
                    <Button variant="outline-primary" disabled={!this.state.isReady}
                            onClick={() => this.handleReady()}>Update</Button>
                </Row>
            </>
        );
    }
}

export default Gameplay;