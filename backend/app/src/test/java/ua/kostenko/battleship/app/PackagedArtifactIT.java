package ua.kostenko.battleship.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ua.kostenko.battleship.app.security.SecurityHttp;
import ua.kostenko.battleship.app.security.SecurityHttp.Reply;

/**
 * Owner of S10: the executable JAR starts from default configuration with {@code java -jar}, reports itself ready,
 * bundles and serves no user-interface asset (R57), and answers the unauthenticated operations exactly as the
 * in-process run of the same application does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PackagedArtifactIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> UNAUTHENTICATED = List.of("/api/v1/meta", "/api/v1/rulesets", "/api/v1/health");

    private static PackagedService packaged;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startThePackagedJar() throws Exception {
        packaged = PackagedService.start(Map.of());
    }

    @AfterAll
    static void stopThePackagedJar() throws Exception {
        if (packaged != null) packaged.close();
    }

    // (1)

    @Test
    void theJarStartsFromDefaultConfigurationAndReportsItselfReady() throws Exception {
        Reply health = packaged.get("/api/v1/health");

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.json()).isEqualTo(JSON.readTree("{\"live\":true,\"ready\":true}"));
    }

    // (2)

    @Test
    void noUserInterfaceAssetIsServed() throws Exception {
        for (String path : List.of("/", "/index.html", "/static/x.js", "/assets/x.css")) {
            assertThat(packaged.get(path).status()).as("GET " + path).isEqualTo(404);
        }
    }

    // (3)

    @Test
    void theJarEntryListHoldsNoHtmlCssOrClientScript() throws Exception {
        try (JarFile jar = new JarFile(PackagedService.jar().toFile())) {
            List<String> entries = Collections.list(jar.entries()).stream()
                    .map(java.util.zip.ZipEntry::getName)
                    .toList();

            assertThat(entries).isNotEmpty();
            assertThat(entries)
                    .filteredOn(name -> name.matches("(?i).*\\.(html?|css|m?js|map)$"))
                    .as("user-interface assets in the JAR")
                    .isEmpty();
            assertThat(entries)
                    .filteredOn(name -> name.matches("(?i)^BOOT-INF/classes/(static|public|resources|templates)/.*"))
                    .as("static resource roots in the JAR")
                    .isEmpty();
        }
    }

    // (4)

    @Test
    void theUnauthenticatedOperationsAnswerAsTheInProcessRunDoes() throws Exception {
        SecurityHttp inProcess = new SecurityHttp(port);
        for (String path : UNAUTHENTICATED) {
            Reply here = inProcess.call("GET", path, null);
            Reply jar = packaged.get(path);

            assertThat(jar.status()).as(path).isEqualTo(here.status());
            assertThat(withoutServerTime(jar.json())).as(path).isEqualTo(withoutServerTime(here.json()));
        }
    }

    private static JsonNode withoutServerTime(JsonNode body) {
        return ((ObjectNode) body.deepCopy()).without("serverTime");
    }
}
