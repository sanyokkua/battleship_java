import React from "react";
import Button from "react-bootstrap/Button";
import {ShipDto} from "../../../logic/GameTypes";

type ButtonShipProps = {
    isActive: boolean,
    ship: ShipDto
    onButtonClick: (ship: ShipDto) => void
};

function ButtonShip(props: ButtonShipProps) {
    const ship: ShipDto = props.ship;
    const buttonClass: string = props.isActive ? "success" : "outline-primary";

    let shipText: string = "⊡";
    for (let i = 1; i < ship.shipSize; i++) {
        shipText += "⊡";
    }

    return (
        <>
            <Button variant={buttonClass} onClick={() => props.onButtonClick(props.ship)}>{shipText}</Button>
        </>
    );
}


export default ButtonShip;