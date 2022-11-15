import React from "react";
import {Container, Nav, Navbar} from "react-bootstrap";
import LinkContainer from "react-router-bootstrap/LinkContainer";

function AppNavBar(props) {
    return (
        <Navbar bg="primary" variant="dark" expand="lg">
            <Container>
                <Navbar.Brand>React-Bootstrap</Navbar.Brand>
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
                        <LinkContainer to="/finish">
                            <Nav.Link>Results Game</Nav.Link>
                        </LinkContainer>
                    </Nav>
                </Navbar.Collapse>
            </Container>
        </Navbar>
    );
}

export default AppNavBar;