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
    return repo -> {
      var matches = projects.findAll().stream().filter(p -> ProjectAnalyticsController.repositoryKey(p.repositoryUrl()).equalsIgnoreCase(repo)).toList();
      if (matches.size() == 1) return matches.getFirst().status() == ProjectStatus.ACTIVE ? Optional.of(matches.getFirst().id().toString()) : Optional.empty();
      if (matches.size() > 1) return Optional.empty();
      return fallback.projectIdFor(repo);
    };
  }
}
