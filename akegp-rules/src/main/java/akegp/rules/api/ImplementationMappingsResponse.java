package akegp.rules.api;

import java.util.List;
import java.util.UUID;

public record ImplementationMappingsResponse(
    UUID ruleUuid, List<String> sourceFiles, List<String> targetFiles) {}
