package akegp.rules.github;

import akegp.rules.service.RepositoryBrowsingException;
import java.net.URI;
import org.springframework.http.HttpStatus;

/** Parses repository identities only; user-supplied URLs are never fetched. */
public record GitHubRepository(String owner, String name) {
  public static GitHubRepository parse(String repositoryUrl) {
    if (repositoryUrl == null || repositoryUrl.isBlank()) {
      throw new RepositoryBrowsingException(HttpStatus.BAD_REQUEST,
          "The selected project has no repository URL; edit the project before browsing");
    }
    try {
      URI uri = URI.create(repositoryUrl.strip());
      if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
          || uri.getPort() != -1 || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null || uri.getRawFragment() != null) {
        throw new IllegalArgumentException();
      }
      String path = uri.getRawPath();
      if (path == null || !path.matches("/[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}/?")) {
        throw new IllegalArgumentException();
      }
      String[] parts = path.replaceAll("/$", "").substring(1).split("/");
      String name = parts[1].replaceFirst("\\.git$", "");
      if (name.isEmpty() || name.equals(".") || name.equals("..")) throw new IllegalArgumentException();
      return new GitHubRepository(parts[0], name);
    } catch (IllegalArgumentException exception) {
      throw new RepositoryBrowsingException(HttpStatus.BAD_REQUEST,
          "Repository browsing requires a GitHub URL: https://github.com/owner/repository");
    }
  }
}
