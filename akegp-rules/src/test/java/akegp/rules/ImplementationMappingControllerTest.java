package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.service.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ImplementationMappingControllerTest {
  private MockMvc mvc;
  private UUID project;
  private UUID rule;
  private UUID otherProject;
  private UUID otherRule;
  private UUID siblingRule;
  private MemoryMappings mappings;

  @BeforeEach
  void setUp() {
    var projects = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    var ids = new TestReadableIdRepository();
    var adrs = new ADRServiceImpl(new InMemoryADRRepository(), projects, ids);
    var repository = new InMemoryRuleRepository();
    var rules = new RuleServiceImpl(repository, projects, adrs, ids);
    project = projects.register(new ProjectRegistrationRequest("First", null,
        "https://example.com/first", Set.of(ArchitectureStyle.LAYERED))).id();
    otherProject = projects.register(new ProjectRegistrationRequest("Second", null,
        "https://example.com/second", Set.of(ArchitectureStyle.LAYERED))).id();
    rule = rules.create(project, ReadableIdServiceTest.ruleRequest(
        adrs.create(project, ReadableIdServiceTest.adrRequest()).id())).id();
    siblingRule = rules.create(project, ReadableIdServiceTest.ruleRequest(
        rules.findById(project, rule).adrId())).id();
    otherRule = rules.create(otherProject, ReadableIdServiceTest.ruleRequest(
        adrs.create(otherProject, ReadableIdServiceTest.adrRequest()).id())).id();
    assertEquals("R-001", rules.findById(project, rule).ruleId());
    assertEquals("R-001", rules.findById(otherProject, otherRule).ruleId());
    mappings = new MemoryMappings(repository);
    mvc = MockMvcBuilders.standaloneSetup(new ImplementationMappingController(
        new ImplementationMappingServiceImpl(mappings, repository, projects)))
        .setControllerAdvice(new ProjectRegistrationExceptionHandler()).build();
  }

  private String url(UUID projectId, UUID ruleId) {
    return "/api/rules/projects/" + projectId + "/rules/" + ruleId + "/implementation-mappings";
  }

  private ResultActions save(String body) throws Exception {
    return mvc.perform(put(url(project, rule)).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void savesOneSourceAndTargetAndRetrievesThem() throws Exception {
    save(""" 
        {"sourceFiles":["src/Order.java"],"targetFiles":["db/V1.sql"]}
        """).andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleUuid").value(rule.toString()))
        .andExpect(jsonPath("$.sourceFiles[0]").value("src/Order.java"))
        .andExpect(jsonPath("$.targetFiles[0]").value("db/V1.sql"));
    mvc.perform(get(url(project, rule))).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("src/Order.java"))
        .andExpect(jsonPath("$.targetFiles[0]").value("db/V1.sql"));
  }

  @Test
  void savesMultipleFilesUpdatesRemovesAndClears() throws Exception {
    save("""
        {"sourceFiles":["src/A.java","src/B.java"],"targetFiles":["db/A.sql","db/B.sql"]}
        """).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles.length()").value(2))
        .andExpect(jsonPath("$.targetFiles.length()").value(2));
    var retained = mappings.findByRule(rule).stream()
        .filter(m -> m.filePath().equals("src/B.java")).findFirst().orElseThrow();
    save("""
        {"sourceFiles":["src/B.java"],"targetFiles":["db/A.sql","db/B.sql"]}
        """).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("src/B.java"))
        .andExpect(jsonPath("$.sourceFiles.length()").value(1))
        .andExpect(jsonPath("$.targetFiles.length()").value(2));
    assertTrue(mappings.findByRule(rule).contains(retained));
    save("""
        {"sourceFiles":["src/Edited.java"],"targetFiles":["db/B.sql"]}
        """).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("src/Edited.java"))
        .andExpect(jsonPath("$.targetFiles.length()").value(1));
    save("{\"sourceFiles\":[],\"targetFiles\":[]}").andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles").isEmpty()).andExpect(jsonPath("$.targetFiles").isEmpty());
    assertTrue(mappings.findByRule(rule).isEmpty());
    mvc.perform(get(url(project, rule))).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles").isEmpty()).andExpect(jsonPath("$.targetFiles").isEmpty());
  }

  @Test
  void repeatedPutPreservesRowsAndAllowsSamePathOnDifferentSides() throws Exception {
    String body = "{\"sourceFiles\":[\"src/A.java\"],\"targetFiles\":[\"src/A.java\"]}";
    save(body).andExpect(status().isOk());
    var before = mappings.findByRule(rule);
    save(body).andExpect(status().isOk());
    assertEquals(before, mappings.findByRule(rule));
    assertEquals(2, before.size());
  }

  @Test
  void normalizesSeparatorsWhitespaceAndDotSegments() throws Exception {
    save("""
        {"sourceFiles":["  src\\\\main\\\\A.java  "],"targetFiles":["./db//V1.sql"]}
        """).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("src/main/A.java"))
        .andExpect(jsonPath("$.targetFiles[0]").value("db/V1.sql"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "null", "\"\"", "\"   \"", "\"/etc/passwd\"", "\"C:/src/A.java\"",
      "\"C:src/A.java\"", "\"C:\\\\src\\\\A.java\"", "\"\\\\\\\\host\\\\share\\\\A.java\"",
      "\"\\\\rooted.java\"", "\"../A.java\"", "\"src/../A.java\"", "\"src\\\\..\\\\A.java\"",
      "\".\"", "\"src/\"", "\"file:///etc/passwd\"", "\"src/A\\u0000.java\""
  })
  void rejectsInvalidPathsOnEitherSideWithoutChangingSavedMappings(String path) throws Exception {
    save("{\"sourceFiles\":[\"saved.java\"],\"targetFiles\":[]}").andExpect(status().isOk());
    var before = mappings.findByRule(rule);
    save("{\"sourceFiles\":[" + path + "],\"targetFiles\":[]}")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    save("{\"sourceFiles\":[],\"targetFiles\":[" + path + "]}")
        .andExpect(status().isBadRequest());
    assertEquals(before, mappings.findByRule(rule));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"sourceFiles\":[\"a.java\",\"a.java\"],\"targetFiles\":[]}",
      "{\"sourceFiles\":[],\"targetFiles\":[\"a.java\",\"a.java\"]}",
      "{\"sourceFiles\":[\"src/a.java\",\"src\\\\a.java\"],\"targetFiles\":[]}",
      "{\"sourceFiles\":[\"src/a.java\",\"./src//a.java\"],\"targetFiles\":[]}",
      "{\"sourceFiles\":null,\"targetFiles\":[]}", "{\"sourceFiles\":[]}", "{}"
  })
  void rejectsDuplicatesAndMissingArrays(String body) throws Exception {
    save(body).andExpect(status().isBadRequest());
    assertTrue(mappings.findByRule(rule).isEmpty());
  }

  @Test
  void rejectsMissingProjectRuleAndWrongProjectOnGetAndPut() throws Exception {
    for (String endpoint : List.of(url(UUID.randomUUID(), rule), url(project, UUID.randomUUID()),
        url(otherProject, rule))) {
      mvc.perform(get(endpoint)).andExpect(status().isNotFound());
      mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON)
          .content("{\"sourceFiles\":[],\"targetFiles\":[]}")).andExpect(status().isNotFound());
    }
  }

  @Test
  void isolatesRulesWithIdenticalReadableIdsAcrossProjects() throws Exception {
    mvc.perform(put(url(otherProject, otherRule)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceFiles\":[\"other.java\"],\"targetFiles\":[]}")).andExpect(status().isOk());
    save("{\"sourceFiles\":[\"first.java\"],\"targetFiles\":[]}").andExpect(status().isOk());
    save("{\"sourceFiles\":[],\"targetFiles\":[]}").andExpect(status().isOk());
    mvc.perform(get(url(otherProject, otherRule))).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("other.java"));
  }

  @Test
  void isolatesRulesWithinTheSameProject() throws Exception {
    mvc.perform(put(url(project, siblingRule)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"sourceFiles\":[\"sibling.java\"],\"targetFiles\":[]}")).andExpect(status().isOk());
    save("{\"sourceFiles\":[\"first.java\"],\"targetFiles\":[]}").andExpect(status().isOk());
    save("{\"sourceFiles\":[],\"targetFiles\":[]}").andExpect(status().isOk());
    mvc.perform(get(url(project, siblingRule))).andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceFiles[0]").value("sibling.java"));
  }

  @Test
  void rejectsMalformedBodiesAndUuid() throws Exception {
    save("{").andExpect(status().isBadRequest());
    mvc.perform(get(url(project, rule).replace(rule.toString(), "invalid")))
        .andExpect(status().isBadRequest());
  }

  // A test double only; production always uses PostgreSQL.
  static class MemoryMappings implements ImplementationMappingRepository {
    private final RuleRepository rules;
    private final Map<UUID, RuleImplementationMapping> rows = new LinkedHashMap<>();

    MemoryMappings(RuleRepository rules) { this.rules = rules; }

    public boolean lockRule(UUID projectId, UUID ruleUuid) {
      return rules.findById(projectId, ruleUuid).isPresent();
    }

    public List<RuleImplementationMapping> findByRule(UUID ruleUuid) {
      return rows.values().stream().filter(m -> m.ruleId().equals(ruleUuid)).toList();
    }

    public void insert(RuleImplementationMapping mapping) { rows.put(mapping.id(), mapping); }

    public void delete(UUID ruleUuid, UUID mappingId) {
      rows.computeIfPresent(mappingId, (id, m) -> m.ruleId().equals(ruleUuid) ? null : m);
    }
  }
}
