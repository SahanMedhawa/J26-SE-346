package akegp.app;

import akegp.analytics.config.AnalyticsQueryConfig;
import akegp.analytics.ingestion.ModelsDemoFixture;
import akegp.analytics.service.ProjectReadAuthorizer;
import akegp.rules.domain.RegisteredProject;
import akegp.rules.repository.ProjectRepository;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.domain.ProjectStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsDevelopmentAccessConfigTest {
  @Test void developmentCompositionAllowsRegisteredProjectsAndExplicitDemoOnly() {
    UUID registered = UUID.randomUUID();
    var repository = new InMemoryProjectRepository();
    repository.save(new RegisteredProject(registered, "Analytics test", "", "https://example.com/test",
        Set.of(), Instant.EPOCH, ProjectStatus.values()[0]));
    new ApplicationContextRunner().withUserConfiguration(AnalyticsQueryConfig.class, AnalyticsDevelopmentAccessConfig.class)
        .withBean(ProjectRepository.class, () -> repository)
        .withPropertyValues("akegp.analytics.development-access=true", "akegp.analytics.demo-enabled=true")
        .run(context -> {
          assertNull(context.getStartupFailure());
          var access = context.getBean(ProjectReadAuthorizer.class);
          assertDoesNotThrow(() -> access.requireRead(null, registered));
          assertDoesNotThrow(() -> access.requireRead(null, ModelsDemoFixture.PROJECT_ID));
          assertThrows(SecurityException.class, () -> access.requireRead(null, UUID.randomUUID()));
        });
  }

  @Test void defaultCompositionStillDeniesAnonymousProjectAccess() {
    new ApplicationContextRunner().withUserConfiguration(AnalyticsQueryConfig.class, AnalyticsDevelopmentAccessConfig.class)
        .run(context -> assertThrows(SecurityException.class,
            () -> context.getBean(ProjectReadAuthorizer.class).requireRead(null, UUID.randomUUID())));
  }
}
