package ua.kostenko.battleship.app.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A real-server Server-Sent Events reader for the stream ITs: it keeps the raw lines, so framing can be asserted as
 * sent, and groups them into events at each blank line. {@link HttpClient#send} with a string body would block until
 * the stream ends, so lines are read asynchronously into a queue.
 */
public final class SseStream implements AutoCloseable {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String END = new String("<end of stream>");

    /** One event as received: its raw lines (without the blank terminator) and the parsed fields. */
    public record Event(List<String> lines, String name, String id, String data) {}

    private final HttpResponse<Stream<String>> response;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    private SseStream(HttpResponse<Stream<String>> response) {
        this.response = response;
        Thread.ofVirtual().start(() -> {
            try (Stream<String> body = response.body()) {
                body.forEach(lines::add);
            } catch (RuntimeException closed) {
                // the reader closed the stream or the server ended it; either way it is over
            } finally {
                lines.add(END);
            }
        });
    }

    /** Opens {@code GET path} with {@code Accept: text/event-stream}; {@code headers} are extra name/value pairs. */
    public static SseStream open(int port, String path, String cookies, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/event-stream")
                .GET();
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient()
                .sendAsync(request.build(), HttpResponse.BodyHandlers.ofLines())
                .get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        return new SseStream(response);
    }

    public int status() {
        return response.statusCode();
    }

    public String header(String name) {
        return response.headers().firstValue(name).orElse("");
    }

    /** The next complete event; fails when none arrives in time or the stream ends first. */
    public Event next() throws InterruptedException {
        List<String> raw = new ArrayList<>();
        while (true) {
            String line = lines.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            if (line == null) throw new AssertionError("no event within " + WAIT + "; partial lines " + raw);
            if (line == END) throw new AssertionError("the stream ended; partial lines " + raw);
            if (!line.isEmpty()) {
                raw.add(line);
            } else if (!raw.isEmpty()) {
                return parse(raw);
            }
        }
    }

    /** True when the server ends the stream before any further line arrives. */
    public boolean ended() throws InterruptedException {
        String line = lines.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        if (line == null) throw new AssertionError("the stream neither ended nor sent anything within " + WAIT);
        return line == END;
    }

    private static Event parse(List<String> raw) {
        String name = null;
        String id = null;
        StringBuilder data = null;
        for (String line : raw) {
            int colon = line.indexOf(':');
            if (colon == 0) continue;
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            switch (field) {
                case "event" -> name = value;
                case "id" -> id = value;
                case "data" ->
                    data = data == null
                            ? new StringBuilder(value)
                            : data.append('\n').append(value);
                default -> {}
            }
        }
        return new Event(List.copyOf(raw), name, id, data == null ? null : data.toString());
    }

    @Override
    public void close() {
        response.body().close();
    }
}
