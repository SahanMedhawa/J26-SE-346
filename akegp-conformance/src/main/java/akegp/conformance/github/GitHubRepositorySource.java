package akegp.conformance.github;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubRepositorySource {
  private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private final String token;

  public GitHubRepositorySource(@Value("${GITHUB_TOKEN:}") String token) {
    this.token = token;
  }

  public RepositoryWorkspace download(String repositoryUrl) throws IOException, InterruptedException {
    RepositoryRef repository = parse(repositoryUrl);
    Path workspace = Files.createTempDirectory("akegp-github-");
    String archiveUrl = "https://api.github.com/repos/" + repository.owner() + "/" + repository.name() + "/zipball/" + repository.ref();
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(archiveUrl))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "akegp-conformance");
    if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
    HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("GitHub archive download failed with HTTP " + response.statusCode());
    }
    try (InputStream body = response.body(); ZipInputStream zip = new ZipInputStream(body)) {
      extractZip(zip, workspace);
    } catch (Exception error) {
      deleteRecursively(workspace);
      if (error instanceof IOException io) throw io;
      throw new IOException("Could not unpack GitHub repository", error);
    }
    Path root = singleDirectory(workspace).orElse(workspace);
    return new RepositoryWorkspace(root, workspace);
  }

  RepositoryRef parse(String repositoryUrl) {
    URI uri = URI.create(repositoryUrl.trim());
    if (!"https".equalsIgnoreCase(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())) {
      throw new IllegalArgumentException("Repository URL must be an HTTPS GitHub URL");
    }
    String[] parts = uri.getPath().split("/");
    if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
      throw new IllegalArgumentException("GitHub URL must look like https://github.com/owner/repository");
    }
    String name = parts[2].toLowerCase(Locale.ROOT).endsWith(".git") ? parts[2].substring(0, parts[2].length() - 4) : parts[2];
    String ref = "HEAD";
    if (parts.length >= 5 && "tree".equals(parts[3]) && !parts[4].isBlank()) ref = parts[4];
    return new RepositoryRef(parts[1], name, ref);
  }

  private void extractZip(ZipInputStream zip, Path destination) throws IOException {
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      Path target = destination.resolve(entry.getName()).normalize();
      if (!target.startsWith(destination)) throw new IOException("Unsafe archive entry");
      if (entry.isDirectory()) Files.createDirectories(target);
      else {
        Files.createDirectories(target.getParent());
        Files.copy(zip, target);
      }
    }
  }

  private Optional<Path> singleDirectory(Path workspace) throws IOException {
    try (var paths = Files.list(workspace)) {
      return paths.filter(Files::isDirectory).findFirst();
    }
  }

  private void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
      });
    }
  }

  public record RepositoryWorkspace(Path path, Path cleanupRoot) implements AutoCloseable {
    @Override public void close() throws IOException {
      if (Files.exists(cleanupRoot)) {
        try (var paths = Files.walk(cleanupRoot)) {
          paths.sorted((left, right) -> right.compareTo(left)).forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
          });
        }
      }
    }
  }

  record RepositoryRef(String owner, String name, String ref) {}
}