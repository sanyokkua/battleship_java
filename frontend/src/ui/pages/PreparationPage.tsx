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
import {CellClickEventData} from "../elements/preparation/PreparationTypes";

function shipComparator(ship1: ShipDto, ship2: ShipDto) {
    if (ship1.shipSize > ship2.shipSize) {
        return 1;
    }
    if (ship1.shipSize < ship2.shipSize) {
        return -1;
    }
    return 0;
}

export type PreparationPageProps = {
    player: PlayerDto,
    sessionId: string
};

export type PreparationPageState = {
    isDataLoaded: boolean,
    opponent: {
        name: string,
        ready: boolean
    },
    ships: ShipDto[],
    field: CellDto[][],
    chosenShip: ShipDto | null,
    onStartButtonClicked: boolean
};

class PreparationPage extends React.Component<PreparationPageProps, PreparationPageState> {
    private updateInterval: NodeJS.Timer | null;

    constructor(props: PreparationPageProps) {
        super(props);
        this.updateInterval = null;

        this.state = {
            isDataLoaded: false,
            opponent: {
                name: "",
                ready: false
            },
            ships: [],
            field: [],
            chosenShip: null,
            onStartButtonClicked: false
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
            console.warn(e);
        }
        this.loadData();

    }

    render() {
        const dataIsNotLoaded = !this.state.isDataLoaded;
        const opponentStatus = this.state.opponent.ready ? "Ready" : "In progress";

        const content = <Container className="text-center" hidden={dataIsNotLoaded}>
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
                                  isReadOnly={false}/>
                </Col>
            </Row>
        </Container>;

        return (
            <>
                {dataIsNotLoaded && <ProgressBar animated now={100}/>}
                {this.state.onStartButtonClicked && <Navigate to="/game/gameplay" replace={true}/>}
                {this.state.isDataLoaded && content}
            </>
        );
    }
}


export default PreparationPage;