package akegp.app;

import akegp.analytics.ingestion.ModelsDemoFixture;
import akegp.analytics.service.ProjectReadAuthorizer;
import akegp.rules.repository.ProjectRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/** Explicit local composition while the platform has no authenticated membership service. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "akegp.analytics.development-access", havingValue = "true")
public class AnalyticsDevelopmentAccessConfig {
  @Bean @Primary
  ProjectReadAuthorizer developmentAnalyticsAccess(ProjectRepository projects, Environment environment) {
    return (principal, project) -> {
      if (ModelsDemoFixture.PROJECT_ID.equals(project)) {
        if (environment.getProperty("akegp.analytics.demo-enabled", Boolean.class, false)) return;
      } else if (projects.findById(project).isPresent()) {
        return;
      }
      throw new SecurityException("Analytics project access denied");
    };
  }
}
