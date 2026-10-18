package akegp.rules.domain;

import java.util.UUID;

public record RuleImplementationMapping(
    UUID id, UUID ruleId, ImplementationMappingSide side, String filePath) {}
