package akegp.rules.api;

import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.ProjectStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String name,
    String description,
    String repositoryUrl,
    Set<ArchitectureStyle> architectureStyles,
    Instant registeredAt,
    ProjectStatus status) {}
