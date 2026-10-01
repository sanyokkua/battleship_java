package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.SessionCookie;
import ua.kostenko.battleship.app.web.dto.ProblemCode;
import ua.kostenko.battleship.application.registry.SessionRegistry;

/**
 * Authenticates the protected operations before any controller runs: the session cookie must name a session the
 * registry knows. Whether that session may touch a particular game is the use cases' decision (R33). Create and join
 * are not matched here because they issue a session instead of requiring one (R32).
 */
final class SessionCookieFilter extends OncePerRequestFilter {
    /** Matched as MVC matches, on the decoded path segments, so a percent-encoded spelling cannot skip the filter. */
    private static final RequestMatcher PROTECTED = new OrRequestMatcher(Stream.of(
                    "/api/v1/games/{gameId}",
                    "/api/v1/games/{gameId}/commands",
                    "/api/v1/games/{gameId}/events",
                    "/api/v1/games/{gameId}/invitation",
                    "/api/v1/games/{gameId}/presence",
                    "/api/v1/games/{gameId}/leave")
            .map(pattern ->
                    (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
            .toList());

    private final SessionRegistry sessions;
    private final ObjectMapper wireMapper;

    SessionCookieFilter(SessionRegistry sessions, ObjectMapper wireMapper) {
        this.sessions = sessions;
        this.wireMapper = wireMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String value = sessionValue(request);
        if (value == null || sessions.find(value).isEmpty()) {
            response.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.cleared());
            ProblemAdvice.writeProblem(request, response, ProblemCode.SESSION_REQUIRED, wireMapper);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String sessionValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SessionCookie.NAME.equals(cookie.getName())
                    && !cookie.getValue().isEmpty()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
