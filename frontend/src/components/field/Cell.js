import PropTypes from "prop-types";
import React from "react";
import Button from "react-bootstrap/Button";

const styleEmpty = "btn-outline-primary";
const styleNotAvailable = "bg-primary p-2 bg-opacity-50";
const styleHasShip = "btn-success";
const styleHasShot = "btn-danger";
const styleMiss = "btn-secondary";

function Cell(props) {
    const currentStyle = props.currentStyle;
    let style = null;
    switch (currentStyle) {
        case "ship":
            style = styleHasShip;
            break;
        case "hit":
            style = styleHasShot;
            break;
        case "not_available":
            style = styleNotAvailable;
            break;
        case "miss":
            style = styleMiss;
            break;
        case "empty":
            style = styleEmpty;
            break;
        default:
            style = styleEmpty;
            break;
    }
    style += " border-dark ";
    return (
        <Button bsPrefix="btn-square-sm" className={style} disabled={props.isDisabled} onClick={props.onButtonClick}/>
    );
}

Cell.propTypes = {
    isDisabled: PropTypes.bool,
    currentStyle: PropTypes.oneOf(["ship", "hit", "not_available", "miss", "empty"]),
    onButtonClick: PropTypes.func
};

export default Cell;