package akegp.enforcement.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/** Mandatory structured reason when a reviewer approves an exception (so overrides can be analysed later). */
public enum Justification {
  RULE_NOT_APPLICABLE("rule-not-applicable-in-context"),
  KNOWN_TECHNICAL_CONSTRAINT("known-technical-constraint"),
  TEMPORARY_WITH_FOLLOW_UP("temporary-with-follow-up-ticket"),
  RULE_NEEDS_REVISION("rule-needs-revision");

  private final String wire;
  Justification(String wire) { this.wire = wire; }
  public String wire() { return wire; }

  public static Optional<Justification> fromWire(String value) {
    for (Justification j : values()) if (j.wire.equals(value)) return Optional.of(j);
    return Optional.empty();
  }

  public static String allowedValues() {
    return Arrays.stream(values()).map(Justification::wire).collect(Collectors.joining(", "));
  }
}
