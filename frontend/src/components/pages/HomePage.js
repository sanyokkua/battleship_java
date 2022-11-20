import React from "react";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import {useNavigate} from "react-router-dom";

function HomePage(props) {
    let navigate = useNavigate();
    return (
        <Container className="d-grid gap-4 w-75-ns p-3">
            <Row>
                <Button variant="success" onClick={() => navigate("/new")}>New Game</Button>
            </Row>
            <Row>
                <Button variant="primary" onClick={() => navigate("/join")}>Join Game</Button>
            </Row>
        </Container>
    );
}

export default HomePage;