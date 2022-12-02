import React from "react";
import Button from "react-bootstrap/Button";
import {CellDto} from "../../../logic/ApplicationTypes";
import {OnCellClick} from "./common/GameplayTypes";

type CellProps = {
    cell: CellDto,
    isReadOnly: boolean,
    onCellClick: OnCellClick
};

function Cell(props: CellProps) {
    let btnClassName: string | null;
    let isDisabled: boolean;
    if (props.cell.ship && props.cell.hasShot) {
        btnClassName = "danger bg-opacity-25";
        isDisabled = true;
    } else if (props.cell.ship) {
        btnClassName = "success";
        isDisabled = true;
    } else if (props.cell.hasShot) {
        isDisabled = true;
        btnClassName = "secondary";
    } else {
        isDisabled = false;
        btnClassName = "outline-primary";
    }

    return (
        <>
            <Button variant={btnClassName} onClick={() => props.onCellClick(props.cell)}
                    disabled={isDisabled || props.isReadOnly}/>
        </>
    );
}

export default Cell;