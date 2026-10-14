package akegp.rules.api;

import akegp.rules.domain.RegisteredProject;
import akegp.rules.service.ProjectRegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules/projects")
public class ProjectRegistrationController {
  private final ProjectRegistrationService projectRegistrationService;

  public ProjectRegistrationController(ProjectRegistrationService projectRegistrationService) {
    this.projectRegistrationService = projectRegistrationService;
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> register(
      @Valid @RequestBody ProjectRegistrationRequest request) {
    RegisteredProject project = projectRegistrationService.register(request);
    return ResponseEntity.created(URI.create("/api/rules/projects/" + project.id()))
        .body(toResponse(project));
  }

  @GetMapping
  public List<ProjectResponse> findAll() {
    return projectRegistrationService.findAll().stream().map(ProjectRegistrationController::toResponse).toList();
  }

  @GetMapping("/{id}")
  public ProjectResponse findById(@PathVariable("id") UUID id) {
    return toResponse(projectRegistrationService.findById(id));
  }

  @PutMapping("/{id}")
  public ProjectResponse update(
      @PathVariable("id") UUID id, @Valid @RequestBody ProjectUpdateRequest request) {
    return toResponse(projectRegistrationService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  public ProjectResponse updateStatus(
      @PathVariable("id") UUID id, @Valid @RequestBody ProjectStatusUpdateRequest request) {
    return toResponse(projectRegistrationService.updateStatus(id, request));
  }

  private static ProjectResponse toResponse(RegisteredProject project) {
    return new ProjectResponse(
        project.id(),
        project.name(),
        project.description(),
        project.repositoryUrl(),
        project.architectureStyles(),
        project.registeredAt(),
        project.status());
  }
}
