package akegp.rules.service;

import java.util.UUID;

public class ADRNotFoundException extends RuntimeException {
  public ADRNotFoundException(UUID id) {
    super("Architecture decision record not found: " + id);
  }
}
