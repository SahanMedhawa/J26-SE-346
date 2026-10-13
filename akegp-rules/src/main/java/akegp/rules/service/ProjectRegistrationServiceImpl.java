package akegp.rules.service;

import akegp.rules.api.ProjectRegistrationRequest;
import akegp.rules.api.ProjectStatusUpdateRequest;
import akegp.rules.api.ProjectUpdateRequest;
import akegp.rules.domain.RegisteredProject;
import akegp.rules.repository.ProjectRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectRegistrationServiceImpl implements ProjectRegistrationService {
  private final ProjectRepository projectRepository;

  public ProjectRegistrationServiceImpl(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  @Override
  public synchronized RegisteredProject register(ProjectRegistrationRequest request) {
    String normalizedUrl = normalizeRepositoryUrl(request.repositoryUrl());
    projectRepository
        .findByNormalizedRepositoryUrl(normalizedUrl)
        .ifPresent(
            ignored -> {
              throw new DuplicateProjectException(
                  "A project is already registered for repository URL: " + normalizedUrl);
            });

    RegisteredProject project =
        new RegisteredProject(
            UUID.randomUUID(),
            request.name().trim(),
            normalizeOptional(request.description()),
            normalizedUrl,
            request.architectureStyles(),
            Instant.now(),
            akegp.rules.domain.ProjectStatus.ACTIVE);
    return projectRepository.save(project);
  }

  @Override
  public List<RegisteredProject> findAll() {
    return projectRepository.findAll();
  }

  @Override
  public RegisteredProject findById(UUID id) {
    return projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
  }

  @Override
  public synchronized RegisteredProject update(UUID id, ProjectUpdateRequest request) {
    RegisteredProject existing = findById(id);
    String normalizedUrl = normalizeRepositoryUrl(request.repositoryUrl());
    projectRepository
        .findByNormalizedRepositoryUrl(normalizedUrl)
        .filter(project -> !project.id().equals(id))
        .ifPresent(
            ignored -> {
              throw new DuplicateProjectException(
                  "A project is already registered for repository URL: " + normalizedUrl);
            });

    return projectRepository.save(
        existing.update(
            request.name().trim(),
            normalizeOptional(request.description()),
            normalizedUrl,
            request.architectureStyles()));
  }

  @Override
  public RegisteredProject updateStatus(UUID id, ProjectStatusUpdateRequest request) {
    RegisteredProject existing = findById(id);
    return projectRepository.save(existing.withStatus(request.status()));
  }

  static String normalizeRepositoryUrl(String repositoryUrl) {
    URI uri = URI.create(repositoryUrl.trim());
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    String path = uri.getPath().replaceAll("/+$", "");
    String authority = host + (port >= 0 ? ":" + port : "");
    return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + authority + path;
  }

  private static String normalizeOptional(String value) {
    return value == null ? null : value.trim();
  }
}
