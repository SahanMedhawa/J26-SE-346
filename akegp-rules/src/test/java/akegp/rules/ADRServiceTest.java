package akegp.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.rules.api.CreateADRRequest;
import akegp.rules.api.ProjectRegistrationRequest;
import akegp.rules.api.ProjectStatusUpdateRequest;
import akegp.rules.api.UpdateADRRequest;
import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.repository.InMemoryADRRepository;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.service.ADRServiceImpl;
import akegp.rules.service.ReadableIdException;
import akegp.rules.service.InactiveProjectException;
import akegp.rules.service.StaleADRException;
import akegp.rules.service.ProjectRegistrationServiceImpl;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ADRServiceTest {
  private ADRServiceImpl service;
  private ProjectRegistrationServiceImpl projectService;
  private UUID projectId;

  @BeforeEach
  void setUp() {
    projectService = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
    service = new ADRServiceImpl(new InMemoryADRRepository(), projectService, new TestReadableIdRepository());
    projectId =
        projectService
            .register(
                new ProjectRegistrationRequest(
                    "Project", null, "https://github.com/example/project-" + UUID.randomUUID(),
                    Set.of(ArchitectureStyle.LAYERED)))
            .id();
  }

  @Test
  void createsAndVersionsADR() {
    ArchitectureDecisionRecord created = service.create(projectId, request());
    assertEquals(1, created.version());

    ArchitectureDecisionRecord updated =
        service.update(
            projectId,
            created.id(),
            new UpdateADRRequest(
                "ADR-001", "Updated architecture decision", ADRStatus.ACCEPTED,
                "Updated context with enough detail.", "Updated decision with enough detail.",
                "Updated consequences with enough detail.", 1));
    assertEquals(2, updated.version());
  }

  @Test
  void identicalUpdateDoesNotIncrementVersion() {
    ArchitectureDecisionRecord created = service.create(projectId, request());
    ArchitectureDecisionRecord unchanged =
        service.update(
            projectId,
            created.id(),
            new UpdateADRRequest(
                created.adrId(), created.title(), created.status(), created.context(),
                created.decision(), created.consequences(), created.version()));
    assertEquals(1, unchanged.version());
    assertEquals(created.updatedAt(), unchanged.updatedAt());
  }

  @Test
  void rejectsStaleUpdateAndInactiveProject() {
    ArchitectureDecisionRecord created = service.create(projectId, request());
    service.update(
        projectId, created.id(),
        new UpdateADRRequest(
            "ADR-001", "Updated architecture decision", ADRStatus.ACCEPTED,
            "Updated context with enough detail.", "Updated decision with enough detail.",
            "Updated consequences with enough detail.", 1));
    assertThrows(
        StaleADRException.class,
        () -> service.update(projectId, created.id(), new UpdateADRRequest(
            "ADR-001", "Another architecture decision", ADRStatus.ACCEPTED,
            "Another context with enough detail.", "Another decision with enough detail.",
            "Another consequences with enough detail.", 1)));

    projectService.updateStatus(projectId, new ProjectStatusUpdateRequest(ProjectStatus.INACTIVE));
    assertThrows(InactiveProjectException.class, () -> service.create(projectId, request()));
  }

  @Test
  void allowsSameAdrIdInDifferentProjectsAndFiltersSearch() {
    service.create(projectId, request());
    UUID otherProject =
        projectService
            .register(
                new ProjectRegistrationRequest(
                    "Other", null, "https://github.com/example/other-" + UUID.randomUUID(),
                    Set.of(ArchitectureStyle.MICROSERVICES)))
            .id();
    service.create(otherProject, request());
    assertEquals(1, service.findAll(projectId, null, "context").size());
    assertEquals(1, service.findAll(otherProject, ADRStatus.PROPOSED, null).size());
  }

  @Test
  void rejectsClientIdAndCrossProjectAccess() {
    ArchitectureDecisionRecord created = service.create(projectId, request());
    assertThrows(
        ReadableIdException.class, () -> service.create(projectId, new CreateADRRequest("ADR-999", "Valid title", ADRStatus.PROPOSED, "Context details", "Decision details", "Consequences details")));
    UUID otherProject =
        projectService
            .register(
                new ProjectRegistrationRequest(
                    "Other", null, "https://github.com/example/other-" + UUID.randomUUID(),
                    Set.of(ArchitectureStyle.MICROSERVICES)))
            .id();
    assertThrows(
        akegp.rules.service.ADRNotFoundException.class,
        () -> service.findById(otherProject, created.id()));
    service.delete(projectId, created.id());
    assertThrows(
        akegp.rules.service.ADRNotFoundException.class,
        () -> service.findById(projectId, created.id()));
  }

  private static CreateADRRequest request() {
    return new CreateADRRequest(
        null, "Architecture decision title", ADRStatus.PROPOSED,
        "This context has enough detail.", "This decision has enough detail.",
        "These consequences have enough detail.");
  }
}
