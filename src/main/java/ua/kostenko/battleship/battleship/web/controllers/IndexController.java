package ua.kostenko.battleship.battleship.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for handling the index page in the Battleship game application.
 * <p>
 * The IndexController class manages requests to the root URL and serves the index page.
 * </p>
 */
@Controller
public class IndexController {

    /**
     * Handles requests to the root URL ("/") and returns the name of the index view.
     *
     * @return the name of the index view
     */
    @RequestMapping(value = "/")
    public String index() {
        return "index";
    }
}
