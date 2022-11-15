import React from 'react';
import PropTypes from 'prop-types'
import ButtonShip from "./ButtonShip";
import Container from "react-bootstrap/Container";

function ShipsList(props) {
    const ships = props.shipsList;

    return (
        <Container className="row align-items-center justify-content-center">
            <div>
                {ships.map(ship => <ButtonShip key={ship.shipId} isActive={false} ship={ship}></ButtonShip>)}
            </div>
        </Container>
    );
}

ShipsList.propTypes = {
    shipsList: PropTypes.arrayOf(PropTypes.shape({
        shipId: PropTypes.string.isRequired,
        shipType: PropTypes.string.isRequired,
        shipDirection: PropTypes.oneOf(["HORIZONTAL", "VERTICAL"]).isRequired,
        shipSize: PropTypes.number.isRequired,
    }))
}

export default ShipsList;