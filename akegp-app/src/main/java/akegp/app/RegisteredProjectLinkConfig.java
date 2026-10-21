package akegp.app;

import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.rules.repository.ProjectRepository;
import akegp.rules.domain.RegisteredProject;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Links enforcement PRs to the project registration already managed by the Rules module. */
@Configuration(proxyBeanMethods = false)
public class RegisteredProjectLinkConfig {
  @Bean
  @Primary
  ProjectLinkResolver registeredProjectLinkResolverWithUrl(ProjectRepository projects) {
    return new ProjectLinkResolver() {
      @Override public Optional<String> projectIdFor(String repo) {
        return findProject(repo, projects).map(project -> project.id().toString());
      }

      @Override public Optional<String> repositoryUrlFor(String repo) {
        return findProject(repo, projects).map(RegisteredProject::repositoryUrl);
      }
    };
  }

  private static Optional<RegisteredProject> findProject(String repo, ProjectRepository projects) {
    String target = repositorySlug(repo);
    if (target == null) return Optional.empty();
    return projects.findAll().stream().filter(project -> target.equals(repositorySlug(project.repositoryUrl()))).findFirst();
  }

  private static String repositorySlug(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      URI uri = value.contains("://") ? URI.create(value) : URI.create("https://github.com/" + value);
      if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) return null;
      String path = uri.getPath().replaceAll("^/+|/+$", "");
      if (path.toLowerCase(Locale.ROOT).endsWith(".git")) path = path.substring(0, path.length() - 4);
      return path.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
