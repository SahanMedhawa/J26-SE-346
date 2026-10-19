package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import akegp.rules.validation.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdrRuleFidelityTest {
  private final InMemoryRuleRepository rules = new InMemoryRuleRepository();
  private final InMemoryADRRepository adrsRepo = new InMemoryADRRepository();
  private final ProjectRegistrationServiceImpl projects =
      new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
  private final TestReadableIdRepository ids = new TestReadableIdRepository();
  private ADRServiceImpl adrs;
  private final ArvmCatalogue catalogue = new ArvmCatalogue();
  private ValidatorFactory factory;
  private RuleStructureValidationService structural;
  private AdrRuleFidelityService fidelity;
  private MockMvc mvc;
  private UUID projectId;
  private UUID adrId;

  @BeforeEach
  void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    adrs = new ADRServiceImpl(adrsRepo, projects, ids);
    structural = new RuleStructureValidationService(rules, projects, adrs, factory.getValidator(), catalogue);
    var assurance = new TestRuleAssuranceRepository();
    var evidence = new RuleEvidenceService(projects, rules, adrs,
        new ImplementationMappingControllerTest.MemoryMappings(rules), assurance);
    fidelity = new AdrRuleFidelityService(evidence);
    var stored = new StoredStructuralValidationService(structural, evidence, assurance, catalogue);
    mvc = MockMvcBuilders.standaloneSetup(
        new RuleStructureValidationController(stored, fidelity, catalogue),
        new RuleController(new RuleServiceImpl(rules, projects, adrs, ids)))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler()).build();
    projectId = projects.register(new ProjectRegistrationRequest(
        "Project", null, "https://github.com/example/" + UUID.randomUUID(),
        Set.of(ArchitectureStyle.MICROSERVICES))).id();
    adrId = adrs.create(projectId, new CreateADRRequest(
        null, "Architecture", ADRStatus.ACCEPTED,
        "Enough context for ADR",
        "OrderService must not directly access OrdersDb",
        "Enough consequences text")).id();
  }

  @AfterEach
  void closeValidator() {
    factory.close();
  }

  private ArchitectureRule saveRule(
      String source, RelationshipType relationship, String target, RuleDecision decision) {
    return saveRule("R-001", source, relationship, target, decision);
  }

  private ArchitectureRule saveRule(
      String ruleId, String source, RelationshipType relationship, String target, RuleDecision decision) {
    var now = Instant.now();
    return rules.insert(new ArchitectureRule(
        UUID.randomUUID(), ruleId, projectId, adrId, source,
        ArchitectureElementType.SERVICE, relationship, target,
        ArchitectureElementType.DATABASE, decision, "Description", RuleSeverity.HIGH, now, now));
  }

  private void setAdrDecision(String decision) {
    var current = adrs.findById(projectId, adrId);
    assertTrue(adrsRepo.update(new ArchitectureDecisionRecord(
        current.id(), current.adrId(), current.projectId(), current.title(), current.status(),
        current.context(), decision, current.consequences(), current.version() + 1,
        current.createdAt(), Instant.now()), current.version()));
  }

  private String fidelityPath(UUID ruleUuid) {
    return "/api/rules/projects/" + projectId + "/rules/" + ruleUuid + "/adr-fidelity";
  }

  @Test
  void exactMatchingAdrAndRuleIsMatch() throws Exception {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.MATCH, result.status());
    assertTrue(result.explanation().toLowerCase().contains("match"));
    mvc.perform(get(fidelityPath(rule.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MATCH"))
        .andExpect(jsonPath("$.ruleUuid").value(rule.id().toString()))
        .andExpect(jsonPath("$.explanation").isNotEmpty());
  }

  @Test
  void differentSourceRequiresReview() {
    var rule = saveRule("BillingService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
    assertTrue(result.explanation().contains("source element"));
  }

  @Test
  void differentTargetRequiresReview() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "PaymentsDb",
        RuleDecision.FORBIDDEN);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
    assertTrue(result.explanation().contains("target element"));
  }

  @Test
  void differentDecisionIsMismatch() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.ALLOWED);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.MISMATCH, result.status());
    assertTrue(result.explanation().contains("decision"));
  }

  @Test
  void differentRelationshipRequiresReview() {
    var rule = saveRule("OrderService", RelationshipType.DATA_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
    assertTrue(result.explanation().contains("relationship"));
  }

  @Test
  void unsupportedAdrWordingIsReviewRequired() {
    setAdrDecision("OrderService should avoid talking to OrdersDb casually");
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
    assertTrue(result.explanation().toLowerCase().contains("unsupported")
        || result.explanation().toLowerCase().contains("ambiguous"));
  }

  @Test
  void missingAdrOrMissingDecisionTextIsReviewRequired() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    rules.update(new ArchitectureRule(
        rule.id(), rule.ruleId(), projectId, UUID.randomUUID(), rule.sourceElement(),
        rule.sourceElementType(), rule.relationshipType(), rule.targetElement(),
        rule.targetElementType(), rule.decision(), rule.description(), rule.severity(),
        rule.createdAt(), rule.updatedAt()));
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED,
        fidelity.evaluate(projectId, rule.id()).status());

    rules.update(new ArchitectureRule(
        rule.id(), rule.ruleId(), projectId, adrId, rule.sourceElement(),
        rule.sourceElementType(), rule.relationshipType(), rule.targetElement(),
        rule.targetElementType(), rule.decision(), rule.description(), rule.severity(),
        rule.createdAt(), rule.updatedAt()));
    setAdrDecision("   ");
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED,
        fidelity.evaluate(projectId, rule.id()).status());
  }

  @Test
  void structuralValidationRemainsUnchangedByFidelityEndpoint() throws Exception {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    var before = structural.validate(projectId, rule.id(), null);
    assertEquals(StructuralValidationStatus.SUPPORTED, before.validationStatus());
    assertEquals("M08", before.matchedSignatureId());

    mvc.perform(get(fidelityPath(rule.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MATCH"));

    var after = structural.validate(projectId, rule.id(), null);
    assertEquals(before.validationStatus(), after.validationStatus());
    assertEquals(before.matchedSignatureId(), after.matchedSignatureId());
    assertEquals(before.message(), after.message());
    assertEquals(rule, rules.findById(projectId, rule.id()).orElseThrow());

    mvc.perform(post("/api/rules/projects/{p}/rules/{r}/validate-structure", projectId, rule.id())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("SUPPORTED"))
        .andExpect(jsonPath("$.matchedSignatureId").value("M08"));
  }

  @Test
  void parserRecognizesOptionalTrailingPeriodAndRejectsBlank() {
    var expected = List.of(new AdrDecisionFidelityParser.InterpretedDecision(
        "A", RelationshipType.DIRECT_DATABASE_ACCESS, "B", RuleDecision.FORBIDDEN));
    assertEquals(expected, AdrDecisionFidelityParser.parse("A must not directly access B."));
    assertEquals(expected, AdrDecisionFidelityParser.parse("  A MUST   NOT DIRECTLY ACCESS B  "));
    assertEquals(expected, AdrDecisionFidelityParser.parse("A must not\ndirectly access B."));
    assertTrue(AdrDecisionFidelityParser.parse("").isEmpty());
    assertTrue(AdrDecisionFidelityParser.parse(null).isEmpty());
    assertTrue(AdrDecisionFidelityParser.parse("A must not access B").isEmpty());
  }

  @Test
  void multipleLinkedRulesIndependentlyMatchSourceListWithExplanation() throws Exception {
    setAdrDecision("AppointmentService and AdminService must not directly access DoctorDB. "
        + "Doctor-related information must be accessed through the interfaces exposed by DoctorService.");
    var appointment = saveRule("R-003", "AppointmentService", RelationshipType.DIRECT_DATABASE_ACCESS,
        "DoctorDB", RuleDecision.FORBIDDEN);
    var admin = saveRule("R-004", "AdminService", RelationshipType.DIRECT_DATABASE_ACCESS,
        "DoctorDB", RuleDecision.FORBIDDEN);
    for (var rule : List.of(appointment, admin)) {
      assertEquals(AdrRuleFidelityStatus.MATCH, fidelity.evaluate(projectId, rule.id()).status());
      mvc.perform(get(fidelityPath(rule.id())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("MATCH"))
          .andExpect(jsonPath("$.ruleUuid").value(rule.id().toString()))
          .andExpect(jsonPath("$.ruleId").value(rule.ruleId()))
          .andExpect(jsonPath("$.adrUuid").value(adrId.toString()));
      assertEquals(rule, rules.findById(projectId, rule.id()).orElseThrow());
    }
    var parsed = AdrDecisionFidelityParser.parse(adrs.findById(projectId, adrId).decision());
    assertEquals(List.of("AppointmentService", "AdminService"),
        parsed.stream().map(AdrDecisionFidelityParser.InterpretedDecision::sourceElement).toList());
    assertTrue(parsed.stream().allMatch(constraint -> constraint.targetElement().equals("DoctorDB")));
  }

  @Test
  void separateConstraintsAreSelectedByTheFullTripleRegardlessOfOrder() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    for (String text : List.of(
        "BillingService must not directly access OrdersDb. OrderService must not directly access OrdersDb.",
        "OrderService must not directly access OrdersDb. BillingService must not directly access OrdersDb.",
        "OrderService must not directly access PaymentsDb; OrderService must not directly access OrdersDb",
        "Access is mediated by service interfaces.\nOrderService must not directly access OrdersDb. "
            + "This preserves service ownership.")) {
      setAdrDecision(text);
      assertEquals(AdrRuleFidelityStatus.MATCH, fidelity.evaluate(projectId, rule.id()).status(), text);
    }
  }

  @Test
  void commaSeparatedSourcesExpandToIndependentConstraints() {
    for (String sources : List.of("A, B, C", "A, B and C", "A, B, and C", "A AND B and C")) {
      var parsed = AdrDecisionFidelityParser.parse(sources + " must not directly access Db.");
      assertEquals(List.of("A", "B", "C"),
          parsed.stream().map(AdrDecisionFidelityParser.InterpretedDecision::sourceElement).toList());
      assertTrue(parsed.stream().allMatch(constraint -> constraint.targetElement().equals("Db")));
    }
  }

  @Test
  void requiredAccessAlsoContradictsRelevantProhibition() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.REQUIRED);
    assertEquals(AdrRuleFidelityStatus.MISMATCH, fidelity.evaluate(projectId, rule.id()).status());
  }

  @Test
  void unrelatedRecognizedConstraintDoesNotTurnUnsupportedRelevantWordingIntoMismatch() {
    setAdrDecision("BillingService must not directly access OrdersDb. "
        + "OrderService should avoid talking to OrdersDb casually.");
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.ALLOWED);
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, fidelity.evaluate(projectId, rule.id()).status());
  }

  @Test
  void ambiguousStatementsAndProseAreNotInterpretedAsElementIdentifiers() {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb",
        RuleDecision.FORBIDDEN);
    for (String text : List.of(
        "OrderService or AdminService must not directly access OrdersDb.",
        "If running in production, OrderService must not directly access OrdersDb.",
        "If running in production,\nOrderService must not directly access OrdersDb.",
        "OrderService must not directly access OrdersDb unless approved.",
        "OrderService must not directly access OrdersDb\nunless approved.",
        "OrderService must not directly access OrdersDb and PaymentsDb.",
        "OrderService must not directly access OrdersDb through DoctorService.",
        "OrderService must not directly access OrdersDb?",
        "OrderService must not directly access",
        "OrderService, must not directly access OrdersDb.",
        "Doctor-related information must be accessed through the interfaces exposed by DoctorService.")) {
      setAdrDecision(text);
      assertTrue(AdrDecisionFidelityParser.parse(text).isEmpty(), text);
      assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, fidelity.evaluate(projectId, rule.id()).status(), text);
    }
  }
}
