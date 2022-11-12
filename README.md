# battleship_java
Battleship game implementation on Java and React (for education purposes).

This project will be developed on Java+Spring Boot (Rest/Mvc/etc) to provide API and in addition to REST API will 
be developed UI using ReactJS.


API:

GET         /api/game/editions
POST        /api/game/sessions
POST        /api/game/sessions/{sessionId}/players
PUT         /api/game/sessions/{sessionId}/players/{playerId}/ships/{shipId}
DELETE      /api/game/sessions/{sessionId}/players/{playerId}/ships?delete
GET         /api/game/sessions/{sessionId}/players/{playerId}/ships?available
POST        /api/game/sessions/{sessionId}/players/{playerId}?start
GET         /api/game/sessions/{sessionId}/players/{playerId}
GET         /api/game/sessions/{sessionId}/players/{playerId}?opponent
GET         /api/game/sessions/{sessionId}/players/{playerId}/field
GET         /api/game/sessions/{sessionId}/players/{playerId}/field?opponent
GET         /api/game/sessions/{sessionId}/players?active
POST        /api/game/sessions/{sessionId}/players/{playerId}/field?shot
GET         /api/game/sessions/{sessionId}/players/{playerId}/cells
GET         /api/game/sessions/{sessionId}/players/{playerId}/ships?NotDestroyed
GET         /api/game/sessions/{sessionId}/winner