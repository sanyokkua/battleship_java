import React from "react";
import Cell from "./Cell";

const fieldTmp = [];
for (let i = 0; i < 10; i++) {
    const row = [];
    for (let j = 0; j < 10; j++) {
        row.push({
            row: i,
            col: j,
            ship: {
                shipId: "shipId" + i + j,
                shipType: "TYPE",
                shipDirection: "VERTICAL",
                shipSize: Math.floor(Math.random() * 4) + 1
            },
            hasShot: false,
            isAvailable: true
        });
    }
    fieldTmp.push(row);
}

class Field extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            playerField: fieldTmp
        };
    }

    render() {
        const field = this.state.playerField;
        const cells = [];

        for (let i = 0; i < field.length; i++) {
            const row = field[i];
            for (let j = 0; j < row.length; j++) {
                cells.push(field[i][j]);
                field[i][j].value = "" + i + "," + j;
            }
        }

        const renderedCells = cells.map(cell => {
            const ship = cell.ship;
            const shot = cell.hasShot;
            const isAvailable = cell.isAvailable;
            let style = "empty";
            if (shot && ship) {
                style = "hit";
            } else if (ship) {
                style = "ship";
            } else if (shot) {
                style = "miss";
            } else if (isAvailable) {
                style = "empty";
            } else {
                style = "not_available";
            }
            let isDisabled = false;
            const id = `${cell.row}_${cell.col}`;
            return <Cell key={id} isDisabled={isDisabled} currentStyle={style}
                         onClick={() => this.props.onClick(cell)}/>;
        });

        return (
            <div className="grid_div" id="board_cells">
                {renderedCells}
            </div>
        );
    }
}

export default Field;