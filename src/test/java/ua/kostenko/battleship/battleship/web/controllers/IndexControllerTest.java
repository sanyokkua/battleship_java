package ua.kostenko.battleship.battleship.web.controllers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class IndexControllerTest {
    final IndexController indexController = new IndexController();

    @Test
    void testIndex() {
        String result = indexController.index();
        Assertions.assertEquals("index", result);
    }
}