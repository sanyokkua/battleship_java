import React from "react";
import Container from "react-bootstrap/Container";
import Nav from "react-bootstrap/Nav";
import Navbar from "react-bootstrap/Navbar";
import {Link} from "wouter";

function ApplicationNavigationBar(props) {
    return (
        <>
            <Navbar bg="primary" variant="dark" expand="lg">
                <Container>
                    <Navbar.Brand>React-Bootstrap</Navbar.Brand>
                    <Navbar.Toggle aria-controls="basic-navbar-nav"/>
                    <Navbar.Collapse id="basic-navbar-nav">
                        <Nav className="me-auto">
                            <Link to="/">
                                <Nav.Link>Home</Nav.Link>
                            </Link>
                            <Link to="/new">
                                <Nav.Link>New Game</Nav.Link>
                            </Link>
                            <Link to="/join">
                                <Nav.Link>Join Game</Nav.Link>
                            </Link>
                        </Nav>
                    </Navbar.Collapse>
                </Container>
            </Navbar>
        </>
    );
}

export default ApplicationNavigationBar;