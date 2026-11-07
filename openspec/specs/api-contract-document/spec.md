# api-contract-document Specification

## Purpose
Defines the shape of the Battleship API contract: one OpenAPI document that frontend and backend share, with no duplication.

## Requirements

### Requirement: Single OpenAPI deliverable (R01) [user]
The deliverable SHALL be one OpenAPI document, `contracts/openapi.yaml`, plus a short guide, with no application code, no second source of truth and no custom validation framework.

#### Scenario: Contract directory contents
- **WHEN** a developer inspects `contracts/`
- **THEN** they find the OpenAPI file, the guide and tooling config only, and no application code or generated client

### Requirement: Client is a view (R02) [user]
Every action SHALL go to the server, and every successful game operation SHALL return the same `GameSnapshot`, which SHALL also be pushed over SSE.

#### Scenario: Action returns snapshot
- **WHEN** a client sends a game command successfully
- **THEN** the response body is the current `GameSnapshot`, identical in shape to what the event stream pushes

### Requirement: Caller-relative snapshot (R03) [user]
The snapshot SHALL be caller-relative (`you` / `opponent`) and SHALL list `allowedActions`; a client SHALL NOT derive permissions, turn, legality, winner, expiry or statistics.

#### Scenario: UI enables actions
- **WHEN** a client renders a snapshot
- **THEN** it enables exactly the actions in `allowedActions`

### Requirement: Eleven operations (R04) [agent]
The contract SHALL define eleven operations under `/api/v1`: meta, rulesets, health, create, read, join, commands, events, invitation, presence, leave.

#### Scenario: Operation inventory
- **WHEN** the document is inspected
- **THEN** it contains exactly those eleven operations under `/api/v1`

### Requirement: No duplication (R05) [user]
The contract SHALL contain one `version`, one board schema, one cell-state enum, one problem document and one response definition per HTTP status.

#### Scenario: Shared definitions
- **WHEN** two operations return the same HTTP status or a board
- **THEN** both reference the single shared response or schema definition

### Requirement: Evolution rules (R24) [agent]
Within `/api/v1` only operations, optional fields and headers, command types and values of the enums the contract marks open MAY be added; anything else SHALL require `/api/v2`.

#### Scenario: Breaking change
- **WHEN** a change removes a field or tightens a closed enum
- **THEN** it is not allowed within `/api/v1` and requires `/api/v2`

### Requirement: Developer can build against the contract alone (User Story 5, Build against one contract, P3)
A backend or frontend developer in any stack SHALL be able to implement against `openapi.yaml` alone.

#### Scenario: Stack-neutral implementation
- **WHEN** a developer in any stack reads only `openapi.yaml`
- **THEN** they have the operations, schemas, examples and rules needed to implement a client or server
