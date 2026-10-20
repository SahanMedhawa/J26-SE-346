package akegp.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PublicRepositoryHistoryTest {
  @Test void previewUsesFixedApiHostAndBoundedCommitCoverage() {
    var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.github.com/repos/SahanMedhawa/HealthMate"))
        .andRespond(withSuccess("{\"default_branch\":\"main\",\"language\":\"TypeScript\",\"created_at\":\"2026-01-01\",\"pushed_at\":\"2026-10-20\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.github.com/repos/SahanMedhawa/HealthMate/commits?per_page=20"))
        .andRespond(withSuccess("[{\"sha\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"commit\":{\"message\":\"A change\\nExtra details\",\"committer\":{\"date\":\"2026-10-20\"}}}]", MediaType.APPLICATION_JSON));
    var history = new PublicRepositoryHistory(builder.build());
    var result = history.read("https://github.com/SahanMedhawa/HealthMate.git");
    assertEquals("main", result.branch()); assertEquals(1, result.commits().size()); assertEquals("A change", result.commits().getFirst().message());
    assertTrue(result.coverage().contains("not complete history"));
    assertThrows(akegp.rules.service.RepositoryBrowsingException.class, () -> history.read("https://localhost/private"));
    server.verify();
  }
  @Test void rateLimitsAreExplicitInsteadOfInventingActivity() {
    var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://api.github.com/repos/example/repo")).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));
    var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> new PublicRepositoryHistory(builder.build()).read("https://github.com/example/repo"));
    assertEquals(429, error.getStatusCode().value()); server.verify();
  }
}
