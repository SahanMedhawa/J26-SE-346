package akegp.app;

import akegp.enforcement.config.EnforcementProperties;
import akegp.enforcement.policy.ConfiguredProjectLinkResolver;
import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.service.ProjectRegistrationService;
import java.util.Optional;
import org.springframework.context.annotation.*;

/** Newly received pipeline events resolve to the same registered project as the web workspace. */
@Configuration(proxyBeanMethods = false)
public class ProjectEnforcementLinks {
  @Bean @Primary
  ProjectLinkResolver registeredProjectLinks(ProjectRegistrationService projects, EnforcementProperties properties) {
    var fallback = new ConfiguredProjectLinkResolver(properties.getProjects(), properties.getDefaultProjectId());
    return new ProjectLinkResolver() {
      private ProjectMatch find(String repo) {
        var matches = projects.findAll().stream()
            .filter(p -> ProjectAnalyticsController.repositoryKey(p.repositoryUrl()).equalsIgnoreCase(repo)).toList();
        return matches.size() == 1 && matches.getFirst().status() == ProjectStatus.ACTIVE
            ? new ProjectMatch(matches.getFirst().id().toString(), matches.getFirst().repositoryUrl())
            : null;
      }

      @Override public Optional<String> projectIdFor(String repo) {
        var match = find(repo);
        if (match != null) return Optional.of(match.projectId());
        boolean registered = projects.findAll().stream()
            .anyMatch(p -> ProjectAnalyticsController.repositoryKey(p.repositoryUrl()).equalsIgnoreCase(repo));
        return registered ? Optional.empty() : fallback.projectIdFor(repo);
      }

      @Override public Optional<String> repositoryUrlFor(String repo) {
        var match = find(repo);
        return match == null ? Optional.empty() : Optional.of(match.repositoryUrl());
      }
    };
  }

  private record ProjectMatch(String projectId, String repositoryUrl) {}
}
