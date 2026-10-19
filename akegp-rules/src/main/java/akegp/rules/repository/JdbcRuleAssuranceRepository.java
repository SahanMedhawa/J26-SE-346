package akegp.rules.repository;

import akegp.rules.domain.*;
import akegp.rules.validation.StructuralValidationStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRuleAssuranceRepository implements RuleAssuranceRepository {
  private final JdbcTemplate jdbc;

  public JdbcRuleAssuranceRepository(@Qualifier("rulesJdbcTemplate") JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  public boolean lockProject(UUID projectId) {
    return !jdbc.query("SELECT id FROM registered_projects WHERE id = ? FOR SHARE",
        (rs, row) -> rs.getObject("id", UUID.class), projectId).isEmpty();
  }

  @Override
  public boolean lockRule(UUID projectId, UUID ruleUuid) {
    return !jdbc.query("SELECT id FROM architecture_rules WHERE project_id = ? AND id = ? FOR UPDATE",
        (rs, row) -> rs.getObject("id", UUID.class), projectId, ruleUuid).isEmpty();
  }

  @Override
  public boolean lockAdr(UUID projectId, UUID adrUuid) {
    return !jdbc.query("SELECT id FROM adrs WHERE project_id = ? AND id = ? FOR SHARE",
        (rs, row) -> rs.getObject("id", UUID.class), projectId, adrUuid).isEmpty();
  }

  @Override
  public Revisions revisions(UUID ruleUuid) {
    return jdbc.query("SELECT structural_revision, review_revision FROM rule_evidence_revisions WHERE rule_id = ?",
        (rs, row) -> new Revisions(rs.getLong(1), rs.getLong(2)), ruleUuid).stream()
        .findFirst().orElse(new Revisions(0, 0));
  }

  @Override
  public Optional<StructuralValidationRecord> validation(UUID ruleUuid) {
    return jdbc.query("SELECT * FROM rule_structural_validations WHERE rule_id = ?", (rs, row) ->
        new StructuralValidationRecord(rs.getObject("id", UUID.class), rs.getObject("rule_id", UUID.class),
            StructuralValidationStatus.valueOf(rs.getString("status")), rs.getString("reason"),
            rs.getString("matched_signature_id"), ArchitectureStyle.valueOf(rs.getString("architecture_style")),
            rs.getString("catalogue_version"), rs.getString("structural_fingerprint"),
            rs.getLong("structural_revision"), rs.getTimestamp("validated_at").toInstant()), ruleUuid)
        .stream().findFirst();
  }

  @Override
  public void saveValidation(StructuralValidationRecord value) {
    jdbc.update("""
        INSERT INTO rule_structural_validations
            (id, rule_id, status, reason, matched_signature_id, architecture_style, catalogue_version,
             structural_fingerprint, structural_revision, validated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (rule_id) DO UPDATE SET status = EXCLUDED.status, reason = EXCLUDED.reason,
            matched_signature_id = EXCLUDED.matched_signature_id, architecture_style = EXCLUDED.architecture_style,
            catalogue_version = EXCLUDED.catalogue_version, structural_fingerprint = EXCLUDED.structural_fingerprint,
            structural_revision = EXCLUDED.structural_revision, validated_at = EXCLUDED.validated_at
        """, value.id(), value.ruleUuid(), value.status().name(), value.reason(), value.matchedSignatureId(),
        value.architectureStyle().name(), value.catalogueVersion(), value.structuralFingerprint(),
        value.structuralRevision(), Timestamp.from(value.validatedAt()));
  }

  @Override
  public void appendReview(ArchitectReviewRecord value) {
    jdbc.update("""
        INSERT INTO architect_reviews
            (id, rule_id, rule_uuid, project_uuid, decision, reviewer, comments, decided_at,
             rule_fingerprint, adr_uuid, adr_version, adr_fingerprint, mapping_fingerprint,
             evidence_fingerprint, review_revision, validation_status, validation_fingerprint)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, value.id(), value.ruleUuid(), value.ruleUuid(), value.projectUuid(), value.decision().name(),
        value.reviewer(), value.comments(), Timestamp.from(value.decidedAt()), value.ruleFingerprint(),
        value.adrUuid(), value.adrVersion(), value.adrFingerprint(), value.mappingFingerprint(),
        value.evidenceFingerprint(), value.reviewRevision(), value.validationStatus().name(), value.validationFingerprint());
  }

  @Override
  public List<ArchitectReviewRecord> reviews(UUID ruleUuid) {
    return jdbc.query("SELECT * FROM architect_reviews WHERE rule_uuid = ? ORDER BY review_order DESC",
        (rs, row) -> new ArchitectReviewRecord(rs.getObject("id", UUID.class), rs.getObject("rule_uuid", UUID.class),
            rs.getObject("project_uuid", UUID.class), ArchitectReviewDecision.valueOf(rs.getString("decision")),
            rs.getString("reviewer"), rs.getString("comments"), rs.getTimestamp("decided_at").toInstant(),
            rs.getString("rule_fingerprint"), rs.getObject("adr_uuid", UUID.class),
            rs.getObject("adr_version", Integer.class), rs.getString("adr_fingerprint"),
            rs.getString("mapping_fingerprint"), rs.getString("evidence_fingerprint"), rs.getLong("review_revision"),
            StructuralValidationStatus.valueOf(rs.getString("validation_status")), rs.getString("validation_fingerprint")), ruleUuid);
  }
}
