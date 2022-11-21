import React from "react";
import Button from "react-bootstrap/Button";
import ButtonGroup from "react-bootstrap/ButtonGroup";
import Dropdown from "react-bootstrap/Dropdown";
import DropdownButton from "react-bootstrap/DropdownButton";
import {ShipDto} from "../../../logic/GameTypes";

export type ButtonShipProps = {
    isActive: boolean,
    ship: ShipDto
};

function ButtonShip(props: ButtonShipProps) {
    const ship: ShipDto = props.ship;
    const buttonClass: string = props.isActive ? "btn btn-success btn-block" : "btn btn-outline-primary btn-block";

    let shipText: string = "";
    let renderedButton;

    for (let i = 0; i < ship.shipSize; i++) {
        shipText += "⊡";
    }

    if (ship.shipSize > 1) {
        switch (ship.shipDirection) {
            case "HORIZONTAL":
                shipText += "⇨";
                break;
            case "VERTICAL":
                shipText += "⇩";
                break;
            default:
                shipText += "⇨";
                break;
        }
        renderedButton = <ButtonGroup size="sm">
            <DropdownButton as={ButtonGroup} title={shipText} id="bg-nested-dropdown">
                <Dropdown.Item eventKey={ship.shipId + "1"}>{"HORIZONTAL"}</Dropdown.Item>
                <Dropdown.Item eventKey={ship.shipId + "2"}>{"VERTICAL"}</Dropdown.Item>
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


export default ButtonShip;