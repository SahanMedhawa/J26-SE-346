package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import akegp.rules.api.*;
import akegp.rules.config.GitHubBrowsingConfig;
import akegp.rules.domain.*;
import akegp.rules.github.GitHubRepositoryClient;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.service.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RepositoryBrowsingControllerTest {
  private MockRestServiceServer server;
  private MockMvc mvc;
  private ProjectRegistrationServiceImpl projects;
  private InMemoryProjectRepository repository;
  private UUID project;

  @BeforeEach
  void setUp() {
    repository = new InMemoryProjectRepository();
    projects = new ProjectRegistrationServiceImpl(repository);
    project = projects.register(new ProjectRegistrationRequest("Orders", null,
        "https://github.com/shop/orders", Set.of(ArchitectureStyle.LAYERED))).id();
    configure("", 8192, 20);
  }

  private void configure(String token, int bytes, int files) {
    var builder = GitHubBrowsingConfig.clientBuilder(token, 1000, 1000);
    server = MockRestServiceServer.bindTo(builder).build();
    var github = new GitHubRepositoryClient(builder.build(), bytes, files);
    mvc = MockMvcBuilders.standaloneSetup(new RepositoryBrowsingController(
            new RepositoryBrowsingService(projects, github)), new ProjectRegistrationController(projects))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler()).build();
  }

  @AfterEach
  void verifyRequests() { server.verify(); }

  private String url(UUID id) { return "/api/rules/projects/" + id + "/repository/files"; }

  private ResultActions browse() throws Exception { return mvc.perform(get(url(project))); }

  private void metadata(String branch) {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(headerDoesNotExist("Authorization"))
        .andExpect(header("Accept", "application/vnd.github+json"))
        .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
        .andRespond(withSuccess("{\"default_branch\":\"" + branch + "\"}", MediaType.APPLICATION_JSON));
  }

  private void tree(String branch, String body) {
    server.expect(requestTo("https://api.github.com/repos/shop/orders/git/trees/" + branch + "?recursive=1"))
        .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
  }

  @Test
  void listsPublicRepositoryRecursivelyOnActualDefaultBranchExcludingDirectoriesAndSubmodules() throws Exception {
    metadata("develop");
    tree("develop", """
        {"truncated":false,"tree":[
          {"type":"tree","path":"src"},
          {"type":"blob","path":"src/main/Order.java"},
          {"type":"blob","path":"README.md"},
          {"type":"commit","path":"vendor/library"}]}
        """);
    browse().andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(project.toString()))
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/shop/orders"))
        .andExpect(jsonPath("$.branch").value("develop"))
        .andExpect(jsonPath("$.files.length()").value(2))
        .andExpect(jsonPath("$.files[0]").value("README.md"))
        .andExpect(jsonPath("$.files[1]").value("src/main/Order.java"))
        .andExpect(jsonPath("$.token").doesNotExist());
  }

  @Test
  void encodesDefaultBranchContainingSlash() throws Exception {
    metadata("release/pp1");
    tree("release%2Fpp1", "{\"truncated\":false,\"tree\":[]}");
    browse().andExpect(status().isOk()).andExpect(jsonPath("$.branch").value("release/pp1"));
  }

  @Test
  void sendsOptionalTokenOnlyToGitHubAndNeverInResponse() throws Exception {
    configure("test-token-never-return", 8192, 20);
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andExpect(header("Authorization", "Bearer test-token-never-return"))
        .andRespond(withSuccess("{\"default_branch\":\"master\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.github.com/repos/shop/orders/git/trees/master?recursive=1"))
        .andExpect(header("Authorization", "Bearer test-token-never-return"))
        .andRespond(withSuccess("{\"truncated\":false,\"tree\":[]}", MediaType.APPLICATION_JSON));
    String response = browse().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(response.contains("test-token"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "https://gitlab.com/shop/orders", "http://localhost/internal"})
  void rejectsMissingOrUnsupportedProjectUrlWithoutFetchingAnything(String repositoryUrl) throws Exception {
    var old = projects.findById(project);
    repository.save(old.update(old.name(), old.description(), repositoryUrl, old.architectureStyles()));
    browse().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    // Existing project retrieval remains valid even when browsing is unavailable.
    mvc.perform(get("/api/rules/projects/" + project)).andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryUrl").value(repositoryUrl));
  }

  @Test
  void handlesLegacyNullUrlWithoutNetworkAccess() throws Exception {
    repository.save(new RegisteredProject(UUID.randomUUID(), "Legacy", null, null,
        Set.of(ArchitectureStyle.LAYERED), Instant.now(), ProjectStatus.ACTIVE));
    UUID legacy = repository.findAll().stream().filter(p -> p.name().equals("Legacy")).findFirst().orElseThrow().id();
    mvc.perform(get(url(legacy))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no repository URL")));
  }

  @Test
  void rejectsMissingProjectAndInvalidUuidWithoutNetworkAccess() throws Exception {
    mvc.perform(get(url(UUID.randomUUID()))).andExpect(status().isNotFound());
    mvc.perform(get("/api/rules/projects/invalid/repository/files")).andExpect(status().isBadRequest());
  }

  @Test
  void usesSelectedProjectsUrlAndExistingProjectEditing() throws Exception {
    UUID other = projects.register(new ProjectRegistrationRequest("Payments", null,
        "https://github.com/another/payments", Set.of(ArchitectureStyle.LAYERED))).id();
    server.expect(requestTo("https://api.github.com/repos/another/payments"))
        .andRespond(withSuccess("{\"default_branch\":\"stable\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.github.com/repos/another/payments/git/trees/stable?recursive=1"))
        .andRespond(withSuccess("{\"truncated\":false,\"tree\":[{\"type\":\"blob\",\"path\":\"payment.sql\"}]}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://api.github.com/repos/shop/renamed"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    mvc.perform(get(url(other))).andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(other.toString()))
        .andExpect(jsonPath("$.files[0]").value("payment.sql"));
    mvc.perform(put("/api/rules/projects/" + project).contentType(MediaType.APPLICATION_JSON).content("""
        {"name":"Orders","repositoryUrl":"https://github.com/shop/renamed","architectureStyles":["LAYERED"]}
        """)).andExpect(status().isOk()).andExpect(jsonPath("$.repositoryUrl").value("https://github.com/shop/renamed"));
    browse().andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource({"401,403", "403,403", "404,404", "409,409", "422,502", "500,502", "503,502", "301,502"})
  void translatesGitHubErrorsWithoutExposingUpstreamBodies(int githubStatus, int expected) throws Exception {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andRespond(withStatus(HttpStatus.valueOf(githubStatus))
            .body("sensitive-upstream-details").header("Location", "http://localhost/internal"));
    var result = browse().andExpect(status().is(expected)).andExpect(jsonPath("$.message").isNotEmpty());
    assertFalse(result.andReturn().getResponse().getContentAsString().contains("sensitive-upstream-details"));
  }

  @Test
  void handlesFailureDuringTreeRequest() throws Exception {
    metadata("main");
    server.expect(requestTo("https://api.github.com/repos/shop/orders/git/trees/main?recursive=1"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    browse().andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 429})
  void reportsRateLimitsAndRetryAfter(int githubStatus) throws Exception {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andRespond(withStatus(HttpStatus.valueOf(githubStatus))
            .header("X-RateLimit-Remaining", "0").header("Retry-After", "60"));
    browse().andExpect(status().isTooManyRequests())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rate limit")));
  }

  @Test
  void handlesPrimaryRateLimitWithoutRetryAfterHeader() throws Exception {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).header("X-RateLimit-Remaining", "0"));
    browse().andExpect(status().isTooManyRequests());
  }

  @Test
  void handlesNetworkFailureWithoutExposingExceptionDetails() throws Exception {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andRespond(withException(new IOException("secret-connection-details")));
    var result = browse().andExpect(status().isServiceUnavailable());
    assertFalse(result.andReturn().getResponse().getContentAsString().contains("secret-connection-details"));
  }

  @Test
  void rejectsTruncatedTreeRatherThanReturningPartialFiles() throws Exception {
    metadata("main");
    tree("main", "{\"truncated\":true,\"tree\":[{\"type\":\"blob\",\"path\":\"partial.java\"}]}");
    browse().andExpect(status().isBadGateway()).andExpect(jsonPath("$.files").doesNotExist())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("truncated")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{", "{}", "{\"default_branch\":null}", "{\"default_branch\":42}"})
  void rejectsMalformedMetadata(String body) throws Exception {
    server.expect(requestTo("https://api.github.com/repos/shop/orders"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    browse().andExpect(status().isBadGateway());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"truncated\":false}", "{\"tree\":[]}",
      "{\"truncated\":false,\"tree\":[{\"type\":\"blob\"}]}",
      "{\"truncated\":false,\"tree\":[{\"type\":\"blob\",\"path\":\"../a\"}]}",
      "{\"truncated\":false,\"tree\":[{\"type\":\"unknown\",\"path\":\"a\"}]}"})
  void rejectsIncompleteOrInvalidTreeResponses(String body) throws Exception {
    metadata("main");
    tree("main", body);
    browse().andExpect(status().isBadGateway()).andExpect(jsonPath("$.files").doesNotExist());
  }

  @Test
  void enforcesFileLimitWithoutReturningPartialResults() throws Exception {
    configure("", 8192, 1);
    metadata("main");
    tree("main", """
        {"truncated":false,"tree":[{"type":"blob","path":"a"},{"type":"blob","path":"b"}]}
        """);
    browse().andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("file limit")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void enforcesResponseByteLimitWithAndWithoutContentLength(boolean contentLength) throws Exception {
    configure("", 64, 20);
    var response = withSuccess(" ".repeat(65), MediaType.APPLICATION_JSON);
    if (contentLength) response.header("Content-Length", "65");
    server.expect(requestTo("https://api.github.com/repos/shop/orders")).andRespond(response);
    browse().andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("byte limit")));
  }

  @Test
  void rejectsUnlimitedOrInvalidConfiguration() {
    assertThrows(IllegalArgumentException.class, () -> GitHubBrowsingConfig.requestFactory(0, 1000));
    assertThrows(IllegalArgumentException.class, () -> GitHubBrowsingConfig.requestFactory(1000, -1));
    assertThrows(IllegalArgumentException.class, () -> new GitHubRepositoryClient(null, Integer.MAX_VALUE, 20));
    assertThrows(IllegalArgumentException.class, () -> new GitHubRepositoryClient(null, 8192, 0));
  }
}
