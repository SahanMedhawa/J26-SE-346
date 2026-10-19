package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import akegp.rules.validation.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit test database only, using a random isolated schema. No live GitHub requests. */
@EnabledIfEnvironmentVariable(named = "AKEGP_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class RuleExportPostgresTest {
  private final String schema = "rules_export_" + UUID.randomUUID().toString().replace("-", "");
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext context;
  private ValidatorFactory validators;
  private RuleExportService exports;
  private RuleService rules;
  private ADRService adrs;
  private ArchitectReviewService reviews;
  private UUID project;
  private UUID rule;
  private UUID adr;

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {}

  @BeforeEach
  void setUp() {
    var dataSource = new DriverManagerDataSource(System.getenv("AKEGP_TEST_POSTGRES_URL"),
        System.getenv("AKEGP_TEST_POSTGRES_USER"), System.getenv("AKEGP_TEST_POSTGRES_PASSWORD"));
    var properties = new Properties();
    properties.setProperty("currentSchema", schema);
    dataSource.setConnectionProperties(properties);
    jdbc = new JdbcTemplate(dataSource);
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration/rules").load().migrate();
    validators = Validation.buildDefaultValidatorFactory();
    context = new AnnotationConfigApplicationContext();
    context.register(Transactions.class);
    context.registerBean("rulesJdbcTemplate", JdbcTemplate.class, () -> jdbc);
    context.registerBean("rulesTransactionManager", JdbcTransactionManager.class, () -> new JdbcTransactionManager(dataSource));
    context.registerBean(Validator.class, () -> validators.getValidator());
    context.register(JdbcProjectRepository.class, JdbcADRRepository.class, JdbcRuleRepository.class,
        JdbcReadableIdRepository.class, ProjectRegistrationServiceImpl.class, ADRServiceImpl.class, RuleServiceImpl.class,
        JdbcImplementationMappingRepository.class, ImplementationMappingServiceImpl.class, ArvmCatalogue.class,
        RuleStructureValidationService.class, JdbcRuleAssuranceRepository.class, RuleEvidenceService.class,
        StoredStructuralValidationService.class, ArchitectReviewService.class, RuleExportService.class);
    context.refresh();
    project = context.getBean(ProjectRegistrationService.class).register(new ProjectRegistrationRequest(
        "Integration Export", null, "https://github.com/example/" + UUID.randomUUID() + ".git",
        Set.of(ArchitectureStyle.MICROSERVICES))).id();
    adrs = context.getBean(ADRService.class);
    adr = adrs.create(project, new CreateADRRequest(null, "Accepted decision", ADRStatus.ACCEPTED,
        "Decision context", "Documented decision", "Documented consequences")).id();
    rules = context.getBean(RuleService.class);
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
    context.getBean(StoredStructuralValidationService.class).validate(project, rule, null);
    context.getBean(ImplementationMappingService.class).replace(project, rule,
        new ImplementationMappingsRequest(List.of("src/Z.java", "src/A.java"), List.of("db/Z.sql", "db/A.sql")));
    reviews = context.getBean(ArchitectReviewService.class);
    confirm();
    exports = context.getBean(RuleExportService.class);
  }

  @AfterEach
  void cleanUp() {
    if (context != null) context.close();
    if (validators != null) validators.close();
    // Generated UUID identifier; only this test's schema is removed.
    if (jdbc != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }

  private void confirm() {
    reviews.submit(project, rule, new ArchitectReviewRequest(ArchitectReviewDecision.CONFIRMED,
        "Architect", "Review for export", reviews.find(project, rule).evidenceFingerprint()));
  }

  private TransactionTemplate readOnlySnapshot() {
    var template = new TransactionTemplate(context.getBean(JdbcTransactionManager.class));
    template.setReadOnly(true);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return template;
  }

  @Test
  void exportsDatabaseValuesInAReadOnlyTransactionAndPreservesExistingMappingQueryOrder() {
    var beforeValidation = context.getBean(RuleAssuranceRepository.class).validation(rule);
    var beforeHistory = context.getBean(RuleAssuranceRepository.class).reviews(rule);
    var beforeMappings = context.getBean(ImplementationMappingRepository.class).findByRule(rule);
    var exported = readOnlySnapshot().execute(status -> {
      assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
      return exports.export(project); // FOR UPDATE or accidental writes would fail on PostgreSQL.
    });
    assertNotNull(exported);
    assertEquals(1, exported.ruleCount());
    assertEquals(rules.findById(project, rule).ruleId(), exported.rules().getFirst().ruleId());
    assertEquals(adrs.findById(project, adr).adrId(), exported.rules().getFirst().adrId());
    assertEquals(beforeMappings.stream().filter(m -> m.side() == ImplementationMappingSide.SOURCE)
        .map(RuleImplementationMapping::filePath).toList(), exported.rules().getFirst().sourceFiles());
    assertEquals(beforeMappings.stream().filter(m -> m.side() == ImplementationMappingSide.TARGET)
        .map(RuleImplementationMapping::filePath).toList(), exported.rules().getFirst().targetFiles());
    assertEquals(beforeValidation, context.getBean(RuleAssuranceRepository.class).validation(rule));
    assertEquals(beforeHistory, context.getBean(RuleAssuranceRepository.class).reviews(rule));
    assertEquals(beforeMappings, context.getBean(ImplementationMappingRepository.class).findByRule(rule));
  }

  @Test
  @Timeout(30)
  void concurrentEditCannotMixOldConfirmationWithNewRuleInOneExportSnapshot() {
    var original = rules.findById(project, rule);
    readOnlySnapshot().executeWithoutResult(status -> {
      // Establish a PostgreSQL snapshot before the concurrent commit.
      jdbc.queryForObject("SELECT description FROM architecture_rules WHERE id = ?", String.class, rule);
      try (var executor = Executors.newSingleThreadExecutor()) {
        executor.submit(() -> rules.update(project, rule, new UpdateRuleRequest(null, adr,
            original.sourceElement(), original.sourceElementType(), original.relationshipType(),
            original.targetElement(), original.targetElementType(), original.decision(),
            "Interpretation changed concurrently", original.severity()))).get(10, TimeUnit.SECONDS);
      } catch (Exception exception) { throw new AssertionError(exception); }
      assertEquals(1, exports.export(project).ruleCount()); // Coherent evidence as of this snapshot.
    });
    assertEquals(0, exports.export(project).ruleCount()); // A fresh request sees the edit and stale confirmation.
  }

  @Test
  void exportsCurrentConfirmationLinkedToProposedAdrWithoutMutatingItsStatusOrReview() {
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), ADRStatus.PROPOSED,
        a.context(), a.decision(), a.consequences(), a.version()));
    confirm();
    var beforeAdr = adrs.findById(project, adr);
    var beforeReviews = context.getBean(RuleAssuranceRepository.class).reviews(rule);
    assertEquals(1, exports.export(project).ruleCount());
    assertEquals(beforeAdr, adrs.findById(project, adr));
    assertEquals(beforeReviews, context.getBean(RuleAssuranceRepository.class).reviews(rule));
  }

  @Test
  void freshExportExcludesChangedRulesSupersededAdrsAndDeletedRules() {
    assertEquals(1, exports.export(project).ruleCount());
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, adr, r.sourceElement(), r.sourceElementType(), r.relationshipType(),
        r.targetElement(), r.targetElementType(), RuleDecision.FORBIDDEN, r.description(), r.severity()));
    assertEquals(0, exports.export(project).ruleCount());
    confirm();
    assertEquals(1, exports.export(project).ruleCount());
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), ADRStatus.SUPERSEDED,
        a.context(), a.decision(), a.consequences(), a.version()));
    confirm(); // Still excluded even when superseded ADR evidence was subsequently reviewed.
    assertEquals(0, exports.export(project).ruleCount());
    rules.delete(project, rule);
    assertEquals(0, exports.export(project).ruleCount());
  }
}
