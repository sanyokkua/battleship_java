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
public final class SecurityHttp {
    public static final String SESSION_COOKIE = "__Host-battleship_session";
    public static final String XSRF_COOKIE = "XSRF-TOKEN";

    public record Reply(int status, HttpResponse<String> response, JsonNode json) {
        public String header(String name) {
            return response.headers().firstValue(name).orElse("");
        }

        public List<String> setCookies(String name) {
            return response.headers().allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith(name + "="))
                    .toList();
        }
    }

    private final int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Reply> seen = new ArrayList<>();

    public SecurityHttp(int port) {
        this.port = port;
    }

    public List<Reply> seen() {
        return seen;
    }

    /** Sends a request; {@code headers} are name/value pairs, {@code cookies} the raw Cookie header or null. */
    public Reply call(String method, String path, String cookies, String... headers) throws Exception {
        return send(method, path, cookies, HttpRequest.BodyPublishers.noBody(), headers);
    }

    private Reply send(String method, String path, String cookies, HttpRequest.BodyPublisher body, String... headers)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body);
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
    public String freshToken() throws Exception {
        Reply meta = call("GET", "/api/v1/meta", null);
        String cookie = meta.setCookies(XSRF_COOKIE).getFirst();
        return cookie.substring(XSRF_COOKIE.length() + 1, cookie.indexOf(';'));
    }

    /** A POST carrying the cookie and the matching header, plus any extra cookie text. */
    public Reply postWithToken(String path, String token, String extraCookies) throws Exception {
        String cookies = XSRF_COOKIE + "=" + token + (extraCookies == null ? "" : "; " + extraCookies);
        return call("POST", path, cookies, "X-XSRF-TOKEN", token);
    }

    /** A JSON POST carrying a fresh anti-forgery token and the given session cookie (null for none). */
    public Reply postJson(String path, String sessionCookie, String json, String... headers) throws Exception {
        return postBody(path, sessionCookie, "application/json", json, headers);
    }

    /** A POST carrying a fresh anti-forgery token, the given session cookie and an arbitrary body and Content-Type. */
    public Reply postBody(String path, String sessionCookie, String contentType, String body, String... headers)
            throws Exception {
        return postPublished(path, sessionCookie, contentType, HttpRequest.BodyPublishers.ofString(body), headers);
    }

    /** As {@link #postBody} but sent with no declared length, so the body travels chunked. */
    public Reply postChunked(String path, String sessionCookie, String contentType, byte[] body, String... headers)
            throws Exception {
        return postPublished(
                path,
                sessionCookie,
                contentType,
                HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(body)),
                headers);
    }

    private Reply postPublished(
            String path, String sessionCookie, String contentType, HttpRequest.BodyPublisher body, String... headers)
            throws Exception {
        String token = freshToken();
        String cookies = XSRF_COOKIE + "=" + token + (sessionCookie == null ? "" : "; " + sessionCookie);
        String[] all = new String[headers.length + 4];
        all[0] = "X-XSRF-TOKEN";
        all[1] = token;
        all[2] = "Content-Type";
        all[3] = contentType;
        System.arraycopy(headers, 0, all, 4, headers.length);
        return send("POST", path, cookies, body, all);
    }

    /** The Cookie header text for the session cookie a reply issued, or null when it issued none. */
    public static String issuedSession(Reply reply) {
        List<String> issued = reply.setCookies(SESSION_COOKIE);
        return issued.isEmpty()
                ? null
                : issued.getFirst().substring(0, issued.getFirst().indexOf(';'));
    }
}
