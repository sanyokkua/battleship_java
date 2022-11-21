import React from "react";
import Badge from "react-bootstrap/Badge";
import Button from "react-bootstrap/Button";
import Row from "react-bootstrap/Row";
import Status from "../../components/common/Status";
// import Field from "../../components/field/Field";
import {CellDto, PlayerBaseInfoDto, PlayerDto} from "../../logic/GameTypes";

export type FinishPageProps = {
    winner: PlayerBaseInfoDto,
    player: PlayerDto,
    opponent: PlayerBaseInfoDto,
    opponentField: CellDto[][],
};

function FinishPage(props: FinishPageProps) {
    return (
        <>
            <Row>
                <h1>Game is finished!</h1>
                <p>
                    Player <Badge bg="success"> {props.winner.playerName}</Badge> has win this game.
                </p>

                <Status badgeColor="warning"
                        badgeText={props.player.playerName}
                        textInTheMiddle="Field of:"/>
                {/*<Field/>*/}

                <Status badgeColor="warning"
                        badgeText={props.opponent.playerName}
                        textInTheMiddle="Field of:"/>
                {/*<Field/>*/}

                <Button variant="outline-primary">Return to main page</Button>
            </Row>
        </>
    );
}

export default FinishPage;