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

class PreparationPage extends React.Component {
    constructor(props) {
        super(props);
        this.timerTick = this.timerTick.bind(this);
        this.state = {
            chosenShip: null,
            allShips: null,
            isOpponentReady: false,
            isAllShipsOnBoard: false,
            isLoadedInitialData: false,


            shipList: null,
            activeShipId: null,
            activeShip: null,

            playerField: null,
            lastClickedCell: null
        };
    }

    async componentDidMount() {
        this.updateInterval = setInterval(this.timerTick, 3000);
        await this.updateCurrentState();
    }

    componentWillUnmount() {
        clearInterval(this.updateInterval);
    }

    async timerTick() {
        try {
            const opponentDto = await service.getOpponent(this.props.sessionId, this.props.player.playerId);
            if (opponentDto && opponentDto.playerName && opponentDto.playerName.length) {
                const playerName = opponentDto.playerName;
                const isPlayerReady = opponentDto.isReady;
                this.setState({
                    opponentName: playerName,
                    isOpponentReady: isPlayerReady

                });
                if (isPlayerReady) {
                    clearInterval(this.updateInterval);
                }
                this.updateStatusOfInitialData();
            }
        } catch (e) {
            console.log(e);
        }
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
            const shipId = this.state.activeShipId;
            const row = eventCell.row;
            const col = eventCell.col;
            const coordinate = {
                row: row,
                column: col
            };
            if (eventCell.ship && eventCell.direction) {
                const direction = eventCell.direction;
                try {
                    const resultShip = await service.addShipToField(sessionId, playerId, shipId, coordinate, direction);
                    if (resultShip.ship.shipId !== eventCell.ship.shipId) {
                        throw new Error("IDs are different");
                    }
                    await this.updateCurrentState();
                } catch (e) {

                }
            } else {
                await service.removeShipFromField(sessionId, playerId, coordinate);
                await this.updateCurrentState();
            }
        }
    }


    async updateCurrentState() {
        const ships = await service.getPrepareShipsList(this.props.sessionId, this.props.player.playerId);
        const field = await service.getField(this.props.sessionId, this.props.player.playerId);

        ships.sort(shipComparator);
        const activeShip = ships.length ? ships[0] : null;

        this.setState({
            allShips: ships,
            chosenShip: activeShip,
            playerField: field
        });

        this.updateStatusOfInitialData();
    }

    updateStatusOfInitialData() {
        if (!this.state.isLoadedInitialData
            && this.state.opponentName && this.state.opponentName.length
            && this.state.shipList && this.state.shipList.length
            && this.state.playerField && this.state.playerField.length) {
            this.setState({
                isLoadedInitialData: true
            });
        }
    }


    handleOnClickReadyButton() {
        console.log("ready btn clicked");
    }

    render() {
        const navigate = <Navigate to="/game/gameplay" replace={true}/>;
        const progressBarLoading = <ProgressBar animated now={100}/>;

        const opponentName = this.state.opponentName;
        const opponentStatus = this.state.isOpponentReady ? "Ready" : "In progress";

        return (
            <>
                {!this.state.isLoadedInitialData && progressBarLoading}
                <Container className="text-center" hidden={this.state.isLoadedInitialData}>
                    <Row className="justify-content-md-center">
                        <Status badgeColor="warning" badgeText={opponentStatus}
                                textInTheMiddle="status:"
                                highlightedTextColor="primary"
                                highlightedText={opponentName}/>
                    </Row>
                    <Row>
                        <Col>
                            <ChoseShipList shipsList={this.state.shipList}
                                           onShipIsChosen={(ship) => this.handleOnShipChosen(ship)}
                                           shipIdActive={this.state.activeShipId}
                            />
                        </Col>
                        <Col>
                            <PrepareField playerField={this.state.playerField}
                                          onCellClick={(cell) => this.handleOnCellClicked(cell)}/>
                        </Col>
                    </Row>
                </Container>
                {this.state.isReady && navigate}
            </>
        );
    }
}

PreparationPage.propTypes = {
    player: PropTypes.object.isRequired,
    sessionId: PropTypes.string.isRequired
};

export default PreparationPage;