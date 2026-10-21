package akegp.enforcement.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpGithubStatusClientTest {
  record Received(String method, String path, String auth, String accept, String version, String body) {}

  private HttpServer server;
  private final List<Received> received = new ArrayList<>();
  private int responseCode = 201;

  @BeforeEach void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> {
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      var h = ex.getRequestHeaders();
      received.add(new Received(ex.getRequestMethod(), ex.getRequestURI().getPath(), h.getFirst("Authorization"),
          h.getFirst("Accept"), h.getFirst("X-GitHub-Api-Version"), body));
      byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(responseCode, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
  }

  @AfterEach void stop() { server.stop(0); }

  private HttpGithubStatusClient client() {
    return new HttpGithubStatusClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "tok",
        "architecture-enforcement");
  }

  @Test void postsTheStatusTheWayGithubExpectsIt() {
    client().setStatus("acme/shop", "a1b2c3d", StatusState.PENDING, "Held: 1 violation");
    Received r = received.get(0);
    assertEquals("POST", r.method());
    assertEquals("/repos/acme/shop/statuses/a1b2c3d", r.path());
    assertEquals("Bearer tok", r.auth());
    assertEquals("application/vnd.github+json", r.accept());
    assertEquals("2022-11-28", r.version());
    assertEquals("{\"state\":\"pending\",\"context\":\"architecture-enforcement\",\"description\":\"Held: 1 violation\"}",
        r.body());
  }

  @Test void descriptionIsCutToGithubsLimit() {
    client().setStatus("o/r", "abc1234", StatusState.SUCCESS, "x".repeat(300));
    String body = received.get(0).body();
    assertTrue(body.contains("\"description\":\"" + "x".repeat(140) + "\""));
  }

  @Test void quotesAndNewlinesAreEscaped() {
    client().setStatus("o/r", "abc1234", StatusState.FAILURE, "say \"no\"\nnow \\ done");
    assertTrue(received.get(0).body().contains("say \\\"no\\\"\\nnow \\\\ done"));
  }

  @Test void errorResponseBecomesAnException() {
    responseCode = 422;
    var e = assertThrows(GithubStatusException.class,
        () -> client().setStatus("o/r", "abc1234", StatusState.SUCCESS, "d"));
    assertTrue(e.getMessage().contains("422"));
  }

  @Test void unreachableGithubBecomesAnException() {
    var dead = new HttpGithubStatusClient("http://127.0.0.1:1", "tok", "ctx");
    assertThrows(GithubStatusException.class, () -> dead.setStatus("o/r", "abc1234", StatusState.SUCCESS, "d"));
  }
}
