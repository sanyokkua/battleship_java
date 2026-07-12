package ua.kostenko.battleship.battleship.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for handling the index page in the Battleship game application.
 * <p>
 * The IndexController class manages requests to the root URL and serves the index page. It also
 * forwards client-side (React Router) routes to the same index page so that a direct browser
 * load or refresh on a route such as {@code /new} or {@code /game/preparation} serves the SPA
 * shell instead of a 404, letting React Router take over rendering on the client.
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

    /**
     * Forwards single-segment, extensionless paths (e.g. {@code /new}, {@code /join}) to the
     * index view so client-side routes resolve on a direct load/refresh. Paths whose last
     * segment contains a dot (e.g. {@code /favicon.ico}, {@code /static/main.js}) are excluded
     * so real static resources keep resolving through the normal static-resource handling.
     *
     * @return the name of the index view
     */
    @RequestMapping(value = "/{path:[^\\.]*}")
    public String forward() {
        return "index";
    }

    /**
     * Forwards multi-segment, extensionless paths (e.g. {@code /game/preparation}) to the index
     * view so client-side routes resolve on a direct load/refresh. Paths whose last segment
     * contains a dot are excluded so real static resources keep resolving through the normal
     * static-resource handling.
     *
     * @return the name of the index view
     */
    @RequestMapping(value = "/**/{path:[^\\.]*}")
    public String forwardNested() {
        return "index";
    }
}
