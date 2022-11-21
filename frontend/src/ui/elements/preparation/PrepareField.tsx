import React from "react";
import PrepCell from "./PrepCell";
import {CellDto} from "../../../logic/GameTypes";
import {CellClickEventData} from "./PreparationTypes";

type PrepareFieldProps = {
    isReadOnly: boolean,
    playerField: CellDto[][],
    onCellClick: (cellClickEventData: CellClickEventData) => void;
};

function PrepareField(props: PrepareFieldProps) {
    const field = props.playerField;
    const flatField = field.flat();

    return (
        <div className="grid_div" id="board_cells">
            {flatField
                .map(cell => <PrepCell key={`${cell.row}_${cell.col}`}
                                       cell={cell}
                                       onButtonClick={(event) => props.onCellClick(event)}/>)}
        </div>
    );
}

export default PrepareField;