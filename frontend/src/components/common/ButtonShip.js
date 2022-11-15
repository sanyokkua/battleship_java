import React from 'react';
import PropTypes from 'prop-types'
import Button from 'react-bootstrap/Button'
import ButtonGroup from "react-bootstrap/ButtonGroup";
import DropdownButton from "react-bootstrap/DropdownButton";
import Dropdown from "react-bootstrap/Dropdown";


const active = "btn btn-success btn-block";
const inactive = "btn btn-outline-primary btn-block";
const direction_vertical = "⇩";
const direction_horizontal = "⇨";
const shipSymbol = "⊡";


function ButtonShip(props) {
    const isActive = props.isActive;
    const ship = props.ship;
    const buttonClass = isActive ? active : inactive;
    let shipText = "";
    let renderedButton = null;

    for (let i = 0; i < ship.shipSize; i++) {
        shipText += shipSymbol;
    }

    if (ship.shipSize > 1) {
        switch (ship.shipDirection) {
            case "HORIZONTAL":
                shipText += direction_horizontal;
                break;
            case "VERTICAL":
                shipText += direction_vertical;
                break;
            default:
                shipText += direction_horizontal;
                break;
        }
        renderedButton = <ButtonGroup size="sm">
            <DropdownButton as={ButtonGroup} title={shipText} id="bg-nested-dropdown">
                <Dropdown.Item eventKey={ship.shipId + "1"}>{direction_horizontal}</Dropdown.Item>
                <Dropdown.Item eventKey={ship.shipId + "2"}>{direction_vertical}</Dropdown.Item>
            </DropdownButton>
        </ButtonGroup>;
    } else {
        renderedButton = <Button className={buttonClass}>{shipText}</Button>;
    }

    return (
        <>
            {renderedButton}
        </>
    );
}

ButtonShip.propTypes = {
    isActive: PropTypes.bool.isRequired,
    ship: PropTypes.shape({
        shipId: PropTypes.string.isRequired,
        shipType: PropTypes.string.isRequired,
        shipDirection: PropTypes.oneOf(["HORIZONTAL", "VERTICAL"]).isRequired,
        shipSize: PropTypes.number.isRequired,
    })

}

export default ButtonShip;