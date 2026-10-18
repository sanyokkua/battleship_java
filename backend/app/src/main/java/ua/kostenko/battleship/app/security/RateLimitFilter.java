package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.ToIntFunction;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.config.BattleshipProperties.RateLimits;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.dto.ProblemCode;

/**
 * Throttles the eight limited operations, each with its own configured per-minute limit (R38, R52). Create and join
 * are keyed by the caller address, which is used only here and is never logged (research.md D17); the other six are
 * keyed by the session cookie, which the session filter has already validated. Metadata, rulesets and health are not
 * limited. A refusal is answered here, before any controller runs, so it changes no game state.
 */
final class RateLimitFilter extends OncePerRequestFilter {
    private enum Key {
        ADDRESS,
        SESSION
    }

    private record Limited(String name, RequestMatcher matcher, Key key, ToIntFunction<RateLimits> limit) {
        static Limited of(String name, HttpMethod method, String pattern, Key key, ToIntFunction<RateLimits> limit) {
            var paths = PathPatternRequestMatcher.withDefaults();
            // MVC answers HEAD for every GET mapping, so a HEAD must be counted like the GET it mirrors.
            RequestMatcher matcher = method == HttpMethod.GET
                    ? new OrRequestMatcher(
                            paths.matcher(HttpMethod.GET, pattern), paths.matcher(HttpMethod.HEAD, pattern))
                    : paths.matcher(method, pattern);
            return new Limited(name, matcher, key, limit);
        }
    }

    /** Matched as MVC matches, on the decoded path segments, so a percent-encoded spelling cannot skip the filter. */
    private static final List<Limited> CLASSES = List.of(
            Limited.of("createGame", HttpMethod.POST, "/api/v1/games", Key.ADDRESS, RateLimits::createGamePerMinute),
            Limited.of(
                    "joinGame", HttpMethod.POST, "/api/v1/games/{gameId}/join", Key.ADDRESS, RateLimits::joinPerMinute),
            Limited.of(
                    "sendCommand",
                    HttpMethod.POST,
                    "/api/v1/games/{gameId}/commands",
                    Key.SESSION,
                    RateLimits::commandsPerMinute),
            Limited.of("getGame", HttpMethod.GET, "/api/v1/games/{gameId}", Key.SESSION, RateLimits::readGamePerMinute),
            Limited.of(
                    "sendPresence",
                    HttpMethod.POST,
                    "/api/v1/games/{gameId}/presence",
                    Key.SESSION,
                    RateLimits::presencePerMinute),
            Limited.of(
                    "streamGameEvents",
                    HttpMethod.GET,
                    "/api/v1/games/{gameId}/events",
                    Key.SESSION,
                    RateLimits::streamOpenPerMinute),
            Limited.of(
                    "replaceInvitation",
                    HttpMethod.POST,
                    "/api/v1/games/{gameId}/invitation",
                    Key.SESSION,
                    RateLimits::replaceInvitationPerMinute),
            Limited.of(
                    "leaveGame",
                    HttpMethod.POST,
                    "/api/v1/games/{gameId}/leave",
                    Key.SESSION,
                    RateLimits::leavePerMinute));

    private final FixedWindowBuckets buckets;
    private final RateLimits limits;
    private final ObjectMapper wireMapper;

    RateLimitFilter(FixedWindowBuckets buckets, RateLimits limits, ObjectMapper wireMapper) {
        this.buckets = buckets;
        this.limits = limits;
        this.wireMapper = wireMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Limited operation : CLASSES) {
            if (!operation.matcher().matches(request)) {
                continue;
            }
            String key = operation.key() == Key.ADDRESS
                    ? request.getRemoteAddr()
                    : SessionCookieFilter.sessionValue(request);
            long retryAfter =
                    buckets.acquire(operation.name(), key, operation.limit().applyAsInt(limits));
            if (retryAfter > 0) {
                ProblemAdvice.writeProblem(
                        request, response, ProblemCode.RATE_LIMIT_EXCEEDED, wireMapper, (int) retryAfter);
                return;
            }
            break;
        }
        chain.doFilter(request, response);
    }
}
