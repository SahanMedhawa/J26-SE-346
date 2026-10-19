package akegp.enforcement.config;

import akegp.enforcement.conformance.ConformanceScanner;
import akegp.enforcement.conformance.MockConformanceScanner;
import akegp.enforcement.conformance.SharedKernelConformanceScanner;
import akegp.enforcement.github.GithubStatusClient;
import akegp.enforcement.github.HttpGithubStatusClient;
import akegp.enforcement.github.LoggingGithubStatusClient;
import akegp.enforcement.policy.ConfiguredProjectLinkResolver;
import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.repository.InMemoryEnforcementRepository;
import akegp.enforcement.repository.JdbcEnforcementRepository;
import akegp.enforcement.github.HttpPullRequestSource;
import akegp.enforcement.github.NoPullRequestSource;
import akegp.enforcement.github.PullRequestSource;
import akegp.enforcement.review.FixVerificationService;
import akegp.enforcement.review.ReviewService;
import akegp.enforcement.service.ChangeClassifier;
import akegp.enforcement.service.DashboardStatsService;
import akegp.enforcement.service.EnforcementService;
import akegp.enforcement.service.RunStatusPublisher;
import akegp.shared.api.ConformanceScanService;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the enforcement module. Everything here is replaceable through the settings in EnforcementProperties. */
@Configuration
@EnableConfigurationProperties(EnforcementProperties.class)
public class EnforcementModuleConfig {

  /**
   * Uses the application's shared PostgreSQL connection (spring.datasource). The tables
   * {prefix}pipeline_run, {prefix}run_change, {prefix}run_violation, {prefix}audit_log are created by
   * enforcementSchemaInitializer below, once every other module has started.
   * The prefix is akegp.schemas.enforcement (enf_), so only enf_ tables are ever touched.
   */
  @Bean
  public EnforcementRepository enforcementRepository(
      EnforcementProperties props, ObjectProvider<DataSource> dataSource,
      @Value("${akegp.schemas.enforcement:enf_}") String tablePrefix) {
    if (!"jdbc".equalsIgnoreCase(props.getPersistence())) {
      return new InMemoryEnforcementRepository();
    }
    DataSource ds = dataSource.getIfAvailable();
    if (ds == null) {
      throw new IllegalStateException("akegp.enforcement.persistence=jdbc but the application has no DataSource. "
          + "Check spring.datasource.* and that spring-boot-starter-jdbc and the PostgreSQL driver are on the classpath.");
    }
    return new JdbcEnforcementRepository(ds, tablePrefix);
  }

  /**
   * Creates the enf_ tables only after ALL beans exist. Other modules (for example the Rules module's Flyway) run their
   * own migrations while the application starts, and Flyway refuses to start on a database that already contains tables
   * but has no migration history. Creating our tables last keeps the modules independent of each other.
   */
  @Bean
  public SmartInitializingSingleton enforcementSchemaInitializer(EnforcementRepository repository) {
    return () -> {
      if (repository instanceof JdbcEnforcementRepository jdbc) jdbc.init();
    };
  }

  @Bean
  public ProjectLinkResolver projectLinkResolver(EnforcementProperties props) {
    return new ConfiguredProjectLinkResolver(props.getProjects(), props.getDefaultProjectId());
  }

  @Bean
  public GithubStatusClient githubStatusClient(EnforcementProperties props) {
    var g = props.getGithub();
    if (g.getToken() == null || g.getToken().isBlank()) return new LoggingGithubStatusClient();
    return new HttpGithubStatusClient(g.getApiUrl(), g.getToken(), g.getStatusContext());
  }

  @Bean
  public ConformanceScanner conformanceScanner(EnforcementProperties props, ObjectProvider<ConformanceScanService> real) {
    if (!"real".equalsIgnoreCase(props.getConformance())) return new MockConformanceScanner();
    ConformanceScanService service = real.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("akegp.enforcement.conformance=real but no ConformanceScanService bean exists. "
          + "The conformance module must implement akegp.shared.api.ConformanceScanService.");
    }
    return new SharedKernelConformanceScanner(service);
  }

  @Bean public ChangeClassifier changeClassifier() { return new ChangeClassifier(); }

  @Bean
  public RunStatusPublisher runStatusPublisher(GithubStatusClient github, EnforcementRepository repository) {
    return new RunStatusPublisher(github, repository);
  }

  @Bean
  public EnforcementService enforcementService(EnforcementRepository repository, ChangeClassifier classifier,
      ProjectLinkResolver links, ConformanceScanner scanner, RunStatusPublisher status) {
    return new EnforcementService(repository, classifier, links, scanner, status);
  }

  @Bean
  public PullRequestSource pullRequestSource(EnforcementProperties props) {
    var g = props.getGithub();
    if (g.getToken() == null || g.getToken().isBlank()) return new NoPullRequestSource();
    return new HttpPullRequestSource(g.getApiUrl(), g.getToken());
  }

  @Bean
  public FixVerificationService fixVerificationService(EnforcementRepository repository, PullRequestSource source,
                                                       EnforcementService enforcement) {
    return new FixVerificationService(repository, source, enforcement);
  }

  @Bean
  public ReviewService reviewService(EnforcementRepository repository, ConformanceScanner scanner, RunStatusPublisher status) {
    return new ReviewService(repository, scanner, status);
  }

  @Bean
  public DashboardStatsService dashboardStatsService(EnforcementRepository repository) {
    return new DashboardStatsService(repository);
  }
}