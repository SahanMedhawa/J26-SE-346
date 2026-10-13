package akegp.rules.repository;

import akegp.rules.domain.RegisteredProject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
  RegisteredProject save(RegisteredProject project);

  List<RegisteredProject> findAll();

  Optional<RegisteredProject> findById(UUID id);

  Optional<RegisteredProject> findByNormalizedRepositoryUrl(String normalizedRepositoryUrl);
}
