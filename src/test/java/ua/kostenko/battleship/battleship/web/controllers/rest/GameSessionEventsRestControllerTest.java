package ua.kostenko.battleship.battleship.web.controllers.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.kostenko.battleship.battleship.web.sse.SessionEventBroadcaster;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} coverage for
 * {@link GameSessionEventsRestController}. {@link SessionEventBroadcaster} is mocked so this only
 * exercises controller wiring: the async SSE dispatch and the response's content type.
 */
@WebMvcTest(GameSessionEventsRestController.class)
class GameSessionEventsRestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SessionEventBroadcaster broadcaster;

    @Test
    void subscribeToSessionEvents_startsAnAsyncSseStreamFromTheBroadcasterEmitter() throws Exception {
        var emitter = new SseEmitter(0L);
        when(broadcaster.subscribe("sessionId", "playerId")).thenReturn(emitter);

        var mvcResult = mockMvc.perform(get("/api/v2/game/sessions/sessionId/players/playerId/events"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.complete();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream"));
    }
}
