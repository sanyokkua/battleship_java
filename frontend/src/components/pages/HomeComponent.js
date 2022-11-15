import React from 'react';
import Button from "react-bootstrap/Button";
import Row from "react-bootstrap/Row";
import Container from "react-bootstrap/Container";
import "../../app.css";

class HomeComponent extends React.Component {
    constructor(props) {
        super(props);
        this.state = {};
    }

    handleNewGameClick() {
        this.setState({});
    }

    handleJoinGameClick() {
        this.setState({});
    }

    render() {
        return (
            <Container className="d-grid gap-2">
                <Row>
                    <Button variant="success" onClick={() => this.handleNewGameClick()}>
                        New Game
                    </Button>
                </Row>
                <Row>
                    <Button variant="primary" onClick={() => this.handleJoinGameClick()}>
                        Join Game
                    </Button>
                </Row>
            </Container>
        );
    }
}

export default HomeComponent;