import PropTypes from "prop-types";
import React, {useState} from "react";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Modal from "react-bootstrap/Modal";

function PreparationCell(props) {
    const [show, setShow] = useState(false);
    const handleClose = () => setShow(false);
    const handleClick = () => {
        if (!props.cell.ship) {
            setShow(true);
        } else {
            props.onButtonClick(props.cell);
        }
    };
    const handleChose = (direction) => {
        setShow(false);
        const cell = props.cell;
        cell.direction = direction;
        props.onButtonClick(cell);
    };

    let btnClassName = null;
    let isDisabled = false;
    if (props.cell.available) {
        btnClassName = "outline-primary";
        isDisabled = false;
    } else if (props.cell.ship) {
        btnClassName = "success";
        isDisabled = false;
    } else {
        isDisabled = true;
        btnClassName = "bg-primary p-2 bg-opacity-50";
    }

    return (
        <>
            <Button variant={btnClassName} onClick={handleClick} disabled={isDisabled}/>
            <Modal show={show} onHide={handleClose} backdrop="static" centered size="sm">
                <Modal.Header closeButton>Chose direction</Modal.Header>
                <Modal.Body>
                    <Container className="text-center">
                        <Button variant="primary" onClick={() => handleChose("VERTICAL")}>
                            Vertical
                        </Button>{" "}
                        <Button variant="success" onClick={() => handleChose("HORIZONTAL")}>
                            Horizontal
                        </Button>
                    </Container>
                </Modal.Body>
            </Modal>
        </>
    );
}

PreparationCell.propTypes = {
    cell: PropTypes.shape({
        row: PropTypes.number.isRequired,
        col: PropTypes.number.isRequired,
        ship: PropTypes.object.isRequired,
        hasShot: PropTypes.bool.isRequired,
        available: PropTypes.bool.isRequired
    }),
    onButtonClick: PropTypes.func.isRequired
};

export default PreparationCell;