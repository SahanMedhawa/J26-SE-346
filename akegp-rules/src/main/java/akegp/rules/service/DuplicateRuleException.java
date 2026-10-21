package akegp.rules.service;

public class DuplicateRuleException extends RuntimeException {
  public DuplicateRuleException(String ruleId) {
    super(ruleId + " already exists in this project.");
  }
}
