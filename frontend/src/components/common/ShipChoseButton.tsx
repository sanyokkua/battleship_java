import PropTypes from "prop-types";
import React from "react";
import Button from "react-bootstrap/Button";
import ButtonGroup from "react-bootstrap/ButtonGroup";
import ButtonToolbar from "react-bootstrap/ButtonToolbar";

const active = "success";
const inactive = "outline-primary";
const shipSymbol = "⊡";

function ShipChoseButton(props) {
    const ship = props.ship;
    const buttonClass = props.isActive ? active : inactive;

    let buttons = [];

    for (let i = 0; i < ship.shipSize; i++) {
        buttons.push(<Button variant={buttonClass}
                             onClick={() => props.onButtonClick(props.ship)}>{shipSymbol}</Button>);
    }

    return (
        <>
            <ButtonToolbar>
                <ButtonGroup className="me-2">
                    {buttons.map(el => el)}
                </ButtonGroup>
            </ButtonToolbar>
        </>
    );
}

ShipChoseButton.propTypes = {
    onButtonClick: PropTypes.func.isRequired,
    isActive: PropTypes.bool.isRequired,
    ship: PropTypes.shape({
        shipId: PropTypes.string.isRequired,
        shipType: PropTypes.string.isRequired,
        shipDirection: PropTypes.oneOf(["HORIZONTAL", "VERTICAL"]).isRequired,
        shipSize: PropTypes.number.isRequired
    }).isRequired
};

export default ShipChoseButton;