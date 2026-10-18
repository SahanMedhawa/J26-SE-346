package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;

import akegp.rules.api.*;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Uses only an isolated, randomly named schema in the explicitly configured test database. */
@EnabledIfEnvironmentVariable(named = "AKEGP_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class ImplementationMappingPostgresTest {
  private final String schema = "rules_maps_" + UUID.randomUUID().toString().replace("-", "");
  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext context;
  private ImplementationMappingService mappings;
  private RuleService rules;
  private UUID project;
  private UUID rule;

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {}

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource(System.getenv("AKEGP_TEST_POSTGRES_URL"),
        System.getenv("AKEGP_TEST_POSTGRES_USER"), System.getenv("AKEGP_TEST_POSTGRES_PASSWORD"));
    Properties properties = new Properties();
    properties.setProperty("currentSchema", schema);
    dataSource.setConnectionProperties(properties);
    jdbc = new JdbcTemplate(dataSource);
    migrate("4");
    context = new AnnotationConfigApplicationContext();
    context.register(Transactions.class);
    context.registerBean("rulesJdbcTemplate", JdbcTemplate.class, () -> jdbc);
    context.registerBean("rulesTransactionManager", JdbcTransactionManager.class,
        () -> new JdbcTransactionManager(dataSource));
    context.register(JdbcProjectRepository.class, JdbcADRRepository.class, JdbcRuleRepository.class,
        JdbcReadableIdRepository.class, ProjectRegistrationServiceImpl.class, ADRServiceImpl.class,
        RuleServiceImpl.class, JdbcImplementationMappingRepository.class, ImplementationMappingServiceImpl.class);
    context.refresh();
    project = context.getBean(ProjectRegistrationService.class).register(new ProjectRegistrationRequest(
        "Mapping project", null, "https://example.com/" + UUID.randomUUID(),
        Set.of(ArchitectureStyle.LAYERED))).id();
    var adr = context.getBean(ADRService.class).create(project, ReadableIdServiceTest.adrRequest());
    rules = context.getBean(RuleService.class);
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(adr.id())).id();
    var original = rules.findById(project, rule);
    migrate("latest");
    assertEquals(original, rules.findById(project, rule));
    mappings = context.getBean(ImplementationMappingService.class);
  }

  private void migrate(String target) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration/rules").target(target).load().migrate();
  }

  @AfterEach
  void cleanUp() {
    if (context != null) context.close();
    // Generated UUID identifier, never user input; cleanup is confined to this test's schema.
    if (jdbc != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }

  private ImplementationMappingsRequest request(String source) {
    return new ImplementationMappingsRequest(List.of(source), List.of("db/V1.sql"));
  }

  @Test
  void persistsReplacesIdempotentlyAndCascadesOnRuleDeletion() {
    var originalRule = rules.findById(project, rule);
    var saved = mappings.replace(project, rule, request("src/A.java"));
    var repository = context.getBean(ImplementationMappingRepository.class);
    var rows = repository.findByRule(rule);
    assertEquals(saved, mappings.findByRule(project, rule));
    mappings.replace(project, rule, request("src/A.java"));
    assertEquals(rows, repository.findByRule(rule));
    assertEquals(List.of("src/B.java"), mappings.replace(project, rule, request("src/B.java")).sourceFiles());
    assertEquals(originalRule, rules.findById(project, rule));
    rules.delete(project, rule);
    assertTrue(repository.findByRule(rule).isEmpty());
  }

  @Test
  void failedInsertRollsBackDeletedMappingsAndPriorInserts() {
    mappings.replace(project, rule, request("src/Original.java"));
    var before = mappings.findByRule(project, rule);
    jdbc.execute("ALTER TABLE rule_implementation_mappings ADD CONSTRAINT test_failure "
        + "CHECK (file_path <> 'src/Fail.java')");
    assertThrows(DataIntegrityViolationException.class, () -> mappings.replace(project, rule,
        new ImplementationMappingsRequest(List.of("src/Added.java", "src/Fail.java"), List.of())));
    assertEquals(before, mappings.findByRule(project, rule));
  }

  @Test
  void databaseEnforcesSidePathForeignKeyAndUniquenessConstraints() {
    mappings.replace(project, rule, request("src/A.java"));
    assertThrows(DataIntegrityViolationException.class, () -> insert(rule, "SOURCE", "src/A.java"));
    assertThrows(DataIntegrityViolationException.class, () -> insert(rule, "INVALID", "src/B.java"));
    assertThrows(DataIntegrityViolationException.class, () -> insert(rule, "SOURCE", null));
    assertThrows(DataIntegrityViolationException.class, () -> insert(rule, "SOURCE", "  "));
    assertThrows(DataIntegrityViolationException.class, () -> insert(UUID.randomUUID(), "SOURCE", "src/B.java"));
  }

  private void insert(UUID ruleId, String side, String path) {
    jdbc.update("INSERT INTO rule_implementation_mappings (id, rule_id, side, file_path) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(), ruleId, side, path);
  }

  @Test
  @Timeout(45)
  void concurrentReplacementsAreCompleteAndNeverMergeOrDuplicate() throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return mappings.replace(project, rule, request("src/A.java"));
      });
      var second = executor.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return mappings.replace(project, rule, request("src/B.java"));
      });
      start.countDown();
      var a = first.get(30, TimeUnit.SECONDS);
      var b = second.get(30, TimeUnit.SECONDS);
      assertTrue(List.of(a, b).contains(mappings.findByRule(project, rule)));
    }
    assertEquals(2, context.getBean(ImplementationMappingRepository.class).findByRule(rule).size());
  }
}
