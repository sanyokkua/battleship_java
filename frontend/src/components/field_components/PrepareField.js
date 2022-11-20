import PropTypes from "prop-types";
import React from "react";
import PreparationCell from "./PreparationCell";

function PrepareField(props) {
    const field = props.playerField;
    const flatField = field.flat();
    const renderedCells = flatField.map(cell => {
        const id = `${cell.row}_${cell.col}`;
        return <PreparationCell key={id} cell={cell} onButtonClick={(event) => props.onCellClick(event)}/>;
    });
    return (
        <div className="grid_div" id="board_cells">
            {renderedCells}
        </div>
    );
}

PrepareField.propTypes = {
    isReadOnly: PropTypes.bool.isRequired,
    playerField: PropTypes.array,
    onCellClick: PropTypes.func
};

export default PrepareField;