package akegp.rules;

import static org.junit.jupiter.api.Assertions.*;

import akegp.rules.api.*;
import akegp.rules.domain.*;
import akegp.rules.repository.*;
import akegp.rules.repository.ReadableIdRepository.RecordType;
import akegp.rules.service.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadableIdServiceTest {
  private final TestReadableIdRepository ids = new TestReadableIdRepository();
  private final ProjectRegistrationServiceImpl projects =
      new ProjectRegistrationServiceImpl(new InMemoryProjectRepository());
  private final ADRServiceImpl adrs = new ADRServiceImpl(new InMemoryADRRepository(), projects, ids);
  private final RuleServiceImpl rules =
      new RuleServiceImpl(new InMemoryRuleRepository(), projects, adrs, ids);

  UUID project() {
    return projects.register(new ProjectRegistrationRequest("Project", null,
        "https://example.com/" + UUID.randomUUID(), Set.of(ArchitectureStyle.MICROSERVICES))).id();
  }

  static CreateADRRequest adrRequest() {
    return new CreateADRRequest(null, "Use service boundaries", ADRStatus.PROPOSED,
        "Context with enough detail", "Decision with enough detail", "Consequences with enough detail");
  }

  static CreateRuleRequest ruleRequest(UUID adr) {
    return new CreateRuleRequest(null, adr, "Orders", ArchitectureElementType.SERVICE,
        RelationshipType.CALLS, "Payments", ArchitectureElementType.SERVICE,
        RuleDecision.ALLOWED, "Services may call each other", RuleSeverity.HIGH);
  }

  @Test
  void sequencesAreIndependentByProjectAndTypeAndDoNotReuseDeletedIds() {
    UUID project = project();
    var first = adrs.create(project, adrRequest());
    assertEquals("ADR-001", first.adrId());
    var second = adrs.create(project, adrRequest());
    assertEquals("ADR-002", second.adrId());
    assertNotEquals(first.id(), second.id());
    var rule = rules.create(project, ruleRequest(first.id()));
    assertEquals("R-001", rule.ruleId());
    var secondRule = rules.create(project, ruleRequest(first.id()));
    assertEquals("R-002", secondRule.ruleId());
    assertNotEquals(rule.id(), secondRule.id());
    rules.delete(project, secondRule.id());
    adrs.delete(project, second.id());
    assertEquals("ADR-003", adrs.create(project, adrRequest()).adrId());
    assertEquals("R-003", rules.create(project, ruleRequest(first.id())).ruleId());
    UUID other = project();
    var otherAdr = adrs.create(other, adrRequest());
    assertEquals("ADR-001", otherAdr.adrId());
    assertEquals("R-001", rules.create(other, ruleRequest(otherAdr.id())).ruleId());
    assertThrows(ADRNotFoundException.class, () -> adrs.findById(other, first.id()));
    assertThrows(RuleNotFoundException.class, () -> rules.findById(other, rule.id()));
  }

  @Test
  void updatesPreserveUuidReadableIdAndOwnershipWithoutAllocating() {
    UUID project = project();
    var adr = adrs.create(project, adrRequest());
    var updated = adrs.update(project, adr.id(), new UpdateADRRequest(null,
        "Updated title", ADRStatus.ACCEPTED, adr.context(), adr.decision(), adr.consequences(), 1));
    assertEquals(adr.id(), updated.id());
    assertEquals(adr.adrId(), updated.adrId());
    assertEquals(adr.projectId(), updated.projectId());
    assertEquals(2, updated.version());
    assertThrows(ReadableIdException.class, () -> adrs.update(project, adr.id(),
        new UpdateADRRequest("ADR-999", updated.title(), updated.status(), updated.context(),
            updated.decision(), updated.consequences(), 2)));
    var rule = rules.create(project, ruleRequest(adr.id()));
    var edited = rules.update(project, rule.id(), new UpdateRuleRequest(null, adr.id(),
        "UpdatedSource", rule.sourceElementType(), rule.relationshipType(), rule.targetElement(),
        rule.targetElementType(), rule.decision(), rule.description(), rule.severity()));
    assertEquals(rule.id(), edited.id());
    assertEquals(rule.ruleId(), edited.ruleId());
    assertEquals(rule.projectId(), edited.projectId());
    assertEquals(adr.id(), edited.adrId());
    assertThrows(ReadableIdException.class, () -> rules.update(project, rule.id(),
        new UpdateRuleRequest("R-999", adr.id(), rule.sourceElement(), rule.sourceElementType(),
            rule.relationshipType(), rule.targetElement(), rule.targetElementType(), rule.decision(),
            rule.description(), rule.severity())));
    assertEquals("ADR-002", adrs.create(project, adrRequest()).adrId());
    assertEquals("R-002", rules.create(project, ruleRequest(adr.id())).ruleId());
  }

  @Test
  void clientCannotOverrideGenerationEvenWhenCallingServicesDirectly() {
    UUID project = project();
    var request = adrRequest();
    assertThrows(ReadableIdException.class, () -> adrs.create(project, new CreateADRRequest(
        "ADR-999", request.title(), request.status(), request.context(), request.decision(), request.consequences())));
    var adr = adrs.create(project, request);
    assertEquals("ADR-001", adr.adrId());
    var rule = ruleRequest(adr.id());
    assertThrows(ReadableIdException.class, () -> rules.create(project, new CreateRuleRequest(
        "R-999", adr.id(), rule.sourceElement(), rule.sourceElementType(), rule.relationshipType(),
        rule.targetElement(), rule.targetElementType(), rule.decision(), rule.description(), rule.severity())));
    assertEquals("R-001", rules.create(project, rule).ruleId());
  }

  @Test
  void numberingContinuesBeyond999() {
    UUID project = project();
    ids.seed(project, RecordType.ADR, 998);
    ids.seed(project, RecordType.RULE, 998);
    var adr = adrs.create(project, adrRequest());
    assertEquals("ADR-999", adr.adrId());
    assertEquals("ADR-1000", adrs.create(project, adrRequest()).adrId());
    assertEquals("R-999", rules.create(project, ruleRequest(adr.id())).ruleId());
    assertEquals("R-1000", rules.create(project, ruleRequest(adr.id())).ruleId());
  }
}
