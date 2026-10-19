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
    return saveRule(ruleId, source, ArchitectureElementType.SERVICE, relationship,
        target, ArchitectureElementType.DATABASE, decision);
  }

  private ArchitectureRule saveRule(
      String ruleId, String source, ArchitectureElementType sourceType, RelationshipType relationship,
      String target, ArchitectureElementType targetType, RuleDecision decision) {
    var now = Instant.now();
    return rules.insert(new ArchitectureRule(
        UUID.randomUUID(), ruleId, projectId, adrId, source,
        sourceType, relationship, target,
        targetType, decision, "Description", RuleSeverity.HIGH, now, now));
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
    assertEquals("The architecture rule is consistent with the decision recorded in its linked ADR.",
        result.explanation());
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
    assertEquals("The ADR decision cannot be reliably evaluated using the supported decision patterns. "
        + "Manual review is required.", result.explanation());
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

  @Test
  void explicitModalPatternsMatchAndEveryOtherDecisionContradicts() {
    var verbs = List.of("depend on", "call", "directly access");
    var relationships = List.of(RelationshipType.DEPENDS_ON, RelationshipType.CALLS,
        RelationshipType.DIRECT_DATABASE_ACCESS);
    var modals = List.of("must", "must not", "may");
    var decisions = List.of(RuleDecision.REQUIRED, RuleDecision.FORBIDDEN, RuleDecision.ALLOWED);
    for (int v = 0; v < verbs.size(); v++) {
      for (int m = 0; m < modals.size(); m++) {
        setAdrDecision("OrderService " + modals.get(m) + " " + verbs.get(v) + " OrdersDb.");
        for (var ruleDecision : RuleDecision.values()) {
          var rule = saveRule("R-" + v + m + ruleDecision, "OrderService", relationships.get(v),
              "OrdersDb", ruleDecision);
          var result = fidelity.evaluate(projectId, rule.id());
          assertEquals(ruleDecision == decisions.get(m) ? AdrRuleFidelityStatus.MATCH
              : AdrRuleFidelityStatus.MISMATCH, result.status());
          assertFalse(result.explanation().toUpperCase().contains("PP1"));
        }
      }
    }
  }

  @Test
  void layeredConstraintsIndependentlyMatchAndRemainSeparateFromStructuralSupport() throws Exception {
    projectId = projects.register(new ProjectRegistrationRequest(
        "Layered", null, "https://github.com/example/" + UUID.randomUUID(),
        Set.of(ArchitectureStyle.LAYERED))).id();
    adrId = adrs.create(projectId, new CreateADRRequest(null, "Layered dependencies", ADRStatus.ACCEPTED,
        "Enough context for ADR", "OwnerController must depend on ClinicService and must not depend on OwnerRepository.",
        "Enough consequences text")).id();
    var required = saveRule("R-001", "OwnerController", ArchitectureElementType.CONTROLLER,
        RelationshipType.DEPENDS_ON, "ClinicService", ArchitectureElementType.SERVICE_LAYER, RuleDecision.REQUIRED);
    var forbidden = saveRule("R-002", "OwnerController", ArchitectureElementType.CONTROLLER,
        RelationshipType.DEPENDS_ON, "OwnerRepository", ArchitectureElementType.REPOSITORY, RuleDecision.FORBIDDEN);
    for (String text : List.of(
        "OwnerController must depend on ClinicService and must not depend on OwnerRepository.",
        "OwnerController must depend on ClinicService and OwnerController must not depend on OwnerRepository.",
        "OwnerController must depend on ClinicService; OwnerController must not depend on OwnerRepository.",
        "OwnerController must depend on ClinicService. OwnerController must not depend on OwnerRepository.")) {
      setAdrDecision(text);
      for (var rule : List.of(required, forbidden)) {
        assertEquals(AdrRuleFidelityStatus.MATCH, fidelity.evaluate(projectId, rule.id()).status(), text);
        assertEquals(StructuralValidationStatus.SUPPORTED,
            structural.validate(projectId, rule.id(), null).validationStatus());
        mvc.perform(get(fidelityPath(rule.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("MATCH"))
            .andExpect(jsonPath("$.adrUuid").value(adrId.toString()))
            .andExpect(jsonPath("$.adrDisplayId").value(adrs.findById(projectId, adrId).adrId()));
        assertEquals(rule, rules.findById(projectId, rule.id()).orElseThrow());
      }
    }
    setAdrDecision("OwnerController must access operations through ClinicService.");
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, fidelity.evaluate(projectId, required.id()).status());
  }

  @Test
  void conjunctionsPreserveSourceListsAndExplicitSubjectChanges() {
    var parsed = AdrDecisionFidelityParser.parse(
        "A, B and C MUST depend ON pkg.Service, and may CALL pkg.Api and D must not directly access Db.");
    assertEquals(7, parsed.size());
    assertEquals(List.of("A", "B", "C", "A", "B", "C", "D"),
        parsed.stream().map(AdrDecisionFidelityParser.InterpretedDecision::sourceElement).toList());
    assertEquals(new AdrDecisionFidelityParser.InterpretedDecision(
        "D", RelationshipType.DIRECT_DATABASE_ACCESS, "Db", RuleDecision.FORBIDDEN), parsed.get(6));
    assertTrue(parsed.subList(3, 6).stream().allMatch(c -> c.relationshipType() == RelationshipType.CALLS
        && c.decision() == RuleDecision.ALLOWED && c.targetElement().equals("pkg.Api")));
  }

  @Test
  void qualifiedIncompleteOrUnsupportedConjunctionGroupsAreNotPartiallyAccepted() {
    for (String text : List.of(
        "A must depend on B and must not depend on C unless approved.",
        "A must depend on B unless approved and must call C.",
        "A must depend on B and may call C?",
        "If approved, A must depend on B and must call C.",
        "A must depend on B and must call",
        "A must depend on B and",
        "A must depend on B and C.",
        "A must depend on B or must call C.",
        "A should depend on B.",
        "A must access operations through B.",
        "must depend on B.",
        "A must depend on B; must call C unless approved.")) {
      var parsed = AdrDecisionFidelityParser.parse(text);
      if (text.contains(";")) assertEquals(1, parsed.size(), text);
      else assertTrue(parsed.isEmpty(), text);
    }
    assertEquals(1, AdrDecisionFidelityParser.parse(
        "A must depend on B and must call C unless approved. D must call E.").size());
  }

  @Test
  void fullTripleMustMatchAndContradictionsTakePrecedence() {
    var rule = saveRule("OrderService", RelationshipType.DEPENDS_ON, "OrdersDb", RuleDecision.REQUIRED);
    for (String text : List.of(
        "BillingService must depend on OrdersDb.",
        "OrderService must depend on PaymentsDb.",
        "OrderService must call OrdersDb.",
        "orderservice must depend on OrdersDb.",
        "BillingService must depend on OrdersDb. OrderService should depend on OrdersDb.")) {
      setAdrDecision(text);
      assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, fidelity.evaluate(projectId, rule.id()).status(), text);
    }
    for (String text : List.of(
        "OrderService must depend on OrdersDb and must not depend on OrdersDb.",
        "OrderService must not depend on OrdersDb. OrderService must depend on OrdersDb.")) {
      setAdrDecision(text);
      assertEquals(AdrRuleFidelityStatus.MISMATCH, fidelity.evaluate(projectId, rule.id()).status(), text);
    }
  }

  @Test
  void fidelityDoesNotReadRulesOrLinkedAdrsFromAnotherProject() throws Exception {
    var rule = saveRule("OrderService", RelationshipType.DIRECT_DATABASE_ACCESS, "OrdersDb", RuleDecision.FORBIDDEN);
    var otherProject = projects.register(new ProjectRegistrationRequest(
        "Other project", null, "https://github.com/example/" + UUID.randomUUID(),
        Set.of(ArchitectureStyle.LAYERED))).id();
    assertThrows(RuleNotFoundException.class, () -> fidelity.evaluate(otherProject, rule.id()));
    mvc.perform(get("/api/rules/projects/{p}/rules/{r}/adr-fidelity", otherProject, rule.id()))
        .andExpect(status().isNotFound());
    var foreignAdr = adrs.create(otherProject, new CreateADRRequest(null, "Foreign ADR", ADRStatus.ACCEPTED,
        "Enough context for ADR", "OrderService must not directly access OrdersDb", "Enough consequences text"));
    rules.update(new ArchitectureRule(rule.id(), rule.ruleId(), projectId, foreignAdr.id(), rule.sourceElement(),
        rule.sourceElementType(), rule.relationshipType(), rule.targetElement(), rule.targetElementType(),
        rule.decision(), rule.description(), rule.severity(), rule.createdAt(), rule.updatedAt()));
    var result = fidelity.evaluate(projectId, rule.id());
    assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
    assertNull(result.adrUuid());
    assertFalse(result.explanation().contains("PP1"));
  }

  @Test
  void reviewExplanationsContainNoPp1Wording() {
    var rule = saveRule("OrderService", RelationshipType.DEPENDS_ON, "OrdersDb", RuleDecision.REQUIRED);
    for (String text : List.of(" ", "Unsupported decision prose.", "BillingService must depend on OrdersDb.")) {
      setAdrDecision(text);
      var result = fidelity.evaluate(projectId, rule.id());
      assertEquals(AdrRuleFidelityStatus.REVIEW_REQUIRED, result.status());
      assertFalse(result.explanation().toUpperCase().contains("PP1"));
    }
  }
}
