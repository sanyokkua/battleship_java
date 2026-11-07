## ADDED Requirements

### Requirement: Gameplay coverage reaches Sonar
The frontend coverage gate SHALL include code executed by the real-backend Playwright journeys, merged with unit coverage, so gameplay screens are measured (R53, R63, R64; S9).

#### Scenario: Named gameplay line is covered
- **WHEN** the instrumented journeys and unit tests have run and coverage is imported into Sonar
- **THEN** a named gameplay source line appears covered in the LCOV and in the refreshed Sonar report
