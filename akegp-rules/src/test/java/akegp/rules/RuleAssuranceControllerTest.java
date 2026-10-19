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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleAssuranceControllerTest {
  private static ValidatorFactory factory;
  private InMemoryRuleRepository ruleRepository;
  private InMemoryADRRepository adrRepository;
  private ProjectRegistrationServiceImpl projects;
  private ADRServiceImpl adrs;
  private RuleServiceImpl rules;
  private ImplementationMappingServiceImpl mappings;
  private TestRuleAssuranceRepository assurance;
  private StoredStructuralValidationService validation;
  private ArchitectReviewService reviews;
  private MockMvc mvc;
  private UUID project;
  private UUID rule;
  private UUID adr;

  @BeforeAll static void validator() { factory = Validation.buildDefaultValidatorFactory(); }
  @AfterAll static void closeValidator() { factory.close(); }

  @BeforeEach
  void setUp() {
    ruleRepository = new InMemoryRuleRepository();
    adrRepository = new InMemoryADRRepository();
    projects = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    var ids = new TestReadableIdRepository();
    adrs = new ADRServiceImpl(adrRepository, projects, ids);
    rules = new RuleServiceImpl(ruleRepository, projects, adrs, ids);
    project = projects.register(new ProjectRegistrationRequest("HealthMate", null,
        "https://github.com/example/healthmate", Set.of(ArchitectureStyle.MICROSERVICES))).id();
    adr = adrs.create(project, ReadableIdServiceTest.adrRequest()).id();
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
    assurance = new TestRuleAssuranceRepository();
    var mappingRepository = new ImplementationMappingControllerTest.MemoryMappings(ruleRepository);
    mappings = new ImplementationMappingServiceImpl(mappingRepository, ruleRepository, projects);
    var evidence = new RuleEvidenceService(projects, ruleRepository, adrs, mappingRepository, assurance);
    var catalogue = new ArvmCatalogue();
    validation = new StoredStructuralValidationService(new RuleStructureValidationService(
        ruleRepository, projects, adrs, factory.getValidator(), catalogue), evidence, assurance, catalogue);
    reviews = new ArchitectReviewService(evidence, validation, assurance);
    mvc = MockMvcBuilders.standaloneSetup(
        new RuleStructureValidationController(validation, new AdrRuleFidelityService(evidence), catalogue),
        new ArchitectReviewController(reviews), new RuleController(rules), new ImplementationMappingController(mappings))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler(), new RulePersistenceExceptionHandler()).build();
  }

  private String base() { return "/api/rules/projects/" + project + "/rules/" + rule; }
  private ArchitectReviewRequest confirm() {
    return new ArchitectReviewRequest(ArchitectReviewDecision.CONFIRMED, "Architect", "Reviewed",
        reviews.find(project, rule).evidenceFingerprint());
  }
  private void readyForReview() {
    validation.validate(project, rule, null);
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Order.java"), List.of("src/Payment.java")));
  }
  private void update(ArchitectureRule value) {
    rules.update(project, rule, new UpdateRuleRequest(null, value.adrId(), value.sourceElement(), value.sourceElementType(),
        value.relationshipType(), value.targetElement(), value.targetElementType(), value.decision(), value.description(), value.severity()));
  }

  @Test
  void neverValidatedHasMeaningfulNotValidatedAndPendingResponses() throws Exception {
    mvc.perform(get(base() + "/structural-validation")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOT_VALIDATED"))
        .andExpect(jsonPath("$.current").value(false)).andExpect(jsonPath("$.validatedAt").isEmpty());
    mvc.perform(get(base() + "/architect-review")).andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("PENDING"))
        .andExpect(jsonPath("$.confirmationEligible").value(false))
        .andExpect(jsonPath("$.blockedReasons.length()").value(3));
    assertTrue(assurance.validations.isEmpty());
    assertTrue(reviews.history(project, rule).isEmpty());
  }

  @Test
  void existingValidationPostResponseIsPreservedAndResultIsStored() throws Exception {
    mvc.perform(post(base() + "/validate-structure")).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleId").value("R-001"))
        .andExpect(jsonPath("$.ruleUuid").value(rule.toString()))
        .andExpect(jsonPath("$.projectId").value(project.toString()))
        .andExpect(jsonPath("$.architectureStyle").value("MICROSERVICES"))
        .andExpect(jsonPath("$.sourceElementType").value("SERVICE"))
        .andExpect(jsonPath("$.relationshipType").value("CALLS"))
        .andExpect(jsonPath("$.targetElementType").value("SERVICE"))
        .andExpect(jsonPath("$.decision").value("ALLOWED"))
        .andExpect(jsonPath("$.validationStatus").value("SUPPORTED"))
        .andExpect(jsonPath("$.matchedSignatureId").value("M01"))
        .andExpect(jsonPath("$.message").isNotEmpty());
    mvc.perform(get(base() + "/structural-validation")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUPPORTED"))
        .andExpect(jsonPath("$.current").value(true)).andExpect(jsonPath("$.validatedAt").isNotEmpty())
        .andExpect(jsonPath("$.catalogueVersion").isEmpty());
  }

  @Test
  void forbiddenSignatureCanBeSupportedAndConfirmed() {
    var r = ruleRepository.findById(project, rule).orElseThrow();
    update(new ArchitectureRule(r.id(), r.ruleId(), r.projectId(), r.adrId(), r.sourceElement(), r.sourceElementType(),
        RelationshipType.DIRECT_DATABASE_ACCESS, "PaymentDB", ArchitectureElementType.DATABASE,
        RuleDecision.FORBIDDEN, r.description(), r.severity(), r.createdAt(), r.updatedAt()));
    readyForReview();
    assertEquals("M08", assurance.validation(rule).orElseThrow().matchedSignatureId());
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.submit(project, rule, confirm()).reviewStatus());
  }

  @Test
  void unsupportedIsPersistedAndCannotBeConfirmedButChangesCanBeRequested() throws Exception {
    var r = ruleRepository.findById(project, rule).orElseThrow();
    update(new ArchitectureRule(r.id(), r.ruleId(), r.projectId(), r.adrId(), r.sourceElement(), ArchitectureElementType.DATABASE,
        r.relationshipType(), r.targetElement(), r.targetElementType(), r.decision(), r.description(), r.severity(), r.createdAt(), r.updatedAt()));
    readyForReview();
    assertEquals(StructuralValidationStatus.UNSUPPORTED, assurance.validation(rule).orElseThrow().status());
    assertThrows(ArchitectReviewException.class, () -> reviews.submit(project, rule, confirm()));
    mvc.perform(post(base() + "/architect-review").contentType(MediaType.APPLICATION_JSON).content("""
        {"decision":"CHANGES_REQUESTED","reviewer":"Architect","comments":"Please clarify this unsupported relationship."}
        """)).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("CHANGES_REQUESTED"))
        .andExpect(jsonPath("$.structuralValidationStatus").value("UNSUPPORTED"));
    assertEquals(1, reviews.history(project, rule).size());
  }

  @Test
  void confirmationRequiresValidationAndBothMappingSides() throws Exception {
    mvc.perform(post(base() + "/architect-review").contentType(MediaType.APPLICATION_JSON)
        .content("{\"decision\":\"CONFIRMED\",\"reviewer\":\"Architect\",\"expectedEvidenceFingerprint\":\""
            + reviews.find(project, rule).evidenceFingerprint() + "\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
    validation.validate(project, rule, null);
    for (var request : List.of(new ImplementationMappingsRequest(List.of(), List.of()),
        new ImplementationMappingsRequest(List.of("src/A.java"), List.of()),
        new ImplementationMappingsRequest(List.of(), List.of("src/B.java")))) {
      mappings.replace(project, rule, request);
      assertFalse(reviews.find(project, rule).confirmationEligible());
      assertThrows(ArchitectReviewException.class, () -> reviews.submit(project, rule, confirm()));
    }
    assertTrue(reviews.history(project, rule).isEmpty());
  }

  @Test
  void confirmsWithEvidenceAndPreservesOrderedHistory() throws Exception {
    readyForReview();
    String fingerprint = reviews.find(project, rule).evidenceFingerprint();
    mvc.perform(post(base() + "/architect-review").contentType(MediaType.APPLICATION_JSON).content("""
        {"decision":"CONFIRMED","reviewer":"Software Architect","comments":"Interpretation reviewed.","expectedEvidenceFingerprint":"%s"}
        """.formatted(fingerprint))).andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.adrVersion").value(1))
        .andExpect(jsonPath("$.latestReview.current").value(true))
        .andExpect(jsonPath("$.latestReview.reviewerAuthenticated").value(false))
        .andExpect(jsonPath("$.latestReview.review.decidedAt").isNotEmpty());
    reviews.submit(project, rule, new ArchitectReviewRequest(ArchitectReviewDecision.CHANGES_REQUESTED,
        "Second Architect", "Please explain the target element more clearly.", null));
    mvc.perform(get(base() + "/architect-review/history")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].review.decision").value("CHANGES_REQUESTED"))
        .andExpect(jsonPath("$[1].review.decision").value("CONFIRMED"));
    assertEquals(ArchitectReviewStatus.CHANGES_REQUESTED, reviews.find(project, rule).reviewStatus());
    assertEquals(StructuralValidationStatus.SUPPORTED, validation.find(project, rule).status());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sourceType", "relationship", "targetType", "projectStyle"})
  void structuralChangesInvalidateValidationAndConfirmation(String field) {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var r = ruleRepository.findById(project, rule).orElseThrow();
    if (field.equals("projectStyle")) {
      var p = projects.findById(project);
      projects.update(project, new ProjectUpdateRequest(p.name(), p.description(), p.repositoryUrl(), Set.of(ArchitectureStyle.LAYERED)));
    } else {
      update(new ArchitectureRule(r.id(), r.ruleId(), r.projectId(), r.adrId(), r.sourceElement(),
          field.equals("sourceType") ? ArchitectureElementType.API_GATEWAY : r.sourceElementType(),
          field.equals("relationship") ? RelationshipType.DEPENDS_ON : r.relationshipType(), r.targetElement(),
          field.equals("targetType") ? ArchitectureElementType.EXTERNAL_SYSTEM : r.targetElementType(),
          r.decision(), r.description(), r.severity(), r.createdAt(), r.updatedAt()));
    }
    assertEquals(StructuralValidationStatus.NOT_VALIDATED, validation.find(project, rule).status());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertThrows(ArchitectReviewException.class, () -> reviews.submit(project, rule, confirm()));
    assertFalse(reviews.history(project, rule).getFirst().current());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sourceName", "targetName", "decision", "description", "severity", "adrLink"})
  void interpretationEditsInvalidateReviewWithoutInvalidatingStructuralValidation(String field) {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var r = ruleRepository.findById(project, rule).orElseThrow();
    UUID linked = field.equals("adrLink") ? adrs.create(project, ReadableIdServiceTest.adrRequest()).id() : r.adrId();
    update(new ArchitectureRule(r.id(), r.ruleId(), r.projectId(), linked,
        field.equals("sourceName") ? "Edited source" : r.sourceElement(), r.sourceElementType(), r.relationshipType(),
        field.equals("targetName") ? "Edited target" : r.targetElement(), r.targetElementType(),
        field.equals("decision") ? RuleDecision.FORBIDDEN : r.decision(),
        field.equals("description") ? "Edited interpretation" : r.description(),
        field.equals("severity") ? RuleSeverity.LOW : r.severity(), r.createdAt(), r.updatedAt()));
    assertTrue(validation.find(project, rule).current());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertEquals(1, reviews.history(project, rule).size());
  }

  @Test
  void existingRuleUpdateApiInvalidatesTheSavedStructuralResult() throws Exception {
    readyForReview();
    mvc.perform(put(base()).contentType(MediaType.APPLICATION_JSON).content("""
        {"adrId":"%s","sourceElement":"Orders","sourceElementType":"SERVICE",
         "relationshipType":"DEPENDS_ON","targetElement":"Payments","targetElementType":"SERVICE",
         "decision":"ALLOWED","description":"Updated through existing CRUD","severity":"HIGH"}
        """.formatted(adr))).andExpect(status().isOk());
    mvc.perform(get(base() + "/structural-validation")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOT_VALIDATED"));
  }

  @Test
  void revalidationUpsertsCurrentResultAndDoesNotInvalidateIdenticalReviewEvidence() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    UUID id = assurance.validation(rule).orElseThrow().id();
    validation.validate(project, rule, null);
    assertEquals(id, assurance.validation(rule).orElseThrow().id());
    assertEquals(1, assurance.validations.size());
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.find(project, rule).reviewStatus());
    var r = ruleRepository.findById(project, rule).orElseThrow();
    update(new ArchitectureRule(r.id(), r.ruleId(), r.projectId(), r.adrId(), r.sourceElement(), ArchitectureElementType.DATABASE,
        r.relationshipType(), r.targetElement(), r.targetElementType(), r.decision(), r.description(), r.severity(), r.createdAt(), r.updatedAt()));
    validation.validate(project, rule, null);
    assertEquals(id, assurance.validation(rule).orElseThrow().id());
    assertEquals(StructuralValidationStatus.UNSUPPORTED, validation.find(project, rule).status());
  }

  @Test
  void adrContentOrVersionChangeInvalidatesConfirmation() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), a.status(), a.context(),
        "A revised documented architecture decision", a.consequences(), a.version()));
    assertEquals(2, reviews.find(project, rule).adrVersion());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    assertTrue(validation.find(project, rule).current());
  }

  @Test
  void missingAdrBlocksConfirmationButAllowsChangesRequested() {
    readyForReview();
    adrRepository.delete(project, adr); // Simulate missing evidence; production FK normally prevents this.
    var result = reviews.find(project, rule);
    assertNull(result.adrVersion());
    assertFalse(result.confirmationEligible());
    assertThrows(ArchitectReviewException.class, () -> reviews.submit(project, rule, confirm()));
    assertEquals(ArchitectReviewStatus.CHANGES_REQUESTED, reviews.submit(project, rule,
        new ArchitectReviewRequest(ArchitectReviewDecision.CHANGES_REQUESTED, "Architect", "Restore the linked ADR before confirmation.", null)).reviewStatus());
  }

  @Test
  void mappingChangesInvalidateConfirmationAndStaleFormFingerprintIsRejected() throws Exception {
    readyForReview();
    var confirmed = reviews.submit(project, rule, confirm());
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Other.java"), List.of("src/Payment.java")));
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
    mvc.perform(post(base() + "/architect-review").contentType(MediaType.APPLICATION_JSON).content("""
        {"decision":"CONFIRMED","reviewer":"Architect","expectedEvidenceFingerprint":"%s"}
        """.formatted(confirmed.evidenceFingerprint()))).andExpect(status().isConflict());
    assertEquals(1, reviews.history(project, rule).size());
  }

  @Test
  void revisionPreventsChangeThenRevertFromRevivingOldValidationAndReview() {
    readyForReview();
    reviews.submit(project, rule, confirm());
    // PostgreSQL triggers advance this even if later content returns to its original value.
    assurance.advance(rule, true);
    assertFalse(validation.find(project, rule).current());
    validation.validate(project, rule, null);
    assertTrue(validation.find(project, rule).current());
    assertEquals(ArchitectReviewStatus.PENDING, reviews.find(project, rule).reviewStatus());
  }

  @Test
  void noOpRuleAdrAndMappingUpdatesPreserveConfirmation() {
    readyForReview();
    var before = reviews.submit(project, rule, confirm());
    update(ruleRepository.findById(project, rule).orElseThrow());
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), a.status(), a.context(), a.decision(), a.consequences(), a.version()));
    mappings.replace(project, rule, new ImplementationMappingsRequest(List.of("src/Order.java"), List.of("src/Payment.java")));
    assertEquals(before.evidenceFingerprint(), reviews.find(project, rule).evidenceFingerprint());
    assertEquals(ArchitectReviewStatus.CONFIRMED, reviews.find(project, rule).reviewStatus());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"decision\":\"CONFIRMED\",\"reviewer\":\"Architect\"}",
      "{\"decision\":\"PENDING\",\"reviewer\":\"Architect\"}",
      "{\"decision\":\"CONFIRMED\",\"reviewer\":\"   \"}",
      "{\"decision\":\"CHANGES_REQUESTED\",\"reviewer\":\"Architect\"}",
      "{\"decision\":\"CHANGES_REQUESTED\",\"reviewer\":\"Architect\",\"comments\":\"short\"}",
      "{\"decision\":\"CONFIRMED\",\"reviewer\":\"Architect\",\"expectedEvidenceFingerprint\":\"invalid\"}"})
  void rejectsInvalidReviewRequests(String body) throws Exception {
    mvc.perform(post(base() + "/architect-review").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    assertTrue(reviews.history(project, rule).isEmpty());
  }

  @Test
  void projectIsolationAndMissingRecordsApplyToAllNewOperations() throws Exception {
    UUID other = projects.register(new ProjectRegistrationRequest("Other", null,
        "https://github.com/example/other", Set.of(ArchitectureStyle.MICROSERVICES))).id();
    for (String prefix : List.of(base().replace(project.toString(), other.toString()),
        base().replace(project.toString(), UUID.randomUUID().toString()),
        base().replace(rule.toString(), UUID.randomUUID().toString()))) {
      for (String suffix : List.of("/structural-validation", "/architect-review", "/architect-review/history")) {
        mvc.perform(get(prefix + suffix)).andExpect(status().isNotFound());
      }
      mvc.perform(post(prefix + "/validate-structure")).andExpect(status().isNotFound());
      mvc.perform(post(prefix + "/architect-review").contentType(MediaType.APPLICATION_JSON)
          .content("{\"decision\":\"CONFIRMED\",\"reviewer\":\"Architect\"}")).andExpect(status().isNotFound());
    }
  }

  @Test
  void databaseAndConcurrencyErrorsAreSafeAndConsistent() throws Exception {
    assurance.saveFailure = new org.springframework.dao.DataAccessResourceFailureException("secret JDBC credentials");
    var failed = mvc.perform(post(base() + "/validate-structure")).andExpect(status().isServiceUnavailable());
    assertFalse(failed.andReturn().getResponse().getContentAsString().contains("secret"));
    assurance.saveFailure = new org.springframework.dao.PessimisticLockingFailureException("secret lock details");
    var concurrent = mvc.perform(post(base() + "/validate-structure")).andExpect(status().isConflict());
    assertFalse(concurrent.andReturn().getResponse().getContentAsString().contains("secret"));
    assertTrue(assurance.validation(rule).isEmpty());
  }

  @Test
  void evidenceFingerprintIsStableAndLengthPrefixingAvoidsAmbiguousConcatenations() {
    assertNotEquals(EvidenceFingerprints.hash("ab", "c"), EvidenceFingerprints.hash("a", "bc"));
    assertNotEquals(EvidenceFingerprints.hash((Object) null), EvidenceFingerprints.hash("null"));
    assertEquals(64, reviews.find(project, rule).evidenceFingerprint().length());
    assertEquals(reviews.find(project, rule).evidenceFingerprint(), reviews.find(project, rule).evidenceFingerprint());
  }
}
