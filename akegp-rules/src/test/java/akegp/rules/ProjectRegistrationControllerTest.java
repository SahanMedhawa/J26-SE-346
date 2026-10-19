package akegp.rules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import akegp.rules.api.ProjectRegistrationController;
import akegp.rules.api.ProjectRegistrationExceptionHandler;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.service.ProjectRegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectRegistrationControllerTest {
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var service = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProjectRegistrationController(service))
            .setControllerAdvice(new ProjectRegistrationExceptionHandler())
            .build();
  }

  @Test
  void registersProject() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Architecture Platform",
                      "description": "Project description",
                      "repositoryUrl": "https://github.com/example/platform",
                      "architectureStyles": ["MICROSERVICES_AND_LAYERED"]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.architectureStyles[0]").value("MICROSERVICES_AND_LAYERED"));
  }

  @Test
  void rejectsMissingNameAndInvalidUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "repositoryUrl": "not-a-url",
                      "architectureStyles": ["MICROSERVICES"]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.name").exists())
        .andExpect(jsonPath("$.fieldErrors.repositoryUrl").exists());
  }

  @Test
  void rejectsUnsupportedArchitectureStyle() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Architecture Platform",
                      "repositoryUrl": "https://github.com/example/platform",
                      "architectureStyles": ["HEXAGONAL"]
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void retrievesRegisteredProject() throws Exception {
    mockMvc
        .perform(
            post("/api/rules/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Architecture Platform",
                      "repositoryUrl": "https://github.com/example/platform",
                      "architectureStyles": ["LAYERED"]
                    }
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/rules/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Architecture Platform"));
  }

}
