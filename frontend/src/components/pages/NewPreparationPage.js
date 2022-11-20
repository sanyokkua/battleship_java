import PropTypes from "prop-types";
import React from "react";
import Col from "react-bootstrap/Col";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import {Navigate} from "react-router-dom";
import * as service from "../../services/PromiseGameService";
import ChoseShipList from "../common/ChoseShipList";
import Status from "../common/Status";
import PrepareField from "../field_components/PrepareField";

function shipComparator(ship1, ship2) {
    if (ship1.shipSize > ship2.shipSize) {
        return 1;
    }
    if (ship1.shipSize < ship2.shipSize) {
        return -1;
    }
    return 0;
}

class NewPreparationPage extends React.Component {
    constructor(props) {
        super(props);
        this.timerTick = this.timerTick.bind(this);
        this.state = {
            isDataLoaded: false,
            opponent: {
                name: null,
                ready: false
            },
            ships: null,
            field: null,
            chosenShip: null,
            onStartButtonClicked: false
        };
    }

    async componentDidMount() {
        this.updateInterval = setInterval(this.timerTick, 3000);
        this.loadData();
    }

    componentWillUnmount() {
        clearInterval(this.updateInterval);
    }

    timerTick() {
        service.getOpponent(this.props.sessionId, this.props.player.playerId)
               .then(opponentDto => {
                   this.setState({
                       opponent: {
                           name: opponentDto.playerName,
                           ready: opponentDto.isReady
                       }
                   });
               })
               .catch(e => console.warn(e));
    }

    handleOnShipChosen(ship) {
        if (ship) {
            this.setState({
                chosenShip: ship
            });
        } else {
            throw new Error("Ship is null or undefined");
        }
    }

    async handleOnCellClicked(eventCell) {
        if (eventCell) {
            const sessionId = this.props.sessionId;
            const playerId = this.props.player.playerId;
            const shipId = this.state.chosenShip && this.state.chosenShip.shipId ? this.state.chosenShip.shipId : "";
            const coordinate = {
                row: eventCell.row,
                column: eventCell.col
            };
            if (!eventCell.ship && eventCell.direction) {
                const direction = eventCell.direction;
                try {
                    const resultShip = await service.addShipToField(sessionId, playerId, shipId, coordinate, direction);
                    const shipsNew = this.state.ships
                                         .filter(ship => resultShip.removedShipId && resultShip.removedShipId === ship.shipId);
                    this.setState({ships: shipsNew});
                    if (resultShip.ship.shipId !== eventCell.ship.shipId) {
                        throw new Error("IDs are different");
                    }
                } catch (e) {
                    console.warn(e);
                }
            } else {
                await service.removeShipFromField(sessionId, playerId, coordinate);
            }
            this.loadData();
        }
    }

    loadData() {
        this.setState({isDataLoaded: false, ships: null});
        service.getOpponent(this.props.sessionId, this.props.player.playerId)
               .then(opponentDto => {
                   this.setState({
                       opponent: {
                           name: opponentDto.playerName,
                           ready: opponentDto.ready
                       }
                   });
                   return opponentDto;
               })
               .catch((e) => {
                   throw new Error("Failed Loading of Opponent");
               });
        service.getPrepareShipsList(this.props.sessionId, this.props.player.playerId)
               .then(ships => {
                   ships.sort(shipComparator);
                   const shipActive = ships.slice()
                                           .shift();
                   this.setState({ships: ships, chosenShip: shipActive});
                   return ships;
               })
               .catch((e) => {
                   throw new Error("Failed Loading of Opponent");
               });
        service.getField(this.props.sessionId, this.props.player.playerId)
               .then(field => this.setState({field: field, isDataLoaded: true}))
               .catch((e) => {
                   throw new Error("Failed Loading of Opponent");
               });
    }

    render() {
        const dataIsNotLoaded = !this.state.isDataLoaded;
        const opponentStatus = this.state.opponent.ready ? "Ready" : "In progress";

        let shipId = "";
        if (this.state.chosenShip && this.state.chosenShip.shipId) {
            shipId = this.state.chosenShip.shipId;
        }

        const content = <Container className="text-center" hidden={dataIsNotLoaded}>
            <Row className="text-center">
                <Status badgeColor="warning" badgeText={opponentStatus}
                        textInTheMiddle="status:"
                        highlightedTextColor="primary"
                        highlightedText={this.state.opponent.name}/>
            </Row>
            <Row>
                <Col>
                    <ChoseShipList shipsList={this.state.ships}
                                   onShipIsChosen={(ship) => this.handleOnShipChosen(ship)}
                                   shipIdActive={shipId}
                    />
                </Col>
                <Col>
                    <PrepareField playerField={this.state.field}
                                  onCellClick={(cell) => this.handleOnCellClicked(cell)} isReadOnly={false}/>
                </Col>
            </Row>
        </Container>;

        return (
            <>
                {this.state.onStartButtonClicked && <Navigate to="/game/gameplay" replace={true}/>}
                {dataIsNotLoaded && <ProgressBar animated now={100}/>}
                {this.state.isDataLoaded && content}
            </>
        );
    }
}

NewPreparationPage.propTypes = {
    player: PropTypes.object.isRequired,
    sessionId: PropTypes.string.isRequired
};

export default NewPreparationPage;