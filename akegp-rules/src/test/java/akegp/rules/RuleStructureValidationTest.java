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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleStructureValidationTest {
  private final InMemoryRuleRepository rules = new InMemoryRuleRepository();
  private final ProjectRegistrationServiceImpl projects =
      new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
  private final ADRServiceImpl adrs =
      new ADRServiceImpl(new InMemoryADRRepository(), projects, new TestReadableIdRepository());
  private final ArvmCatalogue catalogue = new ArvmCatalogue();
  private ValidatorFactory factory;
  private RuleStructureValidationService service;
  private MockMvc mvc;
  private UUID projectId;
  private UUID adrId;

  @BeforeEach
  void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    service = new RuleStructureValidationService(rules, projects, adrs, factory.getValidator(), catalogue);
    mvc = MockMvcBuilders.standaloneSetup(
        new RuleStructureValidationController(service, catalogue),
        new RuleController(new RuleServiceImpl(rules, projects, adrs, new TestReadableIdRepository())))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler()).build();
    register(Set.of(ArchitectureStyle.MICROSERVICES));
  }

  @AfterEach
  void closeValidator() {
    factory.close();
  }

  private void register(Set<ArchitectureStyle> styles) {
    projectId = projects.register(new ProjectRegistrationRequest(
        "Project", null, "https://github.com/example/" + UUID.randomUUID(), styles)).id();
    adrId = adrs.create(projectId, new CreateADRRequest(
        null, "Architecture", ADRStatus.ACCEPTED, "Context", "Decision", "Consequences")).id();
  }

  private ArchitectureRule save(String source, String relationship, String target, RuleDecision decision) {
    var now = Instant.now();
    return rules.insert(new ArchitectureRule(
        UUID.randomUUID(), "R-001", projectId, adrId, "Source",
        ArchitectureElementType.valueOf(source), RelationshipType.valueOf(relationship), "Target",
        ArchitectureElementType.valueOf(target), decision, "Description", RuleSeverity.HIGH, now, now));
  }

  @ParameterizedTest
  @CsvSource({
      "MICROSERVICES,SERVICE,CALLS,SERVICE,M01",
      "MICROSERVICES,SERVICE,DIRECT_DATABASE_ACCESS,DATABASE,M08",
      "MICROSERVICES,SERVICE,PUBLISHES_TO,MESSAGE_BROKER,M10",
      "LAYERED,CONTROLLER,CALLS,SERVICE_LAYER,L01",
      "LAYERED,REPOSITORY,DATA_ACCESS,DATABASE,L05",
      "LAYERED,CONTROLLER,DIRECT_DATABASE_ACCESS,DATABASE,L10"
  })
  void supportedSignaturesAndDecisions(
      ArchitectureStyle style, String source, String relationship, String target, String signature) {
    register(Set.of(style));
    for (var decision : RuleDecision.values()) {
      var rule = save(source, relationship, target, decision);
      var result = service.validate(projectId, rule.id(), null);
      assertEquals(StructuralValidationStatus.SUPPORTED, result.validationStatus());
      assertEquals(signature, result.matchedSignatureId());
      assertEquals(decision, result.decision());
      assertEquals(rule, rules.findById(projectId, rule.id()).orElseThrow());
    }
  }

  @ParameterizedTest
  @CsvSource({
      "MICROSERVICES,DATABASE,CALLS,SERVICE",
      "MICROSERVICES,API_GATEWAY,DATA_ACCESS,DATABASE",
      "LAYERED,CONTROLLER,PUBLISHES_TO,MESSAGE_BROKER"
  })
  void unsupportedIsSuccessful(
      ArchitectureStyle style, String source, String relationship, String target) throws Exception {
    register(Set.of(style));
    var rule = save(source, relationship, target, RuleDecision.FORBIDDEN);
    mvc.perform(post(path(rule.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("UNSUPPORTED"))
        .andExpect(jsonPath("$.matchedSignatureId").isEmpty())
        .andExpect(jsonPath("$.ruleUuid").value(rule.id().toString()));
  }

  @Test
  void catalogueHasExactlyThirtyUniqueSignaturesAndMatchesEveryDefinition() throws Exception {
    assertEquals(30, catalogue.signatures(null).size());
    assertEquals(30, catalogue.signatures(null).stream().map(ArvmSignature::signatureId).distinct().count());
    for (var style : new ArchitectureStyle[]{ArchitectureStyle.MICROSERVICES, ArchitectureStyle.LAYERED}) {
      assertEquals(15, catalogue.signatures(style).size());
      register(Set.of(style));
      for (var signature : catalogue.signatures(style)) {
        var rule = save(signature.sourceElementType().name(), signature.relationshipType().name(),
            signature.targetElementType().name(), RuleDecision.REQUIRED);
        assertEquals(signature.signatureId(), service.validate(projectId, rule.id(), null).matchedSignatureId());
      }
    }
    mvc.perform(get("/api/rules/arvm/signatures")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(30));
    mvc.perform(get("/api/rules/arvm/signatures").param("architectureStyle", "LAYERED"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15));
    mvc.perform(get("/api/rules/arvm/signatures").param("architectureStyle", "RANDOM"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/rules/arvm/signatures").param("architectureStyle", "MICROSERVICES_AND_LAYERED"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void multiStyleRequiresExplicitSelection(boolean combined) throws Exception {
    register(combined ? Set.of(ArchitectureStyle.MICROSERVICES_AND_LAYERED)
        : Set.of(ArchitectureStyle.MICROSERVICES, ArchitectureStyle.LAYERED));
    var rule = save("SERVICE", "CALLS", "SERVICE", RuleDecision.ALLOWED);
    mvc.perform(post(path(rule.id()))).andExpect(status().isBadRequest());
    mvc.perform(post(path(rule.id())).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path(rule.id())).contentType(MediaType.APPLICATION_JSON)
        .content("{\"architectureStyle\":\"MICROSERVICES\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.matchedSignatureId").value("M01"));
    mvc.perform(post(path(rule.id())).contentType(MediaType.APPLICATION_JSON)
        .content("{\"architectureStyle\":\"LAYERED\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.validationStatus").value("UNSUPPORTED"));
  }

  @Test
  void invalidAndUnregisteredStylesReturnBadRequest() throws Exception {
    var rule = save("SERVICE", "CALLS", "SERVICE", RuleDecision.ALLOWED);
    for (var style : new String[]{"RANDOM", "MICROSERVICES_AND_LAYERED", "LAYERED", ""}) {
      mvc.perform(post(path(rule.id())).contentType(MediaType.APPLICATION_JSON)
          .content("{\"architectureStyle\":\"" + style + "\"}"))
          .andExpect(status().isBadRequest());
    }
    register(Set.of());
    rule = save("SERVICE", "CALLS", "SERVICE", RuleDecision.ALLOWED);
    mvc.perform(post(path(rule.id()))).andExpect(status().isBadRequest());
  }

  @Test
  void missingProjectRuleAndCrossProjectReturnNotFound() throws Exception {
    var rule = save("SERVICE", "CALLS", "SERVICE", RuleDecision.ALLOWED);
    mvc.perform(post(path(UUID.randomUUID()))).andExpect(status().isNotFound());
    mvc.perform(post("/api/rules/projects/{p}/rules/{r}/validate-structure",
        UUID.randomUUID(), rule.id())).andExpect(status().isNotFound());
    register(Set.of(ArchitectureStyle.LAYERED));
    mvc.perform(post(path(rule.id()))).andExpect(status().isNotFound());
  }

  @Test
  void validatesRuleSavedThroughCrudWithoutChangingIt() throws Exception {
    String json = """
        {"adrId":"%s","sourceElement":"Order","sourceElementType":"SERVICE",
        "relationshipType":"DIRECT_DATABASE_ACCESS","targetElement":"Orders","targetElementType":"DATABASE",
        "decision":"FORBIDDEN","description":"No direct database access","severity":"HIGH"}
        """.formatted(adrId);
    var created = mvc.perform(post("/api/rules/projects/{p}/rules", projectId)
        .contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isCreated()).andReturn();
    String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    var saved = rules.findById(projectId, UUID.fromString(id)).orElseThrow();
    mvc.perform(post(path(saved.id()))).andExpect(status().isOk())
        .andExpect(jsonPath("$.validationStatus").value("SUPPORTED"))
        .andExpect(jsonPath("$.matchedSignatureId").value("M08"));
    assertEquals(saved, rules.findById(projectId, saved.id()).orElseThrow());
  }

  @Test
  void invalidSavedFieldsAndMissingAdrAreRejected() throws Exception {
    var rule = save("SERVICE", "CALLS", "SERVICE", RuleDecision.ALLOWED);
    rules.update(new ArchitectureRule(rule.id(), "bad", projectId, adrId, " ", null,
        null, "", null, null, "", null, rule.createdAt(), rule.updatedAt()));
    mvc.perform(post(path(rule.id()))).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("required information")));
    rules.update(new ArchitectureRule(rule.id(), "R-001", projectId, UUID.randomUUID(), "Source",
        ArchitectureElementType.SERVICE, RelationshipType.CALLS, "Target",
        ArchitectureElementType.SERVICE, RuleDecision.ALLOWED, "Description", RuleSeverity.HIGH,
        rule.createdAt(), rule.updatedAt()));
    mvc.perform(post(path(rule.id()))).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource({"relationshipType,CONNECTS_RANDOMLY", "sourceElementType,RANDOM",
      "targetElementType,RANDOM", "decision,RANDOM", "severity,RANDOM"})
  void crudRejectsInvalidEnums(String field, String value) throws Exception {
    String json = """
        {"adrId":"%s","sourceElement":"Source","sourceElementType":"SERVICE",
        "relationshipType":"CALLS","targetElement":"Target","targetElementType":"SERVICE",
        "decision":"ALLOWED","description":"Description","severity":"HIGH"}
        """.formatted(adrId);
    json = json.replaceAll("\"" + field + "\":\"[^\"]*\"", "\"" + field + "\":\"" + value + "\"");
    mvc.perform(post("/api/rules/projects/{p}/rules", projectId)
        .contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());
  }

  private String path(UUID id) {
    return "/api/rules/projects/" + projectId + "/rules/" + id + "/validate-structure";
  }
}
