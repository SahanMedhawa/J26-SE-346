package akegp.rules.repository;

import akegp.rules.domain.RegisteredProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Temporary repository for API development. Data is held only in memory and is lost on restart.
 */
public class InMemoryProjectRepository implements ProjectRepository {
  private final ConcurrentMap<UUID, RegisteredProject> projects = new ConcurrentHashMap<>();

  @Override
  public RegisteredProject save(RegisteredProject project) {
    projects.put(project.id(), project);
    return project;
  }

  @Override
  public List<RegisteredProject> findAll() {
    return projects.values().stream()
        .sorted(Comparator.comparing(RegisteredProject::registeredAt))
        .toList();
  }

  @Override
  public Optional<RegisteredProject> findById(UUID id) {
    return Optional.ofNullable(projects.get(id));
  }

  @Override
  public Optional<RegisteredProject> findByNormalizedRepositoryUrl(String normalizedRepositoryUrl) {
    return projects.values().stream()
        .filter(project -> project.repositoryUrl().equals(normalizedRepositoryUrl))
        .findFirst();
  }
}
