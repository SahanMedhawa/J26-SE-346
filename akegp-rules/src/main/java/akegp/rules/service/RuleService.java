package akegp.rules.service;

import akegp.rules.api.CreateRuleRequest;
import akegp.rules.api.RuleResponse;
import akegp.rules.api.UpdateRuleRequest;
import java.util.List;
import java.util.UUID;

public interface RuleService {
  RuleResponse create(UUID projectId, CreateRuleRequest request);

  List<RuleResponse> findAll(UUID projectId);

  RuleResponse findById(UUID projectId, UUID ruleUuid);

  RuleResponse update(UUID projectId, UUID ruleUuid, UpdateRuleRequest request);

  void delete(UUID projectId, UUID ruleUuid);
}
