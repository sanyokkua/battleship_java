import PropTypes from "prop-types";
import React from "react";
import Button from "react-bootstrap/Button";
import ButtonGroup from "react-bootstrap/ButtonGroup";
import Dropdown from "react-bootstrap/Dropdown";
import DropdownButton from "react-bootstrap/DropdownButton";
import cell from "./Cell";

const styleEmpty = "btn-outline-primary";
const styleNotAvailable = "bg-primary p-2 bg-opacity-50";
const styleHasShip = "btn-success";

const styleHasShot = "btn-danger";
const styleMiss = "btn-secondary";

function getStyleForPrepare(cell: any) {
    let style = null;
    let isDisabled = false;

    if (cell.isAvailable) {
        style = styleEmpty;
        isDisabled = false;
    } else if (cell.ship && cell.ship.shipId) {
        style = styleHasShip;
        isDisabled = false;
    } else {
        style = styleNotAvailable;
        isDisabled = true;
    }
    return {style: style, isDisabled: isDisabled};
}

function getStyleForGameplay(cell: any) {
    let style = null;
    let isDisabled = false;

    if (cell.ship && cell.ship.shipId && !cell.hasShot) {
        style = styleHasShip;
        isDisabled = true;
    } else if (cell.ship && cell.ship.shipId && cell.hasShot) {
        style = styleHasShot;
        isDisabled = true;
    } else if (cell.hasShot) {
        style = styleMiss;
        isDisabled = true;
    } else {
        style = styleEmpty;
        isDisabled = false;
    }
    return {style: style, isDisabled: isDisabled};
}

function FieldCell(props: any) {
    let style = null;
    if (props.stage === "prepare") {
        style = getStyleForPrepare(props.cell);
    } else {
        style = getStyleForGameplay(props.cell);
    }
    const componentStyle = style.style;
    const disabled = props.isAlwaysDisabled && style.isDisabled;
    const onClick = (event: any) => {
        console.log(event);
        props.onButtonClick({cell: cell, direction: event});
    };
    return (
        <>
            {props.stage === "prepare" && props.cell.ship === null ?
             <DropdownButton as={ButtonGroup} className={componentStyle} id="bg-nested-dropdown"
                             onSelect={(e) => onClick(e)} title={""}>
                 <Dropdown.Item eventKey="HORIZONTAL">⇨ Horizontal</Dropdown.Item>
                 <Dropdown.Item eventKey="VERTICAL">⇩ Vertical</Dropdown.Item>
             </DropdownButton>
                                                                   :
             <Button className={componentStyle} disabled={disabled}
                     onClick={(e) => onClick(e)}/>}
        </>
    );
}

FieldCell.propTypes = {
    stage: PropTypes.oneOf(["prepare", "gameplay"]).isRequired,
    cell: PropTypes.shape({
        row: PropTypes.number.isRequired,
        col: PropTypes.number.isRequired,
        ship: PropTypes.object,
        hasShot: PropTypes.bool.isRequired,
        isAvailable: PropTypes.bool.isRequired
    }),
    onButtonClick: PropTypes.func.isRequired,
    isAlwaysDisabled: PropTypes.bool
};

export default FieldCell;