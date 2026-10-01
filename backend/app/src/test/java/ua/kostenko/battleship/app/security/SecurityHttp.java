package ua.kostenko.battleship.app.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/** Real-server HTTP helper for the security ITs: manual cookies, no cookie jar, every response recorded. */
final class SecurityHttp {
    static final String SESSION_COOKIE = "__Host-battleship_session";
    static final String XSRF_COOKIE = "XSRF-TOKEN";

    record Reply(int status, HttpResponse<String> response, JsonNode json) {
        String header(String name) {
            return response.headers().firstValue(name).orElse("");
        }

        List<String> setCookies(String name) {
            return response.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith(name + "="))
                    .toList();
        }
    }

    private final int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Reply> seen = new ArrayList<>();

    SecurityHttp(int port) {
        this.port = port;
    }

    List<Reply> seen() {
        return seen;
    }

    /** Sends a request; {@code headers} are name/value pairs, {@code cookies} the raw Cookie header or null. */
    Reply call(String method, String path, String cookies, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        String raw = response.body();
        Reply reply = new Reply(response.statusCode(), response, raw.isBlank() ? null : mapper.readTree(raw));
        seen.add(reply);
        return reply;
    }

    /** Value of the readable anti-forgery cookie as {@code GET /meta} issues it. */
    String freshToken() throws Exception {
        Reply meta = call("GET", "/api/v1/meta", null);
        String cookie = meta.setCookies(XSRF_COOKIE).getFirst();
        return cookie.substring(XSRF_COOKIE.length() + 1, cookie.indexOf(';'));
    }

    /** A POST carrying the cookie and the matching header, plus any extra cookie text. */
    Reply postWithToken(String path, String token, String extraCookies) throws Exception {
        String cookies = XSRF_COOKIE + "=" + token + (extraCookies == null ? "" : "; " + extraCookies);
        return call("POST", path, cookies, "X-XSRF-TOKEN", token);
    }
}
