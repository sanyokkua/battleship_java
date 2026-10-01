package ua.kostenko.battleship.app.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a random 16-hex correlation id, in MDC for the logs and as a request attribute, and puts the
 * {@code gameId} in MDC when the path names a game (it grants nothing, R58), so every record of the request carries
 * both. Work with no request, such as a scheduler tick, gets an id from {@link #scoped}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "correlationId";
    public static final String GAME_ID_KEY = "gameId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".ID";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern GAME_PATH = Pattern.compile("^/api/v1/games/([A-Za-z0-9_-]{22})(?:/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = newId();
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        MDC.put(MDC_KEY, id);
        Matcher game = GAME_PATH.matcher(request.getRequestURI());
        if (game.matches()) {
            MDC.put(GAME_ID_KEY, game.group(1));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(GAME_ID_KEY);
        }
    }

    /** Runs work with a correlation id in MDC: the caller's when it has one, otherwise a fresh one for this run. */
    public static void scoped(Runnable work) {
        if (MDC.get(MDC_KEY) != null) {
            work.run();
            return;
        }
        MDC.put(MDC_KEY, newId());
        try {
            work.run();
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String newId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
