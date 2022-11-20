import PropTypes from "prop-types";
import React from "react";
import Col from "react-bootstrap/Col";
import Row from "react-bootstrap/Row";
import ShipChoseButton from "./ShipChoseButton";

function ChoseShipList(props) {
    const ships = props.shipsList;
    const onShipIsChosen = (ship) => {
        props.onShipIsChosen(ship);
    };
    return (
        <Row className="">
            {ships.map(ship => <Col key={ship.shipId}><ShipChoseButton key={ship.shipId}
                                                                       isActive={ship.shipId === props.shipIdActive}
                                                                       ship={ship}
                                                                       onButtonClick={(ship => onShipIsChosen(ship))}
            /></Col>)}
        </Row>
    );
}

ChoseShipList.propTypes = {
    shipsList: PropTypes.arrayOf(PropTypes.shape({
        shipId: PropTypes.string.isRequired,
        shipType: PropTypes.string.isRequired,
        shipDirection: PropTypes.oneOf(["HORIZONTAL", "VERTICAL"]).isRequired,
        shipSize: PropTypes.number.isRequired
    })),
    onShipIsChosen: PropTypes.func.isRequired,
    shipIdActive: PropTypes.string
};

export default ChoseShipList;