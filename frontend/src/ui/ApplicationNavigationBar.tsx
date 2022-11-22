import React from "react";
import Container from "react-bootstrap/Container";
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import {LinkContainer} from "react-router-bootstrap";
import {Outlet} from "react-router-dom";

export type AppNavBarProps = {
    hasPreparation: boolean,
    hasGameplay: boolean,
    hasHasResults: boolean
}

function ApplicationNavigationBar(props: AppNavBarProps) {
    const preparationPage = <LinkContainer to="/game/preparation"><Nav.Link>Preparation</Nav.Link></LinkContainer>;
    const gameplayPage = <LinkContainer to="/game/gameplay"><Nav.Link>Gameplay</Nav.Link></LinkContainer>;
    const resultsPage = <LinkContainer to="/game/results"><Nav.Link>Results</Nav.Link></LinkContainer>;

    return (
        <>
            <Navbar collapseOnSelect bg="primary" variant="dark" expand="lg">
                <Container>
                    <LinkContainer to="/">
                        <Navbar.Brand>Battleship</Navbar.Brand>
                    </LinkContainer>
                    <Navbar.Toggle aria-controls="basic-navbar-nav"/>
                    <Navbar.Collapse id="basic-navbar-nav">
                        <Nav className="me-auto">
                            <LinkContainer to="/">
                                <Nav.Link>Home</Nav.Link>
                            </LinkContainer>
                            <LinkContainer to="/new">
                                <Nav.Link>New Game</Nav.Link>
                            </LinkContainer>
                            <LinkContainer to="/join">
                                <Nav.Link>Join Game</Nav.Link>
                            </LinkContainer>
                            {props.hasPreparation && preparationPage}
                            {props.hasGameplay && gameplayPage}
                            {props.hasHasResults && resultsPage}
                        </Nav>
                    </Navbar.Collapse>
                </Container>
            </Navbar>
            <br/>
            <Container fluid>
                <Outlet/>
            </Container>
        </>
    );
}

export default ApplicationNavigationBar;