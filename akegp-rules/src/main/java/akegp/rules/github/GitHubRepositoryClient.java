package akegp.rules.github;

import akegp.rules.service.RepositoryBrowsingException;
import java.net.URI;
import java.util.List;
import java.util.TreeSet;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** At most two read-only GitHub API requests per browse, with no automatic retries. */
public class GitHubRepositoryClient {
  private static final String API_HOST = "https://api.github.com";
  private final RestClient client;
  private final int maxResponseBytes;
  private final int maxFiles;
  private final JsonMapper json = JsonMapper.builder().build();

  public GitHubRepositoryClient(RestClient client, int maxResponseBytes, int maxFiles) {
    if (maxResponseBytes < 1 || maxResponseBytes > 67_108_864 || maxFiles < 1 || maxFiles > 100_000) {
      throw new IllegalArgumentException("GitHub response limit must be 1..67108864 bytes and file limit 1..100000");
    }
    this.client = client;
    this.maxResponseBytes = maxResponseBytes;
    this.maxFiles = maxFiles;
  }

  public RepositoryTree files(GitHubRepository repository) {
    URI metadataUri = UriComponentsBuilder.fromUriString(API_HOST)
        .pathSegment("repos", repository.owner(), repository.name()).build().encode().toUri();
    String branch = requiredString(get(metadataUri), "default_branch");
    URI treeUri = UriComponentsBuilder.fromUriString(API_HOST)
        .pathSegment("repos", repository.owner(), repository.name(), "git", "trees", branch)
        .queryParam("recursive", "1").build().encode().toUri();
    JsonNode tree = get(treeUri);
    if (!tree.path("truncated").isBoolean() || !tree.path("tree").isArray()) throw malformed();
    if (tree.path("truncated").asBoolean()) {
      throw new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
          "GitHub returned a truncated repository tree; a complete file list is unavailable for this repository");
    }
    var files = new TreeSet<String>();
    for (JsonNode entry : tree.path("tree")) {
      String type = requiredString(entry, "type");
      if (!List.of("blob", "tree", "commit").contains(type)) throw malformed();
      if (!type.equals("blob")) continue; // Directories and submodule commits are not selectable files.
      String path = requiredString(entry, "path");
      if (path.startsWith("/") || path.contains("\\") || path.contains(":")
          || path.chars().anyMatch(Character::isISOControl)
          || List.of(path.split("/", -1)).stream().anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."))
          || !files.add(path)) throw malformed();
      if (files.size() > maxFiles) {
        throw new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
            "Repository exceeds the configured GitHub file limit; no partial file list was returned");
      }
    }
    return new RepositoryTree(branch, List.copyOf(files));
  }

  private JsonNode get(URI uri) {
    try {
      return client.get().uri(uri).exchange((request, response) -> {
        int status = response.getStatusCode().value();
        if (status != 200) throw upstreamError(status, response.getHeaders());
        if (response.getHeaders().getContentLength() > maxResponseBytes) throw tooLarge();
        byte[] body = response.getBody().readNBytes(maxResponseBytes + 1);
        if (body.length > maxResponseBytes) throw tooLarge();
        JsonNode value = json.readTree(body);
        if (value == null || !value.isObject()) throw malformed();
        return value;
      });
    } catch (JacksonException exception) {
      throw malformed();
    } catch (RestClientException exception) {
      // Do not propagate upstream bodies, request headers, or credential-bearing exception details.
      throw new RepositoryBrowsingException(HttpStatus.SERVICE_UNAVAILABLE,
          "GitHub could not be reached or the request timed out; try again later");
    }
  }

  private static String requiredString(JsonNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isString() || node.asString().isBlank()) throw malformed();
    return node.asString();
  }

  private static RepositoryBrowsingException upstreamError(int status, HttpHeaders headers) {
    if (status == 429 || status == 403 && ("0".equals(headers.getFirst("X-RateLimit-Remaining"))
        || headers.containsHeader("Retry-After"))) {
      return new RepositoryBrowsingException(HttpStatus.TOO_MANY_REQUESTS,
          "GitHub API rate limit reached; retry later or configure an authorized GitHub token",
          headers.getFirst("Retry-After"));
    }
    if (status == 404) return new RepositoryBrowsingException(HttpStatus.NOT_FOUND,
        "GitHub repository or default branch was not found or is private and inaccessible with the configured token");
    if (status == 401 || status == 403) return new RepositoryBrowsingException(HttpStatus.FORBIDDEN,
        "GitHub denied access; check the configured token and repository permissions");
    if (status == 409) return new RepositoryBrowsingException(HttpStatus.CONFLICT,
        "GitHub repository has no available file tree; it may be empty");
    if (status >= 300 && status < 400) return new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
        "GitHub redirected this repository; update the project repository URL to its current GitHub location");
    return new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
        "GitHub API could not return the repository file list (HTTP " + status + ")");
  }

  private static RepositoryBrowsingException malformed() {
    return new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
        "GitHub returned an invalid or incomplete repository response; no file list was returned");
  }

  private static RepositoryBrowsingException tooLarge() {
    return new RepositoryBrowsingException(HttpStatus.BAD_GATEWAY,
        "GitHub response exceeds the configured byte limit; no partial file list was returned");
  }

  public record RepositoryTree(String branch, List<String> files) {}
}
