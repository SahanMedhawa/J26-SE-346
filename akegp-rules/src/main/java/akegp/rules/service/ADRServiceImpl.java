package akegp.rules.service;

import akegp.rules.api.CreateADRRequest;
import akegp.rules.api.UpdateADRRequest;
import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.domain.ProjectStatus;
import akegp.rules.repository.ADRRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ADRServiceImpl implements ADRService {
  private final ADRRepository adrRepository;
  private final ProjectRegistrationService projectService;

  public ADRServiceImpl(ADRRepository adrRepository, ProjectRegistrationService projectService) {
    this.adrRepository = adrRepository;
    this.projectService = projectService;
  }

  @Override
  @Transactional
  public ArchitectureDecisionRecord create(UUID projectId, CreateADRRequest request) {
    requireActiveProject(projectId);
    String adrId = normalize(request.adrId());
    if (adrRepository.existsByAdrId(projectId, adrId)) {
      throw new DuplicateADRException(adrId + " already exists in this project.");
    }
    Instant now = Instant.now();
    return adrRepository.insert(
        new ArchitectureDecisionRecord(
            UUID.randomUUID(), adrId, projectId, normalize(request.title()), request.status(),
            normalize(request.context()), normalize(request.decision()), normalize(request.consequences()),
            1, now, now));
  }

  @Override
  public List<ArchitectureDecisionRecord> findAll(UUID projectId, ADRStatus status, String search) {
    projectService.findById(projectId);
    return adrRepository.findByProject(projectId, status, search);
  }

  @Override
  public ArchitectureDecisionRecord findById(UUID projectId, UUID id) {
    projectService.findById(projectId);
    return adrRepository.findById(projectId, id).orElseThrow(() -> new ADRNotFoundException(id));
  }

  @Override
  @Transactional
  public ArchitectureDecisionRecord update(UUID projectId, UUID id, UpdateADRRequest request) {
    ArchitectureDecisionRecord existing = findById(projectId, id);
    ArchitectureDecisionRecord candidate =
        new ArchitectureDecisionRecord(
            existing.id(), normalize(request.adrId()), existing.projectId(), normalize(request.title()),
            request.status(), normalize(request.context()), normalize(request.decision()),
            normalize(request.consequences()), existing.version(), existing.createdAt(), existing.updatedAt());
    if (sameContent(existing, candidate)) {
      if (request.version() != existing.version()) {
        throw new StaleADRException();
      }
      return existing;
    }
    if (!existing.adrId().equals(candidate.adrId())
        && adrRepository.existsByAdrId(projectId, candidate.adrId())) {
      throw new DuplicateADRException(candidate.adrId() + " already exists in this project.");
    }
    if (request.version() != existing.version()) {
      throw new StaleADRException();
    }
    ArchitectureDecisionRecord updated =
        new ArchitectureDecisionRecord(
            candidate.id(), candidate.adrId(), candidate.projectId(), candidate.title(), candidate.status(),
            candidate.context(), candidate.decision(), candidate.consequences(), existing.version() + 1,
            candidate.createdAt(), Instant.now());
    if (!adrRepository.update(updated, existing.version())) {
      throw new StaleADRException();
    }
    return updated;
  }

  @Override
  @Transactional
  public void delete(UUID projectId, UUID id) {
    findById(projectId, id);
    // Architecture-rule persistence does not exist yet, so there are no dependencies to inspect.
    if (!adrRepository.delete(projectId, id)) {
      throw new ADRNotFoundException(id);
    }
  }

  private void requireActiveProject(UUID projectId) {
    if (projectService.findById(projectId).status() != ProjectStatus.ACTIVE) {
      throw new InactiveProjectException();
    }
  }

  private static boolean sameContent(
      ArchitectureDecisionRecord left, ArchitectureDecisionRecord right) {
    return left.adrId().equals(right.adrId())
        && left.title().equals(right.title())
        && left.status() == right.status()
        && left.context().equals(right.context())
        && left.decision().equals(right.decision())
        && left.consequences().equals(right.consequences());
  }

  private static String normalize(String value) {
    return value.trim();
  }
}
