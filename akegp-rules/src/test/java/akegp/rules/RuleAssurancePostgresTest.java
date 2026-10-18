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
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL only. Each test owns a random schema; no existing schema is migrated. */
@EnabledIfEnvironmentVariable(named = "AKEGP_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class RuleAssurancePostgresTest {
  private final String schema = "rules_review_" + UUID.randomUUID().toString().replace("-", "");
  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext context;
  private ValidatorFactory validators;
  private ProjectRegistrationService projects;
  private ADRService adrs;
  private RuleService rules;
  private ImplementationMappingService mappings;
  private StoredStructuralValidationService validations;
  private ArchitectReviewService reviews;
  private RuleAssuranceRepository assurance;
  private UUID project;
  private UUID adr;
  private UUID rule;

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {}

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource(System.getenv("AKEGP_TEST_POSTGRES_URL"),
        System.getenv("AKEGP_TEST_POSTGRES_USER"), System.getenv("AKEGP_TEST_POSTGRES_PASSWORD"));
    var properties = new Properties();
    properties.setProperty("currentSchema", schema);
    dataSource.setConnectionProperties(properties);
    jdbc = new JdbcTemplate(dataSource);
    migrate("5");
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
        StoredStructuralValidationService.class, ArchitectReviewService.class);
    context.refresh();
    projects = context.getBean(ProjectRegistrationService.class);
    adrs = context.getBean(ADRService.class);
    rules = context.getBean(RuleService.class);
    mappings = context.getBean(ImplementationMappingService.class);
    project = projects.register(new ProjectRegistrationRequest("HealthMate", null,
        "https://github.com/example/" + UUID.randomUUID(), Set.of(ArchitectureStyle.MICROSERVICES))).id();
    adr = adrs.create(project, ReadableIdServiceTest.adrRequest()).id();
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
    var original = rules.findById(project, rule);
    migrate("latest");
    assertEquals(original, rules.findById(project, rule));
    validations = context.getBean(StoredStructuralValidationService.class);
    reviews = context.getBean(ArchitectReviewService.class);
    assurance = context.getBean(RuleAssuranceRepository.class);
  }

  private Flyway flyway(String target) {
    return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration/rules").target(target).load();
  }
  private void migrate(String target) { flyway(target).migrate(); }

  @AfterEach
  void cleanUp() {
    if (context != null) context.close();
    if (validators != null) validators.close();
    // UUID-generated identifier; cleanup is confined to the schema created by this test.
    if (jdbc != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }

  private TransactionTemplate transaction() { return new TransactionTemplate(context.getBean(JdbcTransactionManager.class)); }
  private ArchitectReviewRequest confirm() {
    return new ArchitectReviewRequest(ArchitectReviewDecision.CONFIRMED, "Architect", "Evidence reviewed",
        reviews.find(project, rule).evidenceFingerprint());
  }
  private void readyForReview() {
    validations.validate(project, rule, null);
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Orders.java"), List.of("src/Payments.java")));
  }
  private void updateRule(RelationshipType relationship, String description) {
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, r.adrId(), r.sourceElement(), r.sourceElementType(),
        relationship, r.targetElement(), r.targetElementType(), r.decision(), description, r.severity()));
  }
  private void updateAdr() {
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), a.status(), a.context(),
        a.decision() + " Revised.", a.consequences(), a.version()));
  }

  @Test
  void v6PreservesExistingRecordsAndStartsWithoutSuccessfulEvidence() {
    flyway("latest").validate();
    assertEquals("R-001", rules.findById(project, rule).ruleId());
    assertEquals(StructuralValidationStatus.NOT_VALIDATED, validations.find(project, rule).status());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rule_structural_validations", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM architect_reviews", Integer.class));
  }

  @Test
  void supportedAndUnsupportedResultsAreUpsertedAndRevalidationPreservesRecordIdentity() {
    readyForReview();
    UUID id = assurance.validation(rule).orElseThrow().id();
    assertEquals(StructuralValidationStatus.SUPPORTED, validations.find(project, rule).status());
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, adr, r.sourceElement(), ArchitectureElementType.DATABASE,
        r.relationshipType(), r.targetElement(), r.targetElementType(), RuleDecision.FORBIDDEN, r.description(), r.severity()));
    assertEquals(StructuralValidationStatus.NOT_VALIDATED, validations.find(project, rule).status());
    validations.validate(project, rule, null);
    assertEquals(StructuralValidationStatus.UNSUPPORTED, validations.find(project, rule).status());
    assertEquals(id, assurance.validation(rule).orElseThrow().id());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rule_structural_validations", Integer.class));
    assertThrows(ArchitectReviewException.class, () -> reviews.submit(project, rule, confirm()));
  }

  @Test
  void structuralChangeThenRevertStillRequiresNewValidationAndReview() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var r = rules.findById(project, rule);
    updateRule(RelationshipType.DEPENDS_ON, r.description());
    updateRule(RelationshipType.CALLS, r.description());
    assertEquals(2, assurance.revisions(rule).structural());
    assertFalse(validations.find(project, rule).current());
    validations.validate(project, rule, null);
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.submit(project, rule, confirm()).reviewStatus());
    assertEquals(2, reviews.history(project, rule).size());
    assertFalse(reviews.history(project, rule).getLast().current());
  }

  @Test
  void noOpUpdatesAndRevalidationPreserveRevisionAndConfirmation() {
    readyForReview();
    var before = reviews.submit(project, rule, confirm());
    var revision = assurance.revisions(rule);
    var r = rules.findById(project, rule);
    updateRule(r.relationshipType(), r.description());
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), a.status(), a.context(), a.decision(), a.consequences(), a.version()));
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Orders.java"), List.of("src/Payments.java")));
    validations.validate(project, rule, null);
    assertEquals(revision, assurance.revisions(rule));
    assertEquals(before.evidenceFingerprint(), reviews.find(project, rule).evidenceFingerprint());
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.find(project, rule).reviewStatus());
  }

  @Test
  void mappingChangeThenRevertDoesNotReviveConfirmationOrInvalidateStructure() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var old = assurance.revisions(rule);
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/New.java"), List.of("src/Payments.java")));
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Orders.java"), List.of("src/Payments.java")));
    assertTrue(validations.find(project, rule).current());
    assertTrue(assurance.revisions(rule).review() > old.review());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
  }

  @Test
  void adrUpdateAndRelinkingInvalidateReviewAndPreserveHistory() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    updateAdr();
    assertTrue(validations.find(project, rule).current());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    reviews.submit(project, rule, confirm());
    var r = rules.findById(project, rule);
    UUID another = adrs.create(project, ReadableIdServiceTest.adrRequest()).id();
    rules.update(project, rule, new UpdateRuleRequest(null, another, r.sourceElement(), r.sourceElementType(),
        r.relationshipType(), r.targetElement(), r.targetElementType(), r.decision(), r.description(), r.severity()));
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertEquals(2, reviews.history(project, rule).size());
  }

  @Test
  void projectMetadataDoesNotInvalidateStructureButStyleChangesDo() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var p = projects.findById(project);
    projects.update(project, new ProjectUpdateRequest(p.name(), "Updated project notes", p.repositoryUrl(), p.architectureStyles()));
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.find(project, rule).reviewStatus());
    projects.update(project, new ProjectUpdateRequest(p.name(), p.description(), p.repositoryUrl(), Set.of(ArchitectureStyle.LAYERED)));
    projects.update(project, new ProjectUpdateRequest(p.name(), p.description(), p.repositoryUrl(), Set.of(ArchitectureStyle.MICROSERVICES)));
    assertFalse(validations.find(project, rule).current());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
  }

  @Test
  void ruleMetadataInvalidatesReviewOnlyAndOtherRulesRemainIsolated() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    UUID other = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, other).reviewStatus());
    var old = assurance.revisions(rule);
    mappings.replace(project, other, new ImplementationMappingsRequest(List.of("other.java"), List.of("target.java")));
    assertEquals(old, assurance.revisions(rule));
    var r = rules.findById(project, rule);
    updateRule(r.relationshipType(), "Changed interpretation of this rule");
    assertTrue(validations.find(project, rule).current());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
  }

  @Test
  void historyIsAppendOnlyAndSurvivesRuleDeletionWhileCurrentEvidenceCascades() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    reviews.submit(project, rule, new ArchitectReviewRequest(ArchitectReviewDecision.CHANGES_REQUESTED,
        "Second Architect", "Please clarify the implementation interpretation.", null));
    assertEquals(ArchitectReviewDecision.CHANGES_REQUESTED, reviews.history(project, rule).getFirst().review().decision());
    rules.delete(project, rule);
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM architect_reviews WHERE rule_uuid = ? AND rule_id IS NULL", Integer.class, rule));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rule_structural_validations WHERE rule_id = ?", Integer.class, rule));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM rule_evidence_revisions WHERE rule_id = ?", Integer.class, rule));
    assertThrows(RuleNotFoundException.class, () -> reviews.find(project, rule));
  }

  @Test
  void transactionRollbackLeavesNeitherValidationNorReview() {
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("source.java"), List.of("target.java")));
    assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(status -> {
      validations.validate(project, rule, null);
      reviews.submit(project, rule, confirm());
      throw new IllegalStateException("Force rollback");
    }));
    assertTrue(assurance.validation(rule).isEmpty());
    assertTrue(assurance.reviews(rule).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"mapping", "adr", "structure", "project"})
  @Timeout(40)
  void concurrentEvidenceEditsBlockConfirmationAndRejectStaleWorkspace(String edit) throws Exception {
    readyForReview();
    String reviewed = reviews.find(project, rule).evidenceFingerprint();
    var editorHoldingLocks = new CountDownLatch(1);
    var allowEditorCommit = new CountDownLatch(1);
    var confirmationStarted = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var editor = executor.submit(() -> transaction().executeWithoutResult(status -> {
        switch (edit) {
          case "mapping" -> mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("changed.java"), List.of("target.java")));
          case "adr" -> updateAdr();
          case "structure" -> updateRule(RelationshipType.DEPENDS_ON, "Changed structural relationship");
          case "project" -> {
            var p = projects.findById(project);
            projects.update(project, new ProjectUpdateRequest(p.name(), p.description(), p.repositoryUrl(), Set.of(ArchitectureStyle.LAYERED)));
          }
          default -> throw new AssertionError(edit);
        }
        editorHoldingLocks.countDown();
        try { assertTrue(allowEditorCommit.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
      }));
      assertTrue(editorHoldingLocks.await(10, TimeUnit.SECONDS));
      var confirmation = executor.submit(() -> {
        confirmationStarted.countDown();
        return reviews.submit(project, rule, new ArchitectReviewRequest(ArchitectReviewDecision.CONFIRMED,
            "Architect", "Reviewed the previously loaded evidence", reviewed));
      });
      try {
        assertTrue(confirmationStarted.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> confirmation.get(200, TimeUnit.MILLISECONDS));
      } finally { allowEditorCommit.countDown(); }
      editor.get(15, TimeUnit.SECONDS);
      var rejected = assertThrows(ExecutionException.class, () -> confirmation.get(15, TimeUnit.SECONDS));
      assertInstanceOf(ArchitectReviewException.class, rejected.getCause());
    } finally { allowEditorCommit.countDown(); }
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertTrue(reviews.history(project, rule).isEmpty());
  }
}
