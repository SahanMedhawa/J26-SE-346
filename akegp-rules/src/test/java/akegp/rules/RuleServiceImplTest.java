package akegp.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import akegp.rules.api.RuleResponse;
import akegp.rules.domain.ADRStatus;
import akegp.rules.domain.ArchitectureDecisionRecord;
import akegp.rules.domain.ArchitectureElementType;
import akegp.rules.domain.ArchitectureRule;
import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import akegp.rules.domain.RuleSeverity;
import akegp.rules.repository.ReadableIdRepository;
import akegp.rules.repository.RuleRepository;
import akegp.rules.service.ADRNotFoundException;
import akegp.rules.service.ADRService;
import akegp.rules.service.ProjectNotFoundException;
import akegp.rules.service.ProjectRegistrationService;
import akegp.rules.service.RuleServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuleServiceImplTest {
  private final RuleRepository rules = mock(RuleRepository.class);
  private final ProjectRegistrationService projects = mock(ProjectRegistrationService.class);
  private final ADRService adrs = mock(ADRService.class);
  private final ReadableIdRepository readableIds = mock(ReadableIdRepository.class);
  private final RuleServiceImpl service = new RuleServiceImpl(rules, projects, adrs, readableIds);
  private final UUID projectId = UUID.randomUUID();
  private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

  @ParameterizedTest
  @EnumSource(ADRStatus.class)
  void listsRulesInRepositoryOrderWithOneBulkAdrLookup(ADRStatus status) {
    var firstAdr = adr("ADR-001", status);
    var secondAdr = adr("ADR-002", status);
    var newest = rule("R-003", firstAdr.id(), createdAt.plusSeconds(3));
    var middle = rule("R-002", secondAdr.id(), createdAt.plusSeconds(2));
    var oldest = rule("R-001", firstAdr.id(), createdAt.plusSeconds(1));
    when(rules.findByProject(projectId)).thenReturn(List.of(newest, middle, oldest));
    when(adrs.findAll(projectId, null, null)).thenReturn(List.of(secondAdr, firstAdr));

    assertEquals(
        List.of(response(newest, "ADR-001"), response(middle, "ADR-002"),
            response(oldest, "ADR-001")),
        service.findAll(projectId));

    verify(projects).findById(projectId);
    verify(rules).findByProject(projectId);
    verify(adrs).findAll(projectId, null, null);
    verifyNoMoreInteractions(projects, rules, adrs);
    verifyNoInteractions(readableIds);
  }

  @Test
  void emptyListingDoesNotLoadAdrs() {
    when(rules.findByProject(projectId)).thenReturn(List.of());

    assertEquals(List.of(), service.findAll(projectId));

    verify(projects).findById(projectId);
    verify(rules).findByProject(projectId);
    verifyNoInteractions(adrs, readableIds);
  }

  @Test
  void missingReferencedAdrPreservesNotFoundException() {
    UUID missingAdrId = UUID.randomUUID();
    var existingAdr = adr("ADR-001", ADRStatus.ACCEPTED);
    when(rules.findByProject(projectId)).thenReturn(List.of(
        rule("R-002", existingAdr.id(), createdAt.plusSeconds(2)),
        rule("R-001", missingAdrId, createdAt.plusSeconds(1))));
    when(adrs.findAll(projectId, null, null)).thenReturn(List.of(existingAdr));

    var exception = assertThrows(ADRNotFoundException.class, () -> service.findAll(projectId));

    assertEquals("Architecture decision record not found: " + missingAdrId, exception.getMessage());
    verify(adrs).findAll(projectId, null, null);
    verifyNoMoreInteractions(adrs);
  }

  @Test
  void missingProjectFailsBeforeLoadingRulesOrAdrs() {
    var failure = new ProjectNotFoundException(projectId);
    when(projects.findById(projectId)).thenThrow(failure);

    assertSame(failure, assertThrows(ProjectNotFoundException.class, () -> service.findAll(projectId)));

    verifyNoInteractions(rules, adrs, readableIds);
  }

  private ArchitectureDecisionRecord adr(String displayId, ADRStatus status) {
    return new ArchitectureDecisionRecord(UUID.randomUUID(), displayId, projectId,
        "Service boundaries", status, "Context", "Decision", "Consequences", 1,
        createdAt, createdAt);
  }

  private ArchitectureRule rule(String displayId, UUID adrId, Instant updatedAt) {
    return new ArchitectureRule(UUID.randomUUID(), displayId, projectId, adrId,
        "Orders", ArchitectureElementType.SERVICE, RelationshipType.CALLS,
        "Payments", ArchitectureElementType.SERVICE, RuleDecision.ALLOWED,
        "Services may call each other", RuleSeverity.HIGH, createdAt, updatedAt);
  }

  private static RuleResponse response(ArchitectureRule rule, String adrDisplayId) {
    return new RuleResponse(rule.id(), rule.ruleId(), rule.projectId(), rule.adrId(), adrDisplayId,
        rule.sourceElement(), rule.sourceElementType(), rule.relationshipType(), rule.targetElement(),
        rule.targetElementType(), rule.decision(), rule.description(), rule.severity(),
        rule.createdAt(), rule.updatedAt());
  }
}
