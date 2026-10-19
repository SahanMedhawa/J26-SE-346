package akegp.enforcement.github;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.service.EnforcementException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real source: GET /repos/{owner}/{name}/pulls/{n} and /pulls/{n}/files on the GitHub REST API. */
public class HttpPullRequestSource implements PullRequestSource {
  static final int PAGE_SIZE = 100;
  static final int MAX_PAGES = 30;       // GitHub never lists more than 3000 files of a pull request

  private final HttpClient http;
  private final String apiUrl;
  private final String token;

  public HttpPullRequestSource(String apiUrl, String token) {
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    this.token = token;
  }

  @Override
  public PullRequestSnapshot fetch(String repo, int prNumber) {
    String base = apiUrl + "/repos/" + repo + "/pulls/" + prNumber;
    Map<String, Object> pr = GithubJson.object(parse(get(base), "pull request"));
    if (!"open".equals(GithubJson.string(pr.get("state"))))
      throw new EnforcementException(409, "The pull request is not open on GitHub, so it cannot be re-scanned");
    Map<String, Object> head = GithubJson.object(pr.get("head"));
    Map<String, Object> baseBranch = GithubJson.object(pr.get("base"));
    String headSha = GithubJson.string(head.get("sha"));
    if (headSha == null || headSha.isBlank()) throw new EnforcementException(502, "GitHub did not return the pull request's latest commit");
    String repoUrl = GithubJson.string(GithubJson.object(baseBranch.get("repo")).get("html_url"));

    List<FileChange> changes = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      List<Object> files = GithubJson.array(parse(get(base + "/files?per_page=" + PAGE_SIZE + "&page=" + page), "file list"));
      for (Object file : files) changes.add(toChange(GithubJson.object(file)));
      if (files.size() < PAGE_SIZE) break;
    }
    return new PullRequestSnapshot(repoUrl, headSha, GithubJson.string(baseBranch.get("sha")), changes);
  }

  static FileChange toChange(Map<String, Object> f) {
    String path = GithubJson.string(f.get("filename"));
    String patch = GithubJson.string(f.get("patch"));
    PatchParser.Lines lines = PatchParser.parse(patch);
    return new FileChange(path, status(GithubJson.string(f.get("status"))), GithubJson.string(f.get("previous_filename")),
        intOf(f.get("additions")), intOf(f.get("deletions")), false, lines.added(), lines.removed(), null);
  }

  /** GitHub says "removed" and "changed"; the pipeline's vocabulary is deleted and modified. */
  static String status(String github) {
    if (github == null) return "modified";
    return switch (github) {
      case "added", "renamed", "copied" -> github;
      case "removed" -> "deleted";
      default -> "modified";
    };
  }

  private static int intOf(Object o) { return o instanceof Double d ? d.intValue() : 0; }

  private static Object parse(String body, String what) {
    try {
      return GithubJson.parse(body);
    } catch (RuntimeException e) {
      throw new EnforcementException(502, "GitHub's answer for the " + what + " was not understood");
    }
  }

  private String get(String url) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .GET()
        .build();
    HttpResponse<String> response;
    try {
      response = http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new EnforcementException(502, "GitHub could not be reached");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EnforcementException(502, "The request to GitHub was interrupted");
    }
    int status = response.statusCode();
    if (status / 100 == 2) return response.body();
    if (status == 404) throw new EnforcementException(404, "GitHub cannot find this pull request, or the token cannot see the repository");
    if (status == 401 || status == 403)
      throw new EnforcementException(502, "GitHub refused the request (HTTP " + status + "): the token needs permission to read pull requests");
    throw new EnforcementException(502, "GitHub answered with HTTP " + status);
  }
}
