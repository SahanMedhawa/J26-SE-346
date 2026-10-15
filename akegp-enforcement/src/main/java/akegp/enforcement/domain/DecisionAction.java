package akegp.enforcement.domain;

import java.util.Optional;

/** What a reviewer can do with a held pull request. */
public enum DecisionAction {
  APPLY_FIX("apply_fix"), REQUEST_CHANGES("request_changes"), APPROVE_EXCEPTION("approve_exception"), REJECT("reject");

  private final String wire;
  DecisionAction(String wire) { this.wire = wire; }
  public String wire() { return wire; }

  public static Optional<DecisionAction> fromWire(String value) {
    for (DecisionAction a : values()) if (a.wire.equals(value)) return Optional.of(a);
    return Optional.empty();
  }
}
