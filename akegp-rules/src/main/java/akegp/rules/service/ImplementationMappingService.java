package akegp.rules.service;

import akegp.rules.api.ImplementationMappingsRequest;
import akegp.rules.api.ImplementationMappingsResponse;
import java.util.UUID;

public interface ImplementationMappingService {
  ImplementationMappingsResponse findByRule(UUID projectId, UUID ruleUuid);

  ImplementationMappingsResponse replace(
      UUID projectId, UUID ruleUuid, ImplementationMappingsRequest request);
}
