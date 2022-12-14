import React from "react";
import Button from "react-bootstrap/Button";
import Col from "react-bootstrap/Col";
import Container from "react-bootstrap/Container";
import ProgressBar from "react-bootstrap/ProgressBar";
import Row from "react-bootstrap/Row";
import Toast from "react-bootstrap/Toast";
import ToastContainer from "react-bootstrap/ToastContainer";
import {Navigate} from "react-router-dom";
import {CellDto, Coordinate, ResponseCreatedPlayerDto, ShipDirection, ShipDto} from "../../logic/ApplicationTypes";
import * as service2 from "../../services/BackendRequestService";
import {CellClickEventData} from "../elements/preparation/common/PreparationTypes";
import PrepareField from "../elements/preparation/PrepareField";
import ShipsList from "../elements/preparation/ShipsList";
import Status from "../elements/preparation/Status";

type PreparationPageProps = {
    player: ResponseCreatedPlayerDto,
    sessionId: string,
    onPageOpened: () => void
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
    private updateIntervals: NodeJS.Timer[];

    constructor(props: PreparationPageProps) {
        super(props);
        this.updateIntervals = [];

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

    removeAllIntervals() {
        for (let i = 0; i < this.updateIntervals.length; i++) {
            clearInterval(this.updateIntervals[i]);
        }
        this.updateIntervals = [];
    }

    setUpdateInterval() {
        this.removeAllIntervals();
        this.updateIntervals.push(setInterval(this.timerTick, 3000));
    }

    async timerTick() {
        try {
            const opponentDto = await service2.getOpponentInformation(this.props.sessionId, this.props.player.playerId);
            if (opponentDto) {
                this.setState({
                                  opponent: {
                                      name: opponentDto.playerName,
                                      ready: opponentDto.ready
                                  }
                              });
                if (opponentDto.ready) {
                    // Status from ready can't be changed, we do not need now checking state
                    this.removeAllIntervals();
                }
            }
        } catch (error) {
            //ignore
        }
    }

    async componentDidMount() {
        this.setUpdateInterval();
        this.loadData();
        this.props.onPageOpened();
    }

    componentWillUnmount() {
        this.removeAllIntervals();
    }

    loadData() {
        this.setState({isDataLoaded: false, ships: []});

        service2.getPreparationState(this.props.sessionId, this.props.player.playerId)
                .then(resp => {
                    const ships = resp.ships;
                    const field = resp.field;
                    const shipActive = ships.length ? ships[0] : null;
                    this.setState({
                                      ships: ships,
                                      chosenShip: shipActive,
                                      field: field,
                                      isDataLoaded: true
                                  });
                })
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
            await service2.removeShipFromField(this.props.sessionId, this.props.player.playerId, coordinate);
            this.loadData();
            return;
        }

        if (!this.state.chosenShip || !eventCell.direction) {
            return;
        }

        const shipId: string = this.state.chosenShip.shipId;
        const direction: ShipDirection = eventCell.direction;

        try {
            await service2.addShipToField(
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
            const result = await service2.startGame(this.props.sessionId, this.props.player.playerId);
            if (result && result.ready) {
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
                                              onCellClick={(clickCellEventData) => this.handleOnCellClicked(
                                                  clickCellEventData)}
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