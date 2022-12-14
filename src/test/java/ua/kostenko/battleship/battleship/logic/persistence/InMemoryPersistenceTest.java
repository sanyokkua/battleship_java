package ua.kostenko.battleship.battleship.logic.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.kostenko.battleship.battleship.logic.engine.config.GameEdition;
import ua.kostenko.battleship.battleship.logic.engine.models.enums.GameStage;
import ua.kostenko.battleship.battleship.logic.engine.models.records.GameState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPersistenceTest {
    private Persistence persistence;

    @BeforeEach
    void before() {
        persistence = new InMemoryPersistence();
        persistence.save(GameState.create(GameEdition.UKRAINIAN, "test_session_1", GameStage.IN_GAME

        ));
        persistence.save(GameState.create(GameEdition.UKRAINIAN, "test_session_2", GameStage.IN_GAME

        ));
        persistence.save(GameState.create(GameEdition.UKRAINIAN, "test_session_3", GameStage.IN_GAME

        ));
    }

    @Test
    void testLoad() {
        var value1 = persistence.load("nonExisting");
        var value2 = persistence.load("    ");
        assertTrue(value1.isEmpty());
        assertTrue(value2.isEmpty());

        var value3 = persistence.load("test_session_1");
        assertTrue(value3.isPresent());
        assertEquals("test_session_1",
                     value3.get()
                           .getGameState()
                           .sessionId());
    }

    @Test
    void testSave() {
        var value1 = persistence.save(null);

        assertTrue(value1.isEmpty());

        var saved_1 = persistence.save(GameState.create(GameEdition.UKRAINIAN,
                                                        "saved_new_test_session_1",
                                                        GameStage.IN_GAME));
        var saved_2 = persistence.save(GameState.create(GameEdition.UKRAINIAN,
                                                        "saved_new_test_session_2",
                                                        GameStage.IN_GAME));
        var saved_3 = persistence.save(GameState.create(GameEdition.UKRAINIAN,
                                                        "saved_new_test_session_3",
                                                        GameStage.IN_GAME));

        assertTrue(saved_1.isPresent());
        assertTrue(saved_2.isPresent());
        assertTrue(saved_3.isPresent());

        var loaded_1 = persistence.load("saved_new_test_session_1");
        var loaded_2 = persistence.load("saved_new_test_session_2");
        var loaded_3 = persistence.load("saved_new_test_session_3");

        assertTrue(loaded_1.isPresent());
        assertTrue(loaded_2.isPresent());
        assertTrue(loaded_3.isPresent());
        assertEquals("saved_new_test_session_1",
                     loaded_1.get()
                             .getGameState()
                             .sessionId());
        assertEquals("saved_new_test_session_2",
                     loaded_2.get()
                             .getGameState()
                             .sessionId());
        assertEquals("saved_new_test_session_3",
                     loaded_3.get()
                             .getGameState()
                             .sessionId());
    }

    @Test
    void testRemove() {
        var loaded_before_1 = persistence.load("test_session_1");
        var loaded_before_2 = persistence.load("test_session_2");
        var loaded_before_3 = persistence.load("test_session_3");

        assertTrue(loaded_before_1.isPresent());
        assertTrue(loaded_before_2.isPresent());
        assertTrue(loaded_before_3.isPresent());

        persistence.remove("test_session_1");
        persistence.remove("test_session_2");
        persistence.remove("test_session_3");

        var loaded_after_1 = persistence.load("test_session_1");
        var loaded_after_2 = persistence.load("test_session_2");
        var loaded_after_3 = persistence.load("test_session_3");

        assertTrue(loaded_after_1.isEmpty());
        assertTrue(loaded_after_2.isEmpty());
        assertTrue(loaded_after_3.isEmpty());
    }

}
