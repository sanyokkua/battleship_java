import React from "react";
import Cell from "./Cell";
import {CellDto} from "../../../logic/GameTypes";
import {OnCellClick} from "./common/GameplayTypes";


type GameplayFieldProps = {
    isReadOnly: boolean,
    field: CellDto[][],
    onCellClick: OnCellClick
};

function GameplayField(props: GameplayFieldProps) {
    const flatField = props.field.flat();

    return (
        <div className="grid_div" id="board_cells">
            {flatField
                .map(cell => <Cell key={`${cell.row}_${cell.col}`}
                                   cell={cell}
                                   isReadOnly={props.isReadOnly}
                                   onCellClick={(event) => props.onCellClick(event)}/>)}
        </div>

    );
}

export default GameplayField;