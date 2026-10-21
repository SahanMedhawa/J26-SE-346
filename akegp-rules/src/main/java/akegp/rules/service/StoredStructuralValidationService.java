package akegp.rules.service;

import akegp.rules.api.StoredStructuralValidationResponse;
import akegp.rules.api.StructuralValidationResponse;
import akegp.rules.domain.ArchitectureStyle;
import akegp.rules.domain.StructuralValidationRecord;
import akegp.rules.repository.RuleAssuranceRepository;
import akegp.rules.validation.ArvmCatalogue;
import akegp.rules.validation.RuleStructureValidationService;
import akegp.rules.validation.StructuralValidationStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoredStructuralValidationService {
  private final RuleStructureValidationService validator;
  private final RuleEvidenceService evidence;
  private final RuleAssuranceRepository repository;
  private final ArvmCatalogue catalogue;

  public StoredStructuralValidationService(RuleStructureValidationService validator, RuleEvidenceService evidence,
      RuleAssuranceRepository repository, ArvmCatalogue catalogue) {
    this.validator = validator; this.evidence = evidence; this.repository = repository; this.catalogue = catalogue;
  }

  @Transactional("rulesTransactionManager")
  public StructuralValidationResponse validate(UUID projectId, UUID ruleUuid, ArchitectureStyle style) {
    var snapshot = evidence.load(projectId, ruleUuid);
    var result = validator.validate(projectId, ruleUuid, style); // The existing algorithm and response are unchanged.
    repository.saveValidation(new StructuralValidationRecord(
        repository.validation(ruleUuid).map(StructuralValidationRecord::id).orElseGet(UUID::randomUUID),
        ruleUuid, result.validationStatus(), result.message(), result.matchedSignatureId(),
        result.architectureStyle(), null,
        EvidenceFingerprints.structural(snapshot.project(), snapshot.rule(), result.architectureStyle(), catalogue),
        snapshot.revisions().structural(), Instant.now()));
    return result;
  }

  @Transactional("rulesTransactionManager")
  public StoredStructuralValidationResponse find(UUID projectId, UUID ruleUuid) {
    return current(evidence.load(projectId, ruleUuid));
  }

  public StoredStructuralValidationResponse current(RuleEvidenceService.Evidence snapshot) {
    var saved = repository.validation(snapshot.rule().id()).orElse(null);
    boolean current = saved != null && saved.structuralRevision() == snapshot.revisions().structural()
        && saved.structuralFingerprint().equals(EvidenceFingerprints.structural(
            snapshot.project(), snapshot.rule(), saved.architectureStyle(), catalogue));
    return new StoredStructuralValidationResponse(snapshot.rule().id(),
        current ? saved.status() : StructuralValidationStatus.NOT_VALIDATED,
        saved == null ? "This rule has not been structurally validated."
            : current ? saved.reason() : "Structural definition or catalogue changed; run structural validation again.",
        saved == null ? null : saved.matchedSignatureId(), saved == null ? null : saved.architectureStyle(),
        saved == null ? null : saved.catalogueVersion(), saved == null ? null : saved.structuralFingerprint(),
        saved == null ? null : saved.validatedAt(), current, saved == null ? null : saved.status(),
        saved == null ? null : saved.structuralRevision(), snapshot.revisions().structural());
  }
}
