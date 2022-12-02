import React from "react";
import {CellDto, ShipDto} from "../../../logic/ApplicationTypes";
import {CellClickEventData} from "./common/PreparationTypes";
import PrepCell from "./PrepCell";

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