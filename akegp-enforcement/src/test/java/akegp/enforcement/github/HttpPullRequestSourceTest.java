package akegp.enforcement.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.enforcement.service.EnforcementException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpPullRequestSourceTest {
  private HttpServer server;
  private final List<String> seenAuth = new CopyOnWriteArrayList<>();
  private volatile int prStatus = 200;
  private volatile String prBody;
  private volatile int filesStatus = 200;
  private int fileCount = 2;

  @BeforeEach
  void start() throws IOException {
    prBody = pr("open", "headsha1", "basesha1");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/repos/o/r/pulls/5", ex -> {
      seenAuth.add(ex.getRequestHeaders().getFirst("Authorization"));
      boolean files = ex.getRequestURI().getPath().endsWith("/files");
      int status = files ? filesStatus : prStatus;
      String body = status != 200 ? "{\"message\":\"nope\"}" : files ? filesPage(ex.getRequestURI().getQuery()) : prBody;
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("content-type", "application/json");
      ex.sendResponseHeaders(status, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    });
    server.start();
  }

  @AfterEach
  void stop() { server.stop(0); }

  private HttpPullRequestSource source() { return new HttpPullRequestSource("http://127.0.0.1:" + server.getAddress().getPort(), "tkn"); }

  private static String pr(String state, String head, String base) {
    return "{\"state\":\"" + state + "\",\"head\":{\"sha\":\"" + head + "\"},\"base\":{\"sha\":\"" + base
        + "\",\"repo\":{\"html_url\":\"https://github.com/o/r\"}}}";
  }

  private String filesPage(String query) {
    int page = query != null && query.contains("page=2") ? 2 : 1;
    List<String> items = new ArrayList<>();
    int from = (page - 1) * HttpPullRequestSource.PAGE_SIZE;
    for (int i = from; i < Math.min(fileCount, from + HttpPullRequestSource.PAGE_SIZE); i++) {
      items.add(i == 0
          ? "{\"filename\":\"src/A.java\",\"status\":\"modified\",\"additions\":1,\"deletions\":1,\"patch\":\"@@ -1,2 +1,2 @@\\n-old\\n+new\\n same\"}"
          : "{\"filename\":\"f" + i + ".txt\",\"status\":\"removed\",\"additions\":0,\"deletions\":3}");
    }
    return "[" + String.join(",", items) + "]";
  }

  @Test void readsTheCurrentCommitAndTheChangedLines() {
    PullRequestSnapshot s = source().fetch("o/r", 5);
    assertEquals("headsha1", s.headSha());
    assertEquals("basesha1", s.baseSha());
    assertEquals("https://github.com/o/r", s.repoUrl());
    assertEquals(2, s.changes().size());
    var a = s.changes().get(0);
    assertEquals("src/A.java", a.path());
    assertEquals("modified", a.status());
    assertEquals("new", a.addedLines().get(0).text());
    assertEquals(1, a.addedLines().get(0).line());
    assertEquals("deleted", s.changes().get(1).status());
    assertTrue(seenAuth.stream().allMatch("Bearer tkn"::equals));
  }

  @Test void readsEveryPageOfFiles() {
    fileCount = HttpPullRequestSource.PAGE_SIZE + 3;
    assertEquals(HttpPullRequestSource.PAGE_SIZE + 3, source().fetch("o/r", 5).changes().size());
  }

  @Test void aClosedPullRequestIsRefused() {
    prBody = pr("closed", "h", "b");
    assertEquals(409, assertThrows(EnforcementException.class, () -> source().fetch("o/r", 5)).status());
  }

  @Test void githubErrorsBecomeSafeMessages() {
    prStatus = 404;
    assertEquals(404, assertThrows(EnforcementException.class, () -> source().fetch("o/r", 5)).status());
    prStatus = 403;
    var e = assertThrows(EnforcementException.class, () -> source().fetch("o/r", 5));
    assertEquals(502, e.status());
    assertTrue(e.getMessage().contains("permission"));
    prStatus = 200; filesStatus = 500;
    assertEquals(502, assertThrows(EnforcementException.class, () -> source().fetch("o/r", 5)).status());
  }

  @Test void unreachableGithubIsAnError() {
    var down = new HttpPullRequestSource("http://127.0.0.1:1", "t");
    assertEquals(502, assertThrows(EnforcementException.class, () -> down.fetch("o/r", 5)).status());
  }

  @Test void garbageAnswerIsAnError() {
    prBody = "not json";
    assertEquals(502, assertThrows(EnforcementException.class, () -> source().fetch("o/r", 5)).status());
  }

  @Test void statusNamesAreTranslated() {
    assertEquals("deleted", HttpPullRequestSource.status("removed"));
    assertEquals("modified", HttpPullRequestSource.status("changed"));
    assertEquals("renamed", HttpPullRequestSource.status("renamed"));
    assertEquals("modified", HttpPullRequestSource.status(null));
  }

  @Test void withoutATokenTheFixCannotBeVerified() {
    assertEquals(503, assertThrows(EnforcementException.class, () -> new NoPullRequestSource().fetch("o/r", 5)).status());
  }
}
