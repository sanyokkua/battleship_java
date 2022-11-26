import React from "react";
import Button from "react-bootstrap/Button";
import Container from "react-bootstrap/Container";
import Row from "react-bootstrap/Row";
import {LinkContainer} from "react-router-bootstrap";

function HomePage(props: any) {
    return (
        <div>
            <Container className="d-grid gap-4 w-75-ns p-3">
                <Row>
                    <LinkContainer to="/new">
                        <Button variant="success">New Game</Button>
                    </LinkContainer>
                </Row>
                <Row>
                    <LinkContainer to="/join">
                        <Button variant="primary">Join Game</Button>
                    </LinkContainer>
                </Row>
            </Container>
        </div>
    );
}

export default HomePage;