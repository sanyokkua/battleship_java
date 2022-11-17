import React from "react";
import Badge from "react-bootstrap/Badge";
import Button from "react-bootstrap/Button";
import Row from "react-bootstrap/Row";
import Status from "../common/Status";
import Field from "../field/Field";


function FinishPage(props) {
    const playerName = "Player Name";
    const opponentField = null;
    const playerField = null;

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

                <Button variant="outline-primary">Return to main page</Button>
            </Row>
        </>
    );
}

export default FinishPage;