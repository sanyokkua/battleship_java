import PropTypes from "prop-types";
import React from "react";
import Badge from "react-bootstrap/Badge";

function Status(props) {
    const badgeColor = props.badgeColor;
    const badgeText = props.badgeText;
    const textInTheMiddle = props.textInTheMiddle;
    const highlightedTextColor = props.highlightedTextColor ? `text-${props.highlightedTextColor}` : "text-primary";

    let highlightedText = null;
    if (props.highlightedText) {
        highlightedText = <span className={highlightedTextColor}>{props.highlightedText}</span>;
    }

    return (
        <p>
            {highlightedText} {textInTheMiddle} <Badge bg={badgeColor}>{badgeText}</Badge>
        </p>
    );
}

Status.propTypes = {
    badgeColor: PropTypes.oneOf(["primary", "secondary", "success", "warning", "info", "light", "dark", "danger"]),
    badgeText: PropTypes.string.isRequired,
    textInTheMiddle: PropTypes.string.isRequired,
    highlightedTextColor: PropTypes.oneOf(["primary", "secondary", "success", "warning", "info", "light", "dark"]),
    highlightedText: PropTypes.string
};

export default Status;