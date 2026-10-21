package akegp.enforcement.github;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Real client: POST /repos/{owner}/{name}/statuses/{sha} on the GitHub REST API. */
public class HttpGithubStatusClient implements GithubStatusClient {
  static final int MAX_DESCRIPTION = 140; // GitHub's limit

  private final HttpClient http;
  private final String apiUrl;
  private final String token;
  private final String context;

  public HttpGithubStatusClient(String apiUrl, String token, String context) {
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    this.token = token;
    this.context = context;
  }

  @Override
  public void setStatus(String repo, String sha, StatusState state, String description) {
    String body = "{\"state\":" + quote(state.wire()) + ",\"context\":" + quote(context)
        + ",\"description\":" + quote(truncate(description)) + "}";
    HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/repos/" + repo + "/statuses/" + sha))
        .timeout(Duration.ofSeconds(15))
        .header("Authorization", "Bearer " + token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2)
        throw new GithubStatusException("GitHub status failed: " + response.statusCode() + " " + response.body());
    } catch (IOException e) {
      throw new GithubStatusException("GitHub status failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GithubStatusException("GitHub status interrupted", e);
    }
  }

  static String truncate(String s) {
    if (s == null) return "";
    return s.length() <= MAX_DESCRIPTION ? s : s.substring(0, MAX_DESCRIPTION);
  }

  /** Minimal JSON string escaping, so this class needs no JSON library. */
  static String quote(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
      }
    }
    return sb.append('"').toString();
  }
}
