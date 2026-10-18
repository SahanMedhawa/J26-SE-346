package akegp.rules.api;

import akegp.rules.domain.ArchitectureStyle;
import java.util.List;
import java.util.UUID;

public record RuleExportResponse(UUID projectId, String projectName, String repositoryUrl,
    ArchitectureStyle architectureStyle, int ruleCount, List<ExportedRuleResponse> rules) {
  public RuleExportResponse(UUID projectId, String projectName, String repositoryUrl,
      ArchitectureStyle architectureStyle, List<ExportedRuleResponse> rules) {
    this(projectId, projectName, repositoryUrl, architectureStyle, rules.size(), List.copyOf(rules));
  }
}
