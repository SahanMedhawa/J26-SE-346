package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL only. Each test owns a random schema; no existing schema is migrated. */
@EnabledIfEnvironmentVariable(named = "AKEGP_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
class ReadableIdPostgresTest {
  private final String schema = "rules_ids_" + UUID.randomUUID().toString().replace("-", "");
  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext context;
  private ProjectRegistrationService projects;
  private ADRService adrs;
  private RuleService rules;

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
    migrate("3");
    context = new AnnotationConfigApplicationContext();
    context.register(Transactions.class);
    context.registerBean("rulesJdbcTemplate", JdbcTemplate.class, () -> jdbc);
    context.registerBean("rulesTransactionManager", JdbcTransactionManager.class,
        () -> new JdbcTransactionManager(dataSource));
    context.register(JdbcProjectRepository.class, JdbcADRRepository.class, JdbcRuleRepository.class,
        JdbcReadableIdRepository.class, ProjectRegistrationServiceImpl.class, ADRServiceImpl.class,
        RuleServiceImpl.class);
    context.refresh();
    projects = context.getBean(ProjectRegistrationService.class);
    adrs = context.getBean(ADRService.class);
    rules = context.getBean(RuleService.class);
  }

  private void migrate(String target) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration/rules").target(target).load().migrate();
  }

  @AfterEach
  void cleanUp() {
    if (context != null) context.close();
    // Identifier is generated locally from a UUID and is never supplied by a client.
    if (jdbc != null) jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
  }

  private UUID project() {
    return projects.register(new ProjectRegistrationRequest("Project", null,
        "https://example.com/" + UUID.randomUUID(), Set.of(ArchitectureStyle.MICROSERVICES))).id();
  }

  private ArchitectureDecisionRecord legacyAdr(UUID project, String displayId) {
    var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return context.getBean(ADRRepository.class).insert(new ArchitectureDecisionRecord(UUID.randomUUID(),
        displayId, project, "Legacy decision", ADRStatus.ACCEPTED, "Legacy context",
        "Legacy decision details", "Legacy consequences", 3, now, now));
  }

  private ArchitectureRule legacyRule(UUID project, UUID adr, String displayId) {
    var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return context.getBean(RuleRepository.class).insert(new ArchitectureRule(UUID.randomUUID(),
        displayId, project, adr, "Orders", ArchitectureElementType.SERVICE, RelationshipType.CALLS,
        "Payments", ArchitectureElementType.SERVICE, RuleDecision.ALLOWED, "Legacy description",
        RuleSeverity.HIGH, now, now));
  }

  @Test
  void migrationPreservesRecordsSeedsMaximaAndCountersSurviveDeletionAndNewAllocator() {
    UUID project = project();
    legacyAdr(project, "ADR-001");
    var oldAdr = legacyAdr(project, "ADR-007");
    legacyRule(project, oldAdr.id(), "R-001");
    var oldRule = legacyRule(project, oldAdr.id(), "R-004");
    migrate("latest");
    assertEquals(oldAdr, adrs.findById(project, oldAdr.id()));
    assertEquals(oldRule.id(), rules.findById(project, oldRule.id()).id());
    assertEquals("R-004", rules.findById(project, oldRule.id()).ruleId());
    var nextAdr = adrs.create(project, ReadableIdServiceTest.adrRequest());
    assertEquals("ADR-008", nextAdr.adrId());
    var nextRule = rules.create(project, ReadableIdServiceTest.ruleRequest(oldAdr.id()));
    assertEquals("R-005", nextRule.ruleId());
    adrs.delete(project, nextAdr.id());
    rules.delete(project, nextRule.id());
    // A newly constructed allocator reads persisted counters, not process-local state.
    var allocator = new JdbcReadableIdRepository(jdbc);
    new TransactionTemplate(new JdbcTransactionManager(dataSource)).executeWithoutResult(status -> {
      assertEquals("ADR-009", allocator.nextId(project, ReadableIdRepository.RecordType.ADR));
      assertEquals("R-006", allocator.nextId(project, ReadableIdRepository.RecordType.RULE));
    });
  }

  @Test
  void migrationSupportsLargeSuffixesLeadingZeroAliasesAndIndependentProjects() {
    UUID project = project();
    var legacy = legacyAdr(project, "ADR-000999");
    legacyAdr(project, "ADR-999");
    legacyRule(project, legacy.id(), "R-999");
    UUID other = project();
    migrate("latest");
    assertEquals("ADR-1000", adrs.create(project, ReadableIdServiceTest.adrRequest()).adrId());
    assertEquals("R-1000", rules.create(project, ReadableIdServiceTest.ruleRequest(legacy.id())).ruleId());
    var otherAdr = adrs.create(other, ReadableIdServiceTest.adrRequest());
    assertEquals("ADR-001", otherAdr.adrId());
    assertEquals("R-001", rules.create(other, ReadableIdServiceTest.ruleRequest(otherAdr.id())).ruleId());
  }

  @Test
  void malformedLegacyIdsAbortMigrationWithoutChangingRecords() {
    UUID project = project();
    var adr = legacyAdr(project, "legacy-decision");
    var rule = legacyRule(project, adr.id(), "legacy-rule");
    assertThrows(org.flywaydb.core.api.FlywayException.class, () -> migrate("latest"));
    assertEquals(adr, adrs.findById(project, adr.id()));
    assertEquals(rule.ruleId(), rules.findById(project, rule.id()).ruleId());
    assertNull(jdbc.queryForObject("SELECT to_regclass('project_record_counters')", String.class));
  }

  @Test
  void duplicateLegacyIdsInDamagedSchemaAbortMigration() {
    UUID project = project();
    jdbc.execute("ALTER TABLE adrs DROP CONSTRAINT adrs_project_adr_id_uq");
    legacyAdr(project, "ADR-001");
    legacyAdr(project, "ADR-001");
    assertThrows(org.flywaydb.core.api.FlywayException.class, () -> migrate("latest"));
    assertEquals(2, adrs.findAll(project, null, null).size());
  }

  @Test
  void failedCreationRollsBackBothCounterAndRecord() {
    migrate("latest");
    UUID project = project();
    var transactions = new TransactionTemplate(context.getBean(JdbcTransactionManager.class));
    assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
      var adr = adrs.create(project, ReadableIdServiceTest.adrRequest());
      rules.create(project, ReadableIdServiceTest.ruleRequest(adr.id()));
      throw new IllegalStateException("Force rollback after both inserts");
    }));
    assertTrue(adrs.findAll(project, null, null).isEmpty());
    assertTrue(rules.findAll(project).isEmpty());
    var adr = adrs.create(project, ReadableIdServiceTest.adrRequest());
    assertEquals("ADR-001", adr.adrId());
    assertEquals("R-001", rules.create(project, ReadableIdServiceTest.ruleRequest(adr.id())).ruleId());
  }

  @Test
  @Timeout(90)
  void concurrentCreationsAreUniqueForBothTypesIncludingFirstAllocation() throws Exception {
    migrate("latest");
    UUID project = project();
    UUID ruleProject = project();
    var linked = adrs.create(ruleProject, ReadableIdServiceTest.adrRequest());
    // Both counters under contention are absent initially.
    var start = new CountDownLatch(1);
    var work = new ArrayList<Callable<String>>();
    for (int i = 0; i < 12; i++) {
      work.add(() -> { assertTrue(start.await(10, TimeUnit.SECONDS));
        return adrs.create(project, ReadableIdServiceTest.adrRequest()).adrId(); });
      work.add(() -> { assertTrue(start.await(10, TimeUnit.SECONDS));
        return rules.create(ruleProject, ReadableIdServiceTest.ruleRequest(linked.id())).ruleId(); });
    }
    try (var executor = Executors.newFixedThreadPool(24)) {
      var results = work.stream().map(executor::submit).toList();
      start.countDown();
      var actual = new HashSet<String>();
      for (var result : results) assertTrue(actual.add(result.get(60, TimeUnit.SECONDS)));
      for (int i = 1; i <= 12; i++) {
        assertTrue(actual.contains("ADR-%03d".formatted(i)));
        assertTrue(actual.contains("R-%03d".formatted(i)));
      }
    }
    assertEquals(12, adrs.findAll(project, null, null).size());
    assertEquals(12, rules.findAll(ruleProject).size());
  }
}
