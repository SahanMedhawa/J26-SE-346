package akegp.rules.service;

import java.util.UUID;

public class RuleNotFoundException extends RuntimeException {
  public RuleNotFoundException(UUID id) {
    super("Architecture rule not found: " + id);
  }
}
