import React from "react";
import Button from "react-bootstrap/Button";
import Row from "react-bootstrap/Row";
import ShipsList from "../common/ShipsList";
import Status from "../common/Status";
import Field from "../field/Field";

class PreparationPage extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            isReady: false,
            shipList: [
                {
                    shipId: "ship-id-1",
                    shipType: "some type",
                    shipDirection: "HORIZONTAL",
                    shipSize: 4
                },
                {
                    shipId: "ship-id-2",
                    shipType: "some type",
                    shipDirection: "VERTICAL",
                    shipSize: 3
                },
                {
                    shipId: "ship-id-3",
                    shipType: "some type",
                    shipDirection: "HORIZONTAL",
                    shipSize: 2
                },
                {
                    shipId: "ship-id-4",
                    shipType: "some type",
                    shipDirection: "VERTICAL",
                    shipSize: 1
                }
            ]
        };
    }

    handleReady() {

    }

    render() {
        return (
            <Row>
                <Status badgeColor="warning"
                        badgeText="In progress"
                        textInTheMiddle="status:"
                        highlightedTextColor="primary"
                        highlightedText="PLAYER"/>
                <ShipsList shipsList={this.state.shipList}/>
                <Field/>
                <Button variant="success" disabled={!this.state.isReady}
                        onClick={() => this.handleReady()}>Ready</Button>
            </Row>
        );
    }
}

export default PreparationPage;