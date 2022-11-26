import React from "react";
import Container from "react-bootstrap/Container";
import ButtonShip from "./ButtonShip";
import {ShipDto} from "../../../logic/GameTypes";

type ShipsListProps = {
    shipsList: ShipDto[],
    activeShipId: string | null,
    onShipIsChosen: (ship: ShipDto) => void;
};

function ShipsList(props: ShipsListProps) {
    return (
        <Container className="row align-items-center justify-content-center">
            <div>
                {props.shipsList.map(ship => <ButtonShip key={ship.shipId}
                                                         isActive={props.activeShipId === ship.shipId}
                                                         ship={ship}
                                                         onButtonClick={props.onShipIsChosen}/>)}
            </div>
        </Container>
    );
}

export default ShipsList;