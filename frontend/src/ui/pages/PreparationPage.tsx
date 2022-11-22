import React from "react";
import Col from "react-bootstrap/Col";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import {Navigate} from "react-router-dom";
import * as service from "../../services/PromiseGameService";
import ShipsList from "../elements/preparation/ShipsList";
import Status from "../elements/preparation/Status";
import PrepareField from "../elements/preparation/PrepareField";
import {CellDto, Coordinate, PlayerDto, ShipDirection, ShipDto} from "../../logic/GameTypes";
import {CellClickEventData} from "../elements/preparation/common/PreparationTypes";
import {shipComparator} from "../../utils/GameUtils";
import ToastContainer from "react-bootstrap/ToastContainer";
import Toast from "react-bootstrap/Toast";
import Button from "react-bootstrap/Button";

type PreparationPageProps = {
    player: PlayerDto,
    sessionId: string
};

type PreparationPageState = {
    isDataLoaded: boolean,
    isErrorOnShipAdd: boolean,
    isErrorOnReadyButtonClick: boolean,
    opponent: {
        name: string,
        ready: boolean
    },
    ships: ShipDto[],
    field: CellDto[][],
    chosenShip: ShipDto | null,
    playerMadeIsReady: boolean
};

class PreparationPage extends React.Component<PreparationPageProps, PreparationPageState> {
    private updateInterval: NodeJS.Timer | null;

    constructor(props: PreparationPageProps) {
        super(props);
        this.updateInterval = null;

        this.state = {
            isDataLoaded: false,
            isErrorOnShipAdd: false,
            isErrorOnReadyButtonClick: false,
            opponent: {
                name: "",
                ready: false
            },
            ships: [],
            field: [],
            chosenShip: null,
            playerMadeIsReady: false
        };

        this.timerTick = this.timerTick.bind(this);
    }

    async timerTick() {
        try {
            const opponentDto = await service.getOpponent(this.props.sessionId, this.props.player.playerId);
            if (opponentDto) {
                this.setState({
                    opponent: {
                        name: opponentDto.playerName,
                        ready: opponentDto.isReady
                    }
                });
                if (opponentDto.isReady) {
                    if (this.updateInterval) {
                        // Status from ready can't be changed, we do not need now checking state
                        clearInterval(this.updateInterval);
                    }
                }
            }
        } catch (error) {
            //ignore
        }
    }

    async componentDidMount() {
        this.updateInterval = setInterval(this.timerTick, 3000);
        this.loadData();
    }

    componentWillUnmount() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
    }

    loadData() {
        this.setState({isDataLoaded: false, ships: []});

        service.getPrepareShipsList(this.props.sessionId, this.props.player.playerId)
            .then(ships => {
                ships.sort(shipComparator);
                const shipActive = ships.length ? ships[0] : null;
                this.setState({ships: ships, chosenShip: shipActive});
            })
            .catch(console.warn);

        service.getField(this.props.sessionId, this.props.player.playerId)
            .then(field => this.setState({field: field, isDataLoaded: true}))
            .catch(console.warn);
    }

    handleOnShipChosen(ship: ShipDto) {
        if (ship) {
            this.setState({
                chosenShip: ship
            });
        } else {
            throw new Error("Ship is null or undefined");
        }
    }

    async handleOnCellClicked(eventCell: CellClickEventData): Promise<void> {
        if (!eventCell) {
            return;
        }

        const coordinate: Coordinate = {row: eventCell.cell.row, column: eventCell.cell.col};

        if (eventCell.isDelete) {
            await service.removeShipFromField(this.props.sessionId, this.props.player.playerId, coordinate);
            this.loadData();
            return;
        }

        if (!this.state.chosenShip || !eventCell.direction) {
            return;
        }

        const shipId: string = this.state.chosenShip.shipId;
        const direction: ShipDirection = eventCell.direction;

        try {
            await service.addShipToField(
                this.props.sessionId,
                this.props.player.playerId,
                shipId,
                coordinate,
                direction
            );
        } catch (e) {
            this.setState({isErrorOnShipAdd: true});
        }
        this.loadData();

    }

    async handleOnReadyButtonClick() {
        try {
            const result = await service.startGame(this.props.sessionId, this.props.player.playerId);
            if (result && result.isReady) {
                this.setState({playerMadeIsReady: true});
            }
        } catch (e) {
            this.setState({isErrorOnReadyButtonClick: true});
        }
    }

    render() {
        const dataIsNotLoaded = !this.state.isDataLoaded;
        const opponentStatus = this.state.opponent.ready ? "Ready" : "In progress";
        const showReadyButton = this.state.ships.length === 0;
        return (
            <>
                {dataIsNotLoaded && <ProgressBar animated now={100}/>}
                {this.state.playerMadeIsReady && <Navigate to="/game/gameplay" replace={true}/>}
                {this.state.isDataLoaded &&
                    <Container className="text-center" hidden={dataIsNotLoaded}>
                        <Row className="text-center">
                            <Status badgeColor="warning" badgeText={opponentStatus}
                                    textInTheMiddle="status:"
                                    highlightedTextColor="primary"
                                    highlightedText={this.state.opponent.name}/>
                        </Row>
                        <Row>
                            <Col>
                                <ShipsList shipsList={this.state.ships}
                                           onShipIsChosen={(ship) => this.handleOnShipChosen(ship)}
                                           activeShipId={this.state.chosenShip?.shipId || null}
                                />
                            </Col>
                            <Col>
                                <PrepareField playerField={this.state.field}
                                              onCellClick={(clickCellEventData) => this.handleOnCellClicked(clickCellEventData)}
                                              isReadOnly={false}
                                              getChosenShip={() => this.state.chosenShip}/>
                            </Col>
                        </Row>
                        <Row hidden={!showReadyButton}>
                            <Button variant={"success"} disabled={!showReadyButton}
                                    onClick={() => this.handleOnReadyButtonClick()}>Ready to go!</Button>
                        </Row>
                        <Row>
                            <ToastContainer className="p-3" position="bottom-end">
                                <Toast onClose={() => this.setState({isErrorOnShipAdd: false})}
                                       show={this.state.isErrorOnShipAdd}
                                       delay={2000}
                                       bg={"danger"}
                                       autohide>
                                    <Toast.Header closeButton={true}>
                                        <strong className="me-auto">Error happened</strong>
                                    </Toast.Header>
                                    <Toast.Body className={"text-white"}>Ship can't be added!</Toast.Body>
                                </Toast>
                            </ToastContainer>

                            <ToastContainer className="p-3" position="bottom-end">
                                <Toast onClose={() => this.setState({isErrorOnReadyButtonClick: false})}
                                       show={this.state.isErrorOnReadyButtonClick}
                                       delay={5000}
                                       bg={"danger"}
                                       autohide>
                                    <Toast.Header closeButton={true}>
                                        <strong className="me-auto">Error happened</strong>
                                    </Toast.Header>
                                    <Toast.Body className={"text-white"}>Player can't be made ready!</Toast.Body>
                                </Toast>
                            </ToastContainer>
                        </Row>
                    </Container>}
            </>
        );
    }
}


export default PreparationPage;