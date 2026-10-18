package akegp.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import akegp.rules.api.CreateADRRequest;
import akegp.rules.api.ProjectRegistrationExceptionHandler;
import akegp.rules.api.RuleController;
import akegp.rules.domain.ADRStatus;
import akegp.rules.repository.InMemoryADRRepository;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.repository.InMemoryRuleRepository;
import akegp.rules.service.ADRServiceImpl;
import akegp.rules.service.ProjectRegistrationServiceImpl;
import akegp.rules.service.RuleServiceImpl;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuleControllerTest {
  private MockMvc mockMvc;
  private UUID projectId;
  private UUID adrId;

  @BeforeEach
  void setUp() {
    var projectService = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    projectId =
        projectService
            .register(
                new akegp.rules.api.ProjectRegistrationRequest(
                    "Project", null, "https://github.com/example/project-" + UUID.randomUUID(),
                    Set.of(akegp.rules.domain.ArchitectureStyle.LAYERED)))
            .id();
    var adrService = new ADRServiceImpl(new InMemoryADRRepository(), projectService);
    adrId =
        adrService
            .create(
                projectId,
                new CreateADRRequest(
                    "ADR-001", "Choose service boundaries", ADRStatus.PROPOSED,
                    "The system needs clear service boundaries.",
                    "We will define boundaries by business capability.",
                    "Teams will own services and contracts."))
            .id();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RuleController(
                    new RuleServiceImpl(new InMemoryRuleRepository(), projectService, adrService)))
            .setControllerAdvice(new ProjectRegistrationExceptionHandler())
            .build();
  }

  @Test
  void createsRetrievesUpdatesAndDeletesRule() throws Exception {
    String request =
        """
        {
          "ruleId": "R-001",
          "adrId": "%s",
          "sourceElement": "OrderService",
          "sourceElementType": "SERVICE",
          "relationshipType": "DIRECT_DATABASE_ACCESS",
          "targetElement": "PaymentDB",
          "targetElementType": "DATABASE",
          "decision": "FORBIDDEN",
          "description": "OrderService must not directly access PaymentDB.",
          "severity": "HIGH"
        }
        """.formatted(adrId);

    var result =
        mockMvc
            .perform(
                post("/api/rules/projects/{projectId}/rules", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ruleId").value("R-001"))
            .andExpect(jsonPath("$.adrDisplayId").value("ADR-001"))
            .andReturn();
    String ruleUuid =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/api/rules/projects/{projectId}/rules", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ruleId").value("R-001"));

    mockMvc
        .perform(
            put("/api/rules/projects/{projectId}/rules/{ruleUuid}", projectId, ruleUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request.replace("R-001", "R-002")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleId").value("R-002"));

    mockMvc
        .perform(delete("/api/rules/projects/{projectId}/rules/{ruleUuid}", projectId, ruleUuid))
        .andExpect(status().isNoContent());
  }

  @Test
  void rejectsInvalidRuleAndDuplicateRuleId() throws Exception {
    String request =
        """
        {
          "ruleId": "bad",
          "adrId": "%s",
          "sourceElement": "OrderService",
          "sourceElementType": "SERVICE",
          "relationshipType": "CALLS",
          "targetElement": "PaymentDB",
          "targetElementType": "DATABASE",
          "decision": "FORBIDDEN",
          "description": "A valid description.",
          "severity": "HIGH"
        }
        """.formatted(adrId);
    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/rules", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.ruleId").exists());

    String valid = request.replace("\"bad\"", "\"R-001\"");
    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/rules", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/rules", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isConflict());
  }
}
