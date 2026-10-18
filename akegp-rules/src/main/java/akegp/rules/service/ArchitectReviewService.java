package akegp.rules.service;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.RuleAssuranceRepository;
import akegp.rules.validation.StructuralValidationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchitectReviewService {
  private final RuleEvidenceService evidence;
  private final StoredStructuralValidationService validation;
  private final RuleAssuranceRepository repository;

  public ArchitectReviewService(RuleEvidenceService evidence, StoredStructuralValidationService validation,
      RuleAssuranceRepository repository) {
    this.evidence = evidence; this.validation = validation; this.repository = repository;
  }

  @Transactional("rulesTransactionManager")
  public ArchitectReviewResponse find(UUID projectId, UUID ruleUuid) {
    return current(evidence.load(projectId, ruleUuid));
  }

  /** Shared effective-review calculation for an already loaded, transaction-consistent evidence snapshot. */
  public ArchitectReviewResponse current(RuleEvidenceService.Evidence snapshot) {
    return workspace(snapshot, validation.current(snapshot));
  }

  @Transactional("rulesTransactionManager")
  public ArchitectReviewResponse submit(UUID projectId, UUID ruleUuid, ArchitectReviewRequest request) {
    var snapshot = evidence.load(projectId, ruleUuid);
    validateRequest(request);
    var structural = validation.current(snapshot);
    var workspace = workspace(snapshot, structural);
    if (request.expectedEvidenceFingerprint() != null
        && !request.expectedEvidenceFingerprint().equals(workspace.evidenceFingerprint())) {
      throw new ArchitectReviewException(HttpStatus.CONFLICT, "Reviewed evidence changed; reload the review workspace before submitting.");
    }
    if (request.decision() == ArchitectReviewDecision.CONFIRMED && !workspace.confirmationEligible()) {
      throw new ArchitectReviewException(HttpStatus.CONFLICT,
          "Rule cannot be confirmed: " + String.join(" ", workspace.blockedReasons()));
    }
    var adr = snapshot.adr();
    repository.appendReview(new ArchitectReviewRecord(UUID.randomUUID(), ruleUuid, projectId,
        request.decision(), request.reviewer().strip(), optional(request.comments()), Instant.now(),
        EvidenceFingerprints.rule(snapshot.rule()), adr == null ? null : adr.id(), adr == null ? null : adr.version(),
        EvidenceFingerprints.adr(adr), EvidenceFingerprints.mappings(snapshot.mappings()),
        workspace.evidenceFingerprint(), snapshot.revisions().review(), structural.status(), structural.validatedFingerprint()));
    return workspace(snapshot, structural);
  }

  @Transactional("rulesTransactionManager")
  public List<ArchitectReviewHistoryResponse> history(UUID projectId, UUID ruleUuid) {
    var snapshot = evidence.load(projectId, ruleUuid);
    var structural = validation.current(snapshot);
    String fingerprint = fingerprint(snapshot, structural);
    return repository.reviews(ruleUuid).stream()
        .map(review -> historyEntry(review, fingerprint, structural)).toList();
  }

  private ArchitectReviewResponse workspace(RuleEvidenceService.Evidence snapshot,
      StoredStructuralValidationResponse structural) {
    var reasons = new ArrayList<String>();
    if (!structural.current()) reasons.add(structural.reason());
    else if (structural.status() != StructuralValidationStatus.SUPPORTED) reasons.add("Structural validation is UNSUPPORTED. " + structural.reason());
    if (snapshot.adr() == null) reasons.add("The linked ADR is missing; a current ADR and version are required.");
    int sources = (int) snapshot.mappings().stream().filter(m -> m.side() == ImplementationMappingSide.SOURCE).count();
    int targets = (int) snapshot.mappings().stream().filter(m -> m.side() == ImplementationMappingSide.TARGET).count();
    if (sources == 0) reasons.add("At least one SOURCE implementation file is required for PP1 confirmation.");
    if (targets == 0) reasons.add("At least one TARGET implementation file is required for PP1 confirmation.");
    String fingerprint = fingerprint(snapshot, structural);
    var latest = repository.reviews(snapshot.rule().id()).stream().findFirst()
        .map(review -> historyEntry(review, fingerprint, structural)).orElse(null);
    var effective = latest != null && latest.current()
        ? ArchitectReviewStatus.valueOf(latest.review().decision().name()) : ArchitectReviewStatus.PENDING;
    var adr = snapshot.adr();
    return new ArchitectReviewResponse(snapshot.rule().id(), snapshot.rule().ruleId(), snapshot.rule().adrId(),
        adr == null ? null : adr.adrId(), adr == null ? null : adr.version(), structural.status(), structural.current(),
        effective, reasons.isEmpty(), List.copyOf(reasons), fingerprint, snapshot.revisions().review(), sources, targets, latest);
  }

  private static String fingerprint(RuleEvidenceService.Evidence snapshot, StoredStructuralValidationResponse structural) {
    return EvidenceFingerprints.hash(snapshot.project().repositoryUrl(), EvidenceFingerprints.rule(snapshot.rule()), EvidenceFingerprints.adr(snapshot.adr()),
        EvidenceFingerprints.mappings(snapshot.mappings()), snapshot.revisions().review(),
        structural.status(), structural.current(), structural.validatedFingerprint());
  }

  private static ArchitectReviewHistoryResponse historyEntry(ArchitectReviewRecord review, String fingerprint,
      StoredStructuralValidationResponse structural) {
    boolean current = review.evidenceFingerprint().equals(fingerprint)
        && (review.decision() != ArchitectReviewDecision.CONFIRMED
            || structural.current() && structural.status() == StructuralValidationStatus.SUPPORTED);
    return new ArchitectReviewHistoryResponse(review, current, false);
  }

  private static void validateRequest(ArchitectReviewRequest request) {
    if (request == null || request.decision() == null || request.reviewer() == null
        || request.reviewer().isBlank() || request.reviewer().length() > 150
        || request.comments() != null && request.comments().length() > 4000
        || request.expectedEvidenceFingerprint() != null && !request.expectedEvidenceFingerprint().matches("[a-f0-9]{64}")) {
      throw new ArchitectReviewException(HttpStatus.BAD_REQUEST, "A valid decision, reviewer (up to 150 characters), and comments (up to 4000 characters) are required.");
    }
    if (request.decision() == ArchitectReviewDecision.CHANGES_REQUESTED
        && (request.comments() == null || request.comments().strip().length() < 10)) {
      throw new ArchitectReviewException(HttpStatus.BAD_REQUEST, "CHANGES_REQUESTED requires meaningful comments of at least 10 characters.");
    }
    if (request.decision() == ArchitectReviewDecision.CONFIRMED && request.expectedEvidenceFingerprint() == null) {
      throw new ArchitectReviewException(HttpStatus.BAD_REQUEST,
          "CONFIRMED requires expectedEvidenceFingerprint from the current architect-review GET response; load and review the evidence first.");
    }
  }

  private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
