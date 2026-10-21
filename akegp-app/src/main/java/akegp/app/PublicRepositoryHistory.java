package akegp.app;

import akegp.rules.github.GitHubRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Public GitHub activity only. Fixed API host, bounded bodies, no credentials or arbitrary URL fetching. */
@Component
public class PublicRepositoryHistory {
  private final RestClient client;
  private final JsonMapper json = JsonMapper.builder().build();
  public record Commit(String sha, String message, String at, String url) {}
  public record Overview(String repository, String url, String branch, String createdAt, String pushedAt,
      String language, List<Commit> commits, String coverage, String source) {}
  public PublicRepositoryHistory() {
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    factory.setReadTimeout(Duration.ofSeconds(8));
    client = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", "AKEGP-Architecture-Analytics")
        .defaultHeader("Accept", "application/vnd.github+json").build();
  }
  PublicRepositoryHistory(RestClient client) { this.client = client; }
  public Overview read(String url) {
    var repository = GitHubRepository.parse(url);
    String key = repository.owner() + "/" + repository.name();
    String endpoint = "https://api.github.com/repos/" + key;
    var metadata = get(endpoint);
    List<Commit> commits = new ArrayList<>();
    var history = get(endpoint + "/commits?per_page=20");
    if (!history.isArray() || !metadata.isObject()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected GitHub response");
    for (var item : history) {
      String sha = item.path("sha").asText("");
      if (!sha.matches("[a-fA-F0-9]{40,64}")) continue;
      var commit = item.path("commit");
      String message = commit.path("message").asText("").lines().findFirst().orElse("");
      commits.add(new Commit(sha, message.length() > 240 ? message.substring(0, 240) : message,
          commit.path("committer").path("date").asText(""), "https://github.com/" + key + "/commit/" + sha));
    }
    return new Overview(key, "https://github.com/" + key, metadata.path("default_branch").asText(""),
        metadata.path("created_at").asText(""), metadata.path("pushed_at").asText(""), metadata.path("language").asText("Unknown"),
        List.copyOf(commits), "Latest up to 20 commits on the default branch; not complete history or architecture analysis.", "GitHub public REST API");
  }
  private JsonNode get(String endpoint) {
    try {
      return client.get().uri(endpoint).exchange((request, response) -> {
        int status = response.getStatusCode().value();
        if (status != 200) throw new ResponseStatusException(status == 404 ? HttpStatus.NOT_FOUND : status == 403 || status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY,
            status == 404 ? "Public repository not found or private" : "GitHub activity is unavailable or rate limited; stored architecture history remains available");
        byte[] bytes = response.getBody().readNBytes(1_048_577);
        if (bytes.length > 1_048_576) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub response exceeds the activity preview limit");
        return json.readTree(bytes);
      });
    } catch (ResponseStatusException e) { throw e; }
    catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub activity could not be loaded; stored architecture history remains available"); }
  }
}
