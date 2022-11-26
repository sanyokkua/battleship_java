import React from "react";
import PrepCell from "./PrepCell";
import {CellDto, ShipDto} from "../../../logic/GameTypes";
import {CellClickEventData} from "./common/PreparationTypes";

type PrepareFieldProps = {
    isReadOnly: boolean,
    playerField: CellDto[][],
    onCellClick: (cellClickEventData: CellClickEventData) => void;
    getChosenShip: () => ShipDto | null
};

function PrepareField(props: PrepareFieldProps) {
    const field = props.playerField;
    const flatField = field.flat();

    return (
        <div className="grid_div" id="board_cells">
            {flatField
                .map(cell => <PrepCell key={`${cell.row}_${cell.col}`}
                                       cell={cell}
                                       getChosenShip={props.getChosenShip}
                                       onButtonClick={(event) => props.onCellClick(event)}/>)}
        </div>
    );
}

export default PrepareField;