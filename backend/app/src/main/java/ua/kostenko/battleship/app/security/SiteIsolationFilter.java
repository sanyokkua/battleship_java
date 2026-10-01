package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;
import ua.kostenko.battleship.app.web.ProblemAdvice;
import ua.kostenko.battleship.app.web.dto.ProblemCode;

/**
 * Refuses an unsafe request that a browser marks as coming from another site (R36): {@code Sec-Fetch-Site} says
 * cross-site or same-site, or an {@code Origin} is present and is not the origin of {@code battleship.public-base-url}. Absent headers are
 * admitted (a script or command-line tool is not a browser) and such a caller still has to present the anti-forgery
 * token. CORS stays unconfigured: the interface and the API share one origin.
 */
final class SiteIsolationFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final String ownOrigin;
    private final ObjectMapper wireMapper;

    /** {@code publicBaseUrl} is the validated {@code battleship.public-base-url}: the one origin this service answers for. */
    SiteIsolationFilter(String publicBaseUrl, ObjectMapper wireMapper) {
        this.ownOrigin = originOf(URI.create(publicBaseUrl));
        this.wireMapper = wireMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (crossSite(request)) {
            ProblemAdvice.writeProblem(request, response, ProblemCode.REQUEST_SECURITY_REJECTED, wireMapper);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean crossSite(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equals(site) || "same-site".equals(site)) {
            return true;
        }
        String origin = request.getHeader("Origin");
        return origin != null && !origin.equals(ownOrigin);
    }

    /** Lower-case scheme and host, with the port only when it is not the scheme's default, as a browser writes it. */
    private static String originOf(URI uri) {
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        int port = uri.getPort();
        boolean standard = port == -1 || port == ("https".equals(scheme) ? 443 : 80);
        return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + (standard ? "" : ":" + port);
    }
}
