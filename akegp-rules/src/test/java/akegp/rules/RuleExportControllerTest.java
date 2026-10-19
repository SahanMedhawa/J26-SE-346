package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import akegp.rules.validation.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleExportControllerTest {
  private static ValidatorFactory validators;
  private InMemoryRuleRepository ruleRepository;
  private InMemoryADRRepository adrRepository;
  private ProjectRegistrationService projects;
  private ADRService adrs;
  private RuleService rules;
  private ImplementationMappingControllerTest.MemoryMappings mappingRepository;
  private ImplementationMappingService mappings;
  private TestRuleAssuranceRepository assurance;
  private StoredStructuralValidationService validations;
  private ArchitectReviewService reviews;
  private RuleExportService exports;
  private MockMvc mvc;
  private UUID project;
  private UUID adr;
  private UUID rule;

  @BeforeAll static void validator() { validators = Validation.buildDefaultValidatorFactory(); }
  @AfterAll static void closeValidator() { validators.close(); }

  @BeforeEach
  void setUp() {
    projects = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    ruleRepository = new InMemoryRuleRepository();
    adrRepository = new InMemoryADRRepository();
    var ids = new TestReadableIdRepository();
    adrs = new ADRServiceImpl(adrRepository, projects, ids);
    rules = new RuleServiceImpl(ruleRepository, projects, adrs, ids);
    mappingRepository = new ImplementationMappingControllerTest.MemoryMappings(ruleRepository);
    mappings = new ImplementationMappingServiceImpl(mappingRepository, ruleRepository, projects);
    assurance = spy(new TestRuleAssuranceRepository());
    var evidence = new RuleEvidenceService(projects, ruleRepository, adrs, mappingRepository, assurance);
    var catalogue = new ArvmCatalogue();
    validations = new StoredStructuralValidationService(new RuleStructureValidationService(
        ruleRepository, projects, adrs, validators.getValidator(), catalogue), evidence, assurance, catalogue);
    reviews = new ArchitectReviewService(evidence, validations, assurance);
    exports = new RuleExportService(projects, ruleRepository, evidence, reviews);
    mvc = MockMvcBuilders.standaloneSetup(new RuleExportController(exports))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler(), new RulePersistenceExceptionHandler()).build();
    project = project("HealthMate", Set.of(ArchitectureStyle.MICROSERVICES));
    adr = adr(project, ADRStatus.ACCEPTED);
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
  }

  private UUID project(String name, Set<ArchitectureStyle> styles) {
    return projects.register(new ProjectRegistrationRequest(name, "Project notes",
        "https://github.com/example/" + UUID.randomUUID() + ".git", styles)).id();
  }
  private UUID adr(UUID p, ADRStatus status) {
    return adrs.create(p, new CreateADRRequest(null, "Architecture decision", status,
        "Decision context", "Documented decision", "Documented consequences")).id();
  }
  private void prepare(UUID p, UUID r) {
    validations.validate(p, r, ArchitectureStyle.MICROSERVICES);
    mappings.replace(p, r, new ImplementationMappingsRequest(List.of("src/Source.java"), List.of("src/Target.java")));
  }
  private void confirm(UUID p, UUID r) {
    reviews.submit(p, r, new ArchitectReviewRequest(ArchitectReviewDecision.CONFIRMED, "Architect", "Evidence reviewed",
        reviews.find(p, r).evidenceFingerprint()));
  }
  private String endpoint(UUID p) { return "/api/rules/projects/" + p + "/export"; }
  private void assertEmpty() throws Exception {
    mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleCount").value(0)).andExpect(jsonPath("$.rules").isEmpty());
  }

  @Test
  void exportsTheRequiredSchemaFromSavedValuesIncludingForbiddenSupportedSignature() throws Exception {
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, adr, "AppointmentService", ArchitectureElementType.SERVICE,
        RelationshipType.DIRECT_DATABASE_ACCESS, "PatientDB", ArchitectureElementType.DATABASE,
        RuleDecision.FORBIDDEN, r.description(), RuleSeverity.HIGH));
    prepare(project, rule);
    confirm(project, rule);
    var savedProject = projects.findById(project);
    var savedRule = rules.findById(project, rule);
    var savedAdr = adrs.findById(project, adr);
    var result = mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(project.toString()))
        .andExpect(jsonPath("$.projectName").value(savedProject.name()))
        .andExpect(jsonPath("$.repositoryUrl").value(savedProject.repositoryUrl()))
        .andExpect(jsonPath("$.architectureStyle").value("MICROSERVICES"))
        .andExpect(jsonPath("$.ruleCount").value(1))
        .andExpect(jsonPath("$.rules[0].ruleId").value(savedRule.ruleId()))
        .andExpect(jsonPath("$.rules[0].adrId").value(savedAdr.adrId()))
        .andExpect(jsonPath("$.rules[0].sourceElement").value(savedRule.sourceElement()))
        .andExpect(jsonPath("$.rules[0].sourceElementType").value("SERVICE"))
        .andExpect(jsonPath("$.rules[0].relationshipType").value("DIRECT_DATABASE_ACCESS"))
        .andExpect(jsonPath("$.rules[0].targetElement").value(savedRule.targetElement()))
        .andExpect(jsonPath("$.rules[0].targetElementType").value("DATABASE"))
        .andExpect(jsonPath("$.rules[0].decision").value("FORBIDDEN"))
        .andExpect(jsonPath("$.rules[0].severity").value("HIGH"))
        .andExpect(jsonPath("$.rules[0].validationStatus").value("SUPPORTED"))
        .andExpect(jsonPath("$.rules[0].architectReviewStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.rules[0].sourceFiles[0]").value("src/Source.java"))
        .andExpect(jsonPath("$.rules[0].targetFiles[0]").value("src/Target.java"))
        .andExpect(jsonPath("$.rules[0].structuralValidationStatus").doesNotExist()).andReturn();
    Map<String, Object> body = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$");
    assertEquals(Set.of("projectId", "projectName", "repositoryUrl", "architectureStyle", "ruleCount", "rules"), body.keySet());
    Map<String, Object> exported = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.rules[0]");
    assertEquals(Set.of("ruleId", "adrId", "sourceElement", "sourceElementType", "relationshipType", "targetElement",
        "targetElementType", "decision", "severity", "validationStatus", "architectReviewStatus", "sourceFiles", "targetFiles"), exported.keySet());
  }

  @Test
  void excludesPendingReviewEvenWhenConfirmationEligible() throws Exception {
    prepare(project, rule);
    assertTrue(reviews.find(project, rule).confirmationEligible());
    assertEmpty();
  }

  @Test
  void latestChangesRequestedSupersedesAnEarlierConfirmation() throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    reviews.submit(project, rule, new ArchitectReviewRequest(ArchitectReviewDecision.CHANGES_REQUESTED,
        "Another Architect", "Please clarify the target interpretation.", null));
    assertEmpty();
  }

  @Test
  void excludesNeverValidatedAndUnsupportedRules() throws Exception {
    assertEmpty();
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, adr, r.sourceElement(), ArchitectureElementType.DATABASE,
        r.relationshipType(), r.targetElement(), r.targetElementType(), r.decision(), r.description(), r.severity()));
    prepare(project, rule);
    assertEquals(StructuralValidationStatus.UNSUPPORTED, validations.find(project, rule).status());
    assertEmpty();
  }

  @Test
  void excludesStaleValidationAndPreviouslyConfirmedStructuralEdits() throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    var r = rules.findById(project, rule);
    rules.update(project, rule, new UpdateRuleRequest(null, adr, r.sourceElement(), r.sourceElementType(),
        RelationshipType.DEPENDS_ON, r.targetElement(), r.targetElementType(), r.decision(), r.description(), r.severity()));
    assertFalse(validations.find(project, rule).current());
    assertEmpty();
    validations.validate(project, rule, null);
    assertEquals(StructuralValidationStatus.SUPPORTED, validations.find(project, rule).status());
    assertEmpty(); // Revalidation alone does not renew the old human confirmation.
  }

  @ParameterizedTest
  @ValueSource(strings = {"source", "target", "decision", "description", "adrContent", "adrLink", "mappings", "revision"})
  void excludesConfirmationsAfterRelevantEvidenceChanges(String change) throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    var r = rules.findById(project, rule);
    switch (change) {
      case "adrContent" -> {
        var a = adrs.findById(project, adr);
        adrs.update(project, adr, new UpdateADRRequest(null, a.title(), a.status(), a.context(), "Revised decision", a.consequences(), a.version()));
      }
      case "mappings" -> mappings.replace(project, rule,
          new ImplementationMappingsRequest(List.of("src/Changed.java"), List.of("src/Target.java")));
      case "revision" -> assurance.advance(rule, false);
      default -> rules.update(project, rule, new UpdateRuleRequest(null,
          change.equals("adrLink") ? adr(project, ADRStatus.ACCEPTED) : adr,
          change.equals("source") ? "Changed source" : r.sourceElement(), r.sourceElementType(), r.relationshipType(),
          change.equals("target") ? "Changed target" : r.targetElement(), r.targetElementType(),
          change.equals("decision") ? RuleDecision.REQUIRED : r.decision(),
          change.equals("description") ? "Changed interpretation" : r.description(), r.severity()));
    }
    assertEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"source", "target", "both"})
  void missingMappingSidesExcludePreviouslyConfirmedRule(String missing) throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    mappings.replace(project, rule, new ImplementationMappingsRequest(
        missing.equals("target") ? List.of("src/Source.java") : List.of(),
        missing.equals("source") ? List.of("src/Target.java") : List.of()));
    assertEmpty();
  }

  @Test
  void returnsMultipleEligibleRulesAndAccurateCountExcludingOtherCandidates() throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    UUID second = rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id();
    prepare(project, second);
    confirm(project, second);
    rules.create(project, ReadableIdServiceTest.ruleRequest(adr)); // Pending candidate must not affect the count.
    var result = exports.export(project);
    assertEquals(2, result.ruleCount());
    assertEquals(result.rules().size(), result.ruleCount());
    assertEquals(Set.of(rules.findById(project, rule).ruleId(), rules.findById(project, second).ruleId()),
        result.rules().stream().map(ExportedRuleResponse::ruleId).collect(java.util.stream.Collectors.toSet()));
    mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleCount").value(2)).andExpect(jsonPath("$.rules.length()").value(2));
  }

  @Test
  void neverIncludesRulesFromAnotherProjectEvenWithIdenticalReadableIds() throws Exception {
    UUID other = project("Other", Set.of(ArchitectureStyle.MICROSERVICES));
    UUID otherAdr = adr(other, ADRStatus.ACCEPTED);
    UUID otherRule = rules.create(other, ReadableIdServiceTest.ruleRequest(otherAdr)).id();
    prepare(other, otherRule);
    confirm(other, otherRule);
    assertEquals(rules.findById(project, rule).ruleId(), rules.findById(other, otherRule).ruleId());
    assertEmpty();
    assertEquals(1, exports.export(other).ruleCount());
  }

  @ParameterizedTest
  @EnumSource(value = ADRStatus.class, names = {"DEPRECATED", "SUPERSEDED"})
  void excludesNonApplicableAdrEvenIfItsCurrentEvidenceWasExplicitlyConfirmed(ADRStatus status) throws Exception {
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), status, a.context(), a.decision(), a.consequences(), a.version()));
    prepare(project, rule);
    confirm(project, rule); // Retired ADRs stay excluded even if their current evidence was reviewed.
    assertEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = ADRStatus.class, names = {"PROPOSED", "ACCEPTED"})
  void exportsCurrentlyConfirmedRulesWithEitherActiveAdrStatus(ADRStatus status) throws Exception {
    var a = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, a.title(), status, a.context(), a.decision(), a.consequences(), a.version()));
    prepare(project, rule);
    confirm(project, rule);
    mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleCount").value(1))
        .andExpect(jsonPath("$.rules[0].validationStatus").value("SUPPORTED"))
        .andExpect(jsonPath("$.rules[0].architectReviewStatus").value("CONFIRMED"));
    assertEquals(status, adrs.findById(project, adr).status());
  }

  @Test
  void exportsAllFourHealthMateShapedRulesWithProposedAdrsWithoutChangingTheirEvidence() throws Exception {
    var firstAdr = adrs.findById(project, adr);
    adrs.update(project, adr, new UpdateADRRequest(null, firstAdr.title(), ADRStatus.PROPOSED,
        firstAdr.context(), firstAdr.decision(), firstAdr.consequences(), firstAdr.version()));
    UUID secondAdr = adr(project, ADRStatus.PROPOSED);
    var fourRules = List.of(rule, rules.create(project, ReadableIdServiceTest.ruleRequest(adr)).id(),
        rules.create(project, ReadableIdServiceTest.ruleRequest(secondAdr)).id(),
        rules.create(project, ReadableIdServiceTest.ruleRequest(secondAdr)).id());
    for (UUID candidate : fourRules) { prepare(project, candidate); confirm(project, candidate); }
    var priorReviews = fourRules.stream().map(assurance::reviews).toList();
    var priorValidations = fourRules.stream().map(assurance::validation).toList();
    mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleCount").value(4)).andExpect(jsonPath("$.rules.length()").value(4));
    assertEquals(Set.of("R-001", "R-002", "R-003", "R-004"), exports.export(project).rules().stream()
        .map(ExportedRuleResponse::ruleId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(ADRStatus.PROPOSED, adrs.findById(project, adr).status());
    assertEquals(ADRStatus.PROPOSED, adrs.findById(project, secondAdr).status());
    assertEquals(priorReviews, fourRules.stream().map(assurance::reviews).toList());
    assertEquals(priorValidations, fourRules.stream().map(assurance::validation).toList());
  }

  @Test
  void missingAdrAndDeletedRulesAreExcluded() throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    adrRepository.delete(project, adr); // Simulate missing evidence; the production FK normally prevents this.
    assertEmpty();
    ruleRepository.delete(project, rule);
    assertEmpty();
  }

  @Test
  void unknownProjectReturns404AndMalformedUuidReturns400() throws Exception {
    mvc.perform(get(endpoint(UUID.randomUUID()))).andExpect(status().isNotFound());
    mvc.perform(get("/api/rules/projects/not-a-uuid/export")).andExpect(status().isBadRequest());
  }

  @Test
  void projectWithNoRulesReturnsMetadataAndEmptyArray() throws Exception {
    ruleRepository.delete(project, rule);
    mvc.perform(get(endpoint(project))).andExpect(status().isOk())
        .andExpect(jsonPath("$.projectName").value(projects.findById(project).name()))
        .andExpect(jsonPath("$.ruleCount").value(0)).andExpect(jsonPath("$.rules").isEmpty());
  }

  @Test
  void inactiveProjectDoesNotExportPreviouslyConfirmedRules() throws Exception {
    prepare(project, rule);
    confirm(project, rule);
    projects.updateStatus(project, new ProjectStatusUpdateRequest(ProjectStatus.INACTIVE));
    assertEmpty();
  }

  @Test
  void mappingStringsAndRepositoryOrderArePreservedWithoutNormalizationOrSorting() {
    validations.validate(project, rule, null);
    for (var path : List.of("src/Z.java", "src/A file.java")) {
      mappingRepository.insert(new RuleImplementationMapping(UUID.randomUUID(), rule, ImplementationMappingSide.SOURCE, path));
    }
    for (var path : List.of("resources/z.sql", "resources/a.sql")) {
      mappingRepository.insert(new RuleImplementationMapping(UUID.randomUUID(), rule, ImplementationMappingSide.TARGET, path));
    }
    confirm(project, rule);
    var result = exports.export(project).rules().getFirst();
    assertEquals(List.of("src/Z.java", "src/A file.java"), result.sourceFiles());
    assertEquals(List.of("resources/z.sql", "resources/a.sql"), result.targetFiles());
  }

  @Test
  void exportDoesNotRevalidateAppendReviewsModifyMappingsOrTakeWriteLocks() {
    prepare(project, rule);
    confirm(project, rule);
    var savedValidation = assurance.validation(rule).orElseThrow();
    var savedHistory = assurance.reviews(rule);
    var savedMappings = mappingRepository.findByRule(rule);
    clearInvocations(assurance);
    assertEquals(exports.export(project), exports.export(project));
    verify(assurance, never()).lockProject(any());
    verify(assurance, never()).lockRule(any(), any());
    verify(assurance, never()).lockAdr(any(), any());
    verify(assurance, never()).saveValidation(any());
    verify(assurance, never()).appendReview(any());
    assertEquals(savedValidation, assurance.validation(rule).orElseThrow());
    assertEquals(savedHistory, assurance.reviews(rule));
    assertEquals(savedMappings, mappingRepository.findByRule(rule));
  }

  @ParameterizedTest
  @ValueSource(strings = {"combined", "both", "layered", "none"})
  void representsStoredProjectStylesWithoutArbitrarilyChoosingOne(String styles) {
    Set<ArchitectureStyle> stored = switch (styles) {
      case "combined" -> Set.of(ArchitectureStyle.MICROSERVICES_AND_LAYERED);
      case "both" -> Set.of(ArchitectureStyle.MICROSERVICES, ArchitectureStyle.LAYERED);
      case "layered" -> Set.of(ArchitectureStyle.LAYERED);
      default -> Set.of();
    };
    UUID p = project("Styles", stored);
    var exported = exports.export(p);
    assertEquals(stored.isEmpty() ? null : styles.equals("layered") ? ArchitectureStyle.LAYERED
        : ArchitectureStyle.MICROSERVICES_AND_LAYERED, exported.architectureStyle());
  }
}
