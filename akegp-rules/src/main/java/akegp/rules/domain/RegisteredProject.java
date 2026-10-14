package akegp.rules.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RegisteredProject(
    UUID id,
    String name,
    String description,
    String repositoryUrl,
    Set<ArchitectureStyle> architectureStyles,
    Instant registeredAt,
    ProjectStatus status) {

  public RegisteredProject {
    architectureStyles = Set.copyOf(architectureStyles);
  }

  public RegisteredProject update(
      String updatedName,
      String updatedDescription,
      String updatedRepositoryUrl,
      Set<ArchitectureStyle> updatedArchitectureStyles) {
    return new RegisteredProject(
        id,
        updatedName,
        updatedDescription,
        updatedRepositoryUrl,
        updatedArchitectureStyles,
        registeredAt,
        status);
  }

  public RegisteredProject withStatus(ProjectStatus updatedStatus) {
    return new RegisteredProject(
        id, name, description, repositoryUrl, architectureStyles, registeredAt, updatedStatus);
  }
}
