import React from "react";
import Row from "react-bootstrap/Row";
import Status from "../common/Status";
import Field from "../field/Field";


function PlayerField(props) {
    const playerName = props.playerName;
    const playerField = props.playerField;
    const clickHandler = props.onCellClickHandler;

    return (
        <>
            <Row>
                <Status badgeColor="warning" badgeText="Player Name" textInTheMiddle="Field of:"/>
                <Field/>
            </Row>
        </>
    );
}

export default PlayerField;