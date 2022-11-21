import React, {useState} from "react";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Modal from "react-bootstrap/Modal";
import {CellDto, ShipDirection} from "../../../logic/GameTypes";
import {CellClickEventData} from "./PreparationTypes";

type PreparationCellProps = {
    cell: CellDto,
    onButtonClick: (cellClickEventData: CellClickEventData) => void
};

function PrepCell(props: PreparationCellProps) {
    const [showDirectionDialog, setShowDirectionDialog] = useState(false);

    const handleOnDialogClose = () => setShowDirectionDialog(false);

    const handleOnCellButtonClick = () => {
        if (!props.cell.ship) {
            setShowDirectionDialog(true);
        } else {
            props.onButtonClick({
                cell: props.cell,
                isDelete: true,
                direction: null
            });
        }
    };
    const handleOnDirectionChose = (direction: ShipDirection) => {
        setShowDirectionDialog(false);
        props.onButtonClick({
            cell: props.cell,
            isDelete: false,
            direction: direction
        });
    };

    let btnClassName: string | null;
    let isDisabled: boolean;

    if (props.cell.isAvailable) {
        btnClassName = "outline-primary";
        isDisabled = false;
    } else if (props.cell.ship) {
        btnClassName = "success";
        isDisabled = false;
    } else {
        isDisabled = true;
        btnClassName = "bg-primary p-2 bg-opacity-25";
    }

    return (
        <>
            <Button variant={btnClassName} onClick={handleOnCellButtonClick} disabled={isDisabled}/>
            <Modal show={showDirectionDialog} onHide={handleOnDialogClose} backdrop="static" centered size="sm">
                <Modal.Header closeButton>Chose direction</Modal.Header>
                <Modal.Body>
                    <Container className="text-center">
                        <Button variant="primary" onClick={() => handleOnDirectionChose("VERTICAL")}>
                            Vertical
                        </Button>{" "}
                        <Button variant="success" onClick={() => handleOnDirectionChose("HORIZONTAL")}>
                            Horizontal
                        </Button>
                    </Container>
                </Modal.Body>
            </Modal>
        </>
    );
}

export default PrepCell;