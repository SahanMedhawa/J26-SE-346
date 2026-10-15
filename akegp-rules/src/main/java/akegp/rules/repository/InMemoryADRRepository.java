package akegp.rules.repository;

import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;

@Profile("test")
public class InMemoryADRRepository implements ADRRepository {
  private final Map<UUID, ArchitectureDecisionRecord> records = new ConcurrentHashMap<>();

  @Override
  public ArchitectureDecisionRecord insert(ArchitectureDecisionRecord adr) {
    records.put(adr.id(), adr);
    return adr;
  }

  @Override
  public List<ArchitectureDecisionRecord> findByProject(UUID projectId, ADRStatus status, String search) {
    String normalizedSearch = search == null ? "" : search.trim().toLowerCase();
    return records.values().stream()
        .filter(adr -> adr.projectId().equals(projectId))
        .filter(adr -> status == null || adr.status() == status)
        .filter(adr -> normalizedSearch.isBlank()
            || (adr.adrId() + " " + adr.title() + " " + adr.context() + " "
                    + adr.decision() + " " + adr.consequences())
                .toLowerCase()
                .contains(normalizedSearch))
        .sorted(Comparator.comparing(ArchitectureDecisionRecord::updatedAt).reversed())
        .toList();
  }

  @Override
  public Optional<ArchitectureDecisionRecord> findById(UUID projectId, UUID id) {
    return Optional.ofNullable(records.get(id)).filter(adr -> adr.projectId().equals(projectId));
  }

  @Override
  public boolean existsByAdrId(UUID projectId, String adrId) {
    return records.values().stream().anyMatch(adr -> adr.projectId().equals(projectId) && adr.adrId().equals(adrId));
  }

  @Override
  public boolean update(ArchitectureDecisionRecord adr, int expectedVersion) {
    synchronized (records) {
      ArchitectureDecisionRecord current = records.get(adr.id());
      if (current == null || current.version() != expectedVersion) {
        return false;
      }
      records.put(adr.id(), adr);
      return true;
    }
  }

  @Override
  public boolean delete(UUID projectId, UUID id) {
    return findById(projectId, id).map(adr -> records.remove(id, adr)).orElse(false);
  }
}
