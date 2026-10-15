package akegp.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import akegp.rules.api.ADRController;
import akegp.rules.api.ProjectRegistrationExceptionHandler;
import akegp.rules.repository.InMemoryADRRepository;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.service.ADRServiceImpl;
import akegp.rules.service.ProjectRegistrationServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ADRControllerTest {
  private MockMvc mockMvc;
  private UUID projectId;

  @BeforeEach
  void setUp() {
    var projectService = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    projectId =
        projectService
            .register(
                new akegp.rules.api.ProjectRegistrationRequest(
                    "Project", null, "https://github.com/example/project-" + UUID.randomUUID(),
                    java.util.Set.of(akegp.rules.domain.ArchitectureStyle.LAYERED)))
            .id();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ADRController(new ADRServiceImpl(new InMemoryADRRepository(), projectService)))
            .setControllerAdvice(new ProjectRegistrationExceptionHandler())
            .build();
  }

  @Test
  void createsAndListsADR() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/adrs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "adrId": "ADR-001",
                      "title": "Choose service boundaries",
                      "status": "PROPOSED",
                      "context": "The system needs clear service boundaries.",
                      "decision": "We will define boundaries by business capability.",
                      "consequences": "Teams will own services and contracts."
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.linkedRuleCount").isEmpty())
        .andExpect(jsonPath("$.linkedRuleIds").isArray());

    mockMvc
        .perform(get("/api/rules/projects/{projectId}/adrs", projectId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].adrId").value("ADR-001"));
  }

  @Test
  void rejectsInvalidRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/adrs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adrId\":\"bad\",\"title\":\"x\",\"status\":\"PROPOSED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.adrId").exists());

    mockMvc
        .perform(
            post("/api/rules/projects/{projectId}/adrs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adrId\":\"ADR-001\",\"title\":\"valid title\",\"status\":\"UNKNOWN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Request body contains invalid or unsupported values"));
  }
}
