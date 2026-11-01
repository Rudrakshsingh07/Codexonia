import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Verifies behavior that only a real servlet container provides: Spring Boot
 * dispatches HTML error responses to the static pages under {@code /error/}.
 */
@SpringBootTest(classes = CodexoniaApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void htmlRequestsGetTheCustomNotFoundPage() throws Exception {
        HttpResponse<String> response = get("/this-page-does-not-exist", "text/html");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("The trace ends here."),
                   "expected custom 404 page, got: " + response.body());
        assertTrue(response.body().contains("/assets/site.css"),
                   "custom 404 page should use the shared shell");
    }

    @Test
    void apiRequestsStillGetJsonErrors() throws Exception {
        HttpResponse<String> response = get("/this-page-does-not-exist", "application/json");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"status\":404"),
                   "expected JSON error body, got: " + response.body());
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
