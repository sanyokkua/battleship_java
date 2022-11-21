import PropTypes from "prop-types";
import React from "react";

function Field(props: any) {
    const field = props.playerField;
    const flatField = field.flat();

    // const renderedCells = flatField.map(cell => {
    //     const id = `${cell.row}_${cell.col}`;
    //
    //     const onClickHandler = ({cell, direction}) => {
    //         props.onCellClick({cell, direction});
    //     };
    //
    //     return <FieldCell key={id} cell={cell} stage={props.stage} onButtonClick={(event) => onClickHandler(event)}
    //                       isAlwaysDisabled={props.is}/>;
    // });

    return (
        <div className="grid_div" id="board_cells">
            {}
        </div>
    );
}

Field.propTypes = {
    stage: PropTypes.oneOf(["prepare", "gameplay"]).isRequired,
    isReadOnly: PropTypes.bool.isRequired,
    playerField: PropTypes.array,
    onCellClick: PropTypes.func
};

export default Field;