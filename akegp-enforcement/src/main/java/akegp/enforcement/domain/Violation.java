package akegp.enforcement.domain;

/** One architecture violation reported by Component 2 (conformance). */
public record Violation(String ruleId, String severity, String file, Integer line, String message, String tag) {}
