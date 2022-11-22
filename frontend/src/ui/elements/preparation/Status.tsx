import React from "react";
import Badge from "react-bootstrap/Badge";
import {Colors} from "./common/PreparationTypes";

type StatusProps = {
    badgeColor: Colors,
    badgeText: string,

    textInTheMiddle: string,

    highlightedTextColor?: Colors,
    highlightedText?: string
};

function Status(props: StatusProps) {
    const highlightedTextColor = props.highlightedTextColor ? `text-${props.highlightedTextColor}` : "text-primary";
    const highlightedText = props.highlightedText &&
        <span className={highlightedTextColor}>{props.highlightedText}</span>;

    return (
        <p>
            {highlightedText} {props.textInTheMiddle} <Badge bg={props.badgeColor}>{props.badgeText}</Badge>
        </p>
    );
}

export default Status;