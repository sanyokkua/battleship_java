package ua.kostenko.battleship.app;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/**
 * The executable JAR that {@code package} produced, started as a child process with {@code java -jar} and only the
 * given environment on top of its default configuration (inherited {@code SPRING_*}, {@code SERVER_*} and
 * {@code BATTLESHIP_*} variables are removed). {@link #close()} and a JVM shutdown hook both kill the child.
 */
public final class PackagedService implements AutoCloseable {
    private static final Duration STARTUP = Duration.ofSeconds(90);

    private final Process process;
    private final Path output;
    private final SecurityHttp http;
    private final Thread killOnExit;

    private PackagedService(Process process, Path output, int port) {
        this.process = process;
        this.output = output;
        this.http = new SecurityHttp(port);
        this.killOnExit = new Thread(process::destroyForcibly);
        Runtime.getRuntime().addShutdownHook(killOnExit);
    }

    /** The JAR path failsafe passes in, from {@code app/pom.xml}. */
    public static Path jar() {
        return Path.of(System.getProperty("packaged.jar"));
    }

    /** Starts the JAR with {@code environment} added and returns once it answers {@code /api/v1/health}. */
    public static PackagedService start(Map<String, String> environment) throws Exception {
        try {
            return launch(environment);
        } catch (PortTaken taken) {
            return launch(environment); // the free port was taken between probing and binding
        }
    }

    private static final class PortTaken extends RuntimeException {
        PortTaken(String message) {
            super(message);
        }
    }

    private static PackagedService launch(Map<String, String> environment) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        Path output = Files.createTempFile("packaged-service", ".log");
        ProcessBuilder builder = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-jar",
                        jar().toString(),
                        "--server.port=" + port)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        builder.environment()
                .keySet()
                .removeIf(name ->
                        name.startsWith("SPRING_") || name.startsWith("SERVER_") || name.startsWith("BATTLESHIP_"));
        builder.environment().putAll(environment);
        PackagedService service = new PackagedService(builder.start(), output, port);
        try {
            service.awaitReady();
        } catch (Throwable failure) {
            service.close();
            throw failure;
        }
        return service;
    }

    private void awaitReady() throws Exception {
        long deadline = System.nanoTime() + STARTUP.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                String log = Files.readString(output);
                String message = "the packaged service exited with " + process.exitValue() + ":\n" + log;
                if (log.contains("already in use")) throw new PortTaken(message);
                throw new AssertionError(message);
            }
            try {
                if (get("/api/v1/health").status() == 200) return;
            } catch (IOException notYet) {
                // not listening yet
            }
            Thread.sleep(200);
        }
        throw new AssertionError(
                "the packaged service was not ready within " + STARTUP + ":\n" + Files.readString(output));
    }

    public Reply get(String path) throws Exception {
        return http.call("GET", path, null);
    }

    public JsonNode getJson(String path) throws Exception {
        return get(path).json();
    }

    @Override
    public void close() throws IOException {
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        try {
            Runtime.getRuntime().removeShutdownHook(killOnExit);
        } catch (IllegalStateException shuttingDown) {
            // the hook is already running
        }
        Files.deleteIfExists(output);
    }
}
