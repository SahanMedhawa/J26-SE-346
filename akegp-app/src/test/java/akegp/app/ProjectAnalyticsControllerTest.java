package akegp.app;

import akegp.analytics.config.AnalyticsDatabase;
import akegp.analytics.service.ProjectReadAuthorizer;
import akegp.conformance.service.CodeGraphService;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.rules.domain.*;
import akegp.rules.api.RuleExportResponse;
import akegp.rules.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectAnalyticsControllerTest {
  final UUID id = UUID.fromString("1b7b6197-7fe4-4bf3-afe4-7e500d564b81");
  final ProjectRegistrationService projects = mock(ProjectRegistrationService.class);
  final RuleExportService exports = mock(RuleExportService.class);
  final CodeGraphService graphs = mock(CodeGraphService.class);
  final EnforcementRepository enforcement = mock(EnforcementRepository.class);
  final AnalyticsDatabase database = mock(AnalyticsDatabase.class);
  final RegisteredProject project = new RegisteredProject(id, "HealthMate", "Demo", "https://github.com/SahanMedhawa/HealthMate.git",
      Set.of(ArchitectureStyle.MICROSERVICES), Instant.parse("2026-10-18T13:40:00Z"), ProjectStatus.ACTIVE);
  ProjectAnalyticsController controller(ProjectReadAuthorizer access, MockEnvironment env) {
    when(projects.findAll()).thenReturn(List.of(project)); when(projects.findById(id)).thenReturn(project);
    return new ProjectAnalyticsController(projects, exports, graphs, enforcement, database, access, env);
  }
  @Test void lookupCanonicalizesRepositoryAndChecksAccess() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(controller((p, id) -> {}, new MockEnvironment())).build();
    mvc.perform(get("/api/analytics/repository").param("url", "https://github.com/sahanmedhawa/HealthMate/"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(true)).andExpect(jsonPath("$.project.id").value(id.toString()));
    mvc.perform(get("/api/analytics/repository").param("url", "https://github.com/example/new"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(false));
    mvc.perform(get("/api/analytics/repository").param("url", "https://github.com.attacker.example/owner/repo")).andExpect(status().isBadRequest());
    var denied = MockMvcBuilders.standaloneSetup(controller((p, id) -> { throw new SecurityException(); }, new MockEnvironment())).build();
    denied.perform(get("/api/analytics/repository").param("url", project.repositoryUrl())).andExpect(status().isForbidden());
  }
  @Test void contextSeparatesMissingScansAndEmptyRulesFromZeroViolations() throws Exception {
    when(exports.export(id)).thenReturn(new RuleExportResponse(id, "HealthMate", project.repositoryUrl(), ArchitectureStyle.MICROSERVICES, List.of()));
    when(graphs.listScans()).thenThrow(new IllegalStateException("unavailable"));
    when(enforcement.listRuns(5000)).thenReturn(List.of());
    var mvc = MockMvcBuilders.standaloneSetup(controller((p, id) -> {}, new MockEnvironment())).build();
    mvc.perform(get("/api/analytics/projects/" + id + "/context")).andExpect(status().isOk())
        .andExpect(jsonPath("$.scans").isEmpty()).andExpect(jsonPath("$.warnings[0]").value("Repository scan history is temporarily unavailable."))
        .andExpect(jsonPath("$.ruleExport.ruleCount").value(0)).andExpect(jsonPath("$.demoEnabled").value(false));
    mvc.perform(post("/api/analytics/projects/" + id + "/demo-history")).andExpect(status().isForbidden());
    verifyNoInteractions(database);
  }
  @Test void recordedIngressRequiresExplicitPublisherKey() throws Exception {
    var mvc = MockMvcBuilders.standaloneSetup(new RecordedSnapshotController(database, exports, new MockEnvironment())).build();
    mvc.perform(post("/api/analytics/projects/" + id + "/snapshots").contentType("application/json").content("{\"contractVersion\":1,\"sequence\":0}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(database, exports);
  }
  @Test void newPipelineEventsUseRegisteredProjectIdentity() {
    when(projects.findAll()).thenReturn(List.of(project));
    var props = new akegp.enforcement.config.EnforcementProperties();
    var resolver = new ProjectEnforcementLinks().registeredProjectLinks(projects, props);
    org.junit.jupiter.api.Assertions.assertEquals(Optional.of(id.toString()), resolver.projectIdFor("SahanMedhawa/HealthMate"));
    when(projects.findAll()).thenReturn(List.of(project.withStatus(ProjectStatus.INACTIVE)));
    org.junit.jupiter.api.Assertions.assertTrue(resolver.projectIdFor("SahanMedhawa/HealthMate").isEmpty());
  }
}
