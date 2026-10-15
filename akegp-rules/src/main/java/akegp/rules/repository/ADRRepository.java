package akegp.rules.repository;

import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ADRRepository {
  ArchitectureDecisionRecord insert(ArchitectureDecisionRecord adr);

  List<ArchitectureDecisionRecord> findByProject(
      UUID projectId, ADRStatus status, String search);

  Optional<ArchitectureDecisionRecord> findById(UUID projectId, UUID id);

  boolean existsByAdrId(UUID projectId, String adrId);

  boolean update(ArchitectureDecisionRecord adr, int expectedVersion);

  boolean delete(UUID projectId, UUID id);
}
