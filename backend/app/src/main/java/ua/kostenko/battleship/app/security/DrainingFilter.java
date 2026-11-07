package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.config.GracefulShutdownConfig;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.dto.ProblemCode;

/**
 * While the service is draining, stops admitting work (R59): opening an event stream (which is what ends the browser's
 * automatic reconnection) and every request that changes anything under {@code /api/v1/games} (create, join, command,
 * invitation, presence, leave) answer {@code 503 service-unavailable} with {@code Retry-After}. Reads, health,
 * metadata and rulesets are answered as usual, and a request admitted before the drain began runs to its end. Answered
 * before any controller runs, so no game state changes.
 */
final class DrainingFilter extends OncePerRequestFilter {
    private static final RequestMatcher GAMES =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/games/**");
    private static final RequestMatcher EVENTS =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/games/{gameId}/events");

    private final GracefulShutdownConfig shutdown;
    private final ObjectMapper wireMapper;
    private final int retryAfterSeconds;

    DrainingFilter(GracefulShutdownConfig shutdown, ObjectMapper wireMapper, int retryAfterSeconds) {
        this.shutdown = shutdown;
        this.wireMapper = wireMapper;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static boolean admitsWork(HttpServletRequest request) {
        String method = request.getMethod();
        if (HttpMethod.OPTIONS.matches(method)) return false;
        if (EVENTS.matches(request)) return true;
        return GAMES.matches(request) && !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (shutdown.draining() && admitsWork(request)) {
            ProblemAdvice.writeProblem(
                    request, response, ProblemCode.SERVICE_UNAVAILABLE, wireMapper, retryAfterSeconds);
            return;
        }
        chain.doFilter(request, response);
    }
}
