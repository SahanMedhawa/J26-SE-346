package akegp.analytics.config;

import akegp.analytics.ingestion.ModelsDemoFixture;
import akegp.analytics.repository.JdbcAnalyticsReadRepository;
import akegp.analytics.service.AnalyticsQueryService;
import akegp.analytics.service.ProjectReadAuthorizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class AnalyticsQueryConfig {
  @Bean(destroyMethod = "close") AnalyticsDatabase analyticsDatabase(Environment environment) {
    return new AnalyticsDatabase(environment);
  }
  @Bean @ConditionalOnMissingBean(ProjectReadAuthorizer.class)
  ProjectReadAuthorizer analyticsAuthorization(Environment environment) {
    return (principal, project) -> {
      if (!environment.getProperty("akegp.analytics.demo-enabled", Boolean.class, false)
          || !ModelsDemoFixture.PROJECT_ID.equals(project)) {
        throw new SecurityException("Analytics project access requires an authenticated membership adapter");
      }
    };
  }
  @Bean AnalyticsQueryService analyticsQueries(AnalyticsDatabase database, ProjectReadAuthorizer authorizer) {
    return new AnalyticsQueryService(project -> new JdbcAnalyticsReadRepository(database.dataSource()).readProject(project)
        .stream().filter(r -> !ModelsDemoFixture.PROJECT_ID.equals(project)
            || (r.provenance().equals("DEMO") && r.dataset().equals(ModelsDemoFixture.SOURCE.dataset()))).toList(), authorizer);
  }
}
