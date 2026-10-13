package akegp.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akegp.rules.api.ProjectRegistrationRequest;
import akegp.rules.api.ProjectStatusUpdateRequest;
import akegp.rules.api.ProjectUpdateRequest;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.domain.RegisteredProject;
import akegp.rules.repository.InMemoryProjectRepository;
import akegp.rules.service.DuplicateProjectException;
import akegp.rules.service.ProjectNotFoundException;
import akegp.rules.service.ProjectRegistrationServiceImpl;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectRegistrationServiceTest {
  private ProjectRegistrationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
  }

  @Test
  void registersProjectAsActiveWithGeneratedIdentityAndTimestamp() {
    RegisteredProject project = service.register(request("https://github.com/example/project"));

    assertEquals("Project", project.name());
    assertEquals(ProjectStatus.ACTIVE, project.status());
    assertEquals(Set.of(ArchitectureStyle.MICROSERVICES), project.architectureStyles());
  }

  @Test
  void preventsDuplicateNormalizedRepositoryUrls() {
    service.register(request("HTTPS://GitHub.com/example/project/"));

    assertThrows(
        DuplicateProjectException.class,
        () -> service.register(request("https://github.com/example/project")));
  }

  @Test
  void retrievesAndUpdatesProjectWithoutChangingIdentityOrRegistrationTime() {
    RegisteredProject original = service.register(request("https://github.com/example/project"));
    RegisteredProject updated =
        service.update(
            original.id(),
            new ProjectUpdateRequest(
                "Updated", "Description", "https://gitlab.com/example/project",
                Set.of(ArchitectureStyle.LAYERED)));

    assertEquals(original.id(), updated.id());
    assertEquals(original.registeredAt(), updated.registeredAt());
    assertEquals("Updated", service.findById(original.id()).name());
  }

  @Test
  void changesProjectStatus() {
    RegisteredProject project = service.register(request("https://github.com/example/project"));

    RegisteredProject inactive =
        service.updateStatus(project.id(), new ProjectStatusUpdateRequest(ProjectStatus.INACTIVE));

    assertEquals(ProjectStatus.INACTIVE, inactive.status());
  }

  @Test
  void supportsCombinedArchitectureStyle() {
    RegisteredProject project =
        service.register(
            new ProjectRegistrationRequest(
                "Project", null, "https://github.com/example/project",
                Set.of(ArchitectureStyle.MICROSERVICES_AND_LAYERED)));

    assertEquals(
        Set.of(ArchitectureStyle.MICROSERVICES_AND_LAYERED), project.architectureStyles());
  }

  @Test
  void reportsMissingProject() {
    assertThrows(
        ProjectNotFoundException.class,
        () -> service.findById(java.util.UUID.randomUUID()));
  }

  private static ProjectRegistrationRequest request(String url) {
    return new ProjectRegistrationRequest(
        "Project", null, url, Set.of(ArchitectureStyle.MICROSERVICES));
  }
}
