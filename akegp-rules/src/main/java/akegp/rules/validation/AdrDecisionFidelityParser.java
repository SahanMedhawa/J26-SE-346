package akegp.rules.validation;

import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Recognizes explicit modal decisions about dependencies, calls and direct database access.
 * This is a bounded grammar, not general natural-language understanding.
 */
public final class AdrDecisionFidelityParser {
  // Keep element names bounded: prose, alternatives and qualifiers are not identifiers.
  private static final String ELEMENT = "[\\p{L}_$][\\p{L}\\p{N}_$-]*(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$-]*)*";
  private static final String SOURCE_SEPARATOR = "(?:\\s*,\\s*(?:and\\s+)?|\\s+and\\s+)";
  private static final Pattern CLAUSE = Pattern.compile(
      "(?:(" + ELEMENT + "(?:" + SOURCE_SEPARATOR + ELEMENT + ")*)\\s+)?"
          + "(must\\s+not|must|may)\\s+"
          + "(depend\\s+on|call|directly\\s+access)\\s+(" + ELEMENT + ")",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern SOURCES = Pattern.compile(SOURCE_SEPARATOR, Pattern.CASE_INSENSITIVE);
  private static final Pattern CONJUNCTION = Pattern.compile("\\s*,?\\s+and\\s+", Pattern.CASE_INSENSITIVE);

  private AdrDecisionFidelityParser() {}

  public static List<InterpretedDecision> parse(String decisionText) {
    if (decisionText == null || decisionText.isBlank()) {
      return List.of();
    }
    List<InterpretedDecision> decisions = new ArrayList<>();
    // A line wrap is whitespace, not a sentence boundary: it may introduce a condition.
    for (String statement : decisionText.strip().split("(?<=[.!?])\\s+|;")) {
      decisions.addAll(parseStatement(statement));
    }
    return List.copyOf(decisions);
  }

  private static List<InterpretedDecision> parseStatement(String statement) {
    String text = statement.strip();
    if (text.endsWith(".")) text = text.substring(0, text.length() - 1).stripTrailing();
    List<InterpretedDecision> constraints = new ArrayList<>();
    String[] sources = null;
    int offset = 0;
    while (offset < text.length()) {
      var clause = CLAUSE.matcher(text).region(offset, text.length());
      if (!clause.lookingAt()) return List.of();
      if (clause.group(1) != null) sources = SOURCES.split(clause.group(1));
      if (sources == null) return List.of();
      String modal = clause.group(2).replaceAll("\\s+", " ");
      RuleDecision decision = modal.equalsIgnoreCase("must not") ? RuleDecision.FORBIDDEN
          : modal.equalsIgnoreCase("must") ? RuleDecision.REQUIRED : RuleDecision.ALLOWED;
      String verb = clause.group(3).replaceAll("\\s+", " ");
      RelationshipType relationship = verb.equalsIgnoreCase("depend on") ? RelationshipType.DEPENDS_ON
          : verb.equalsIgnoreCase("call") ? RelationshipType.CALLS : RelationshipType.DIRECT_DATABASE_ACCESS;
      for (String source : sources) {
        constraints.add(new InterpretedDecision(source, relationship, clause.group(4), decision));
      }
      offset = clause.end();
      if (offset == text.length()) return List.copyOf(constraints);
      var conjunction = CONJUNCTION.matcher(text).region(offset, text.length());
      if (!conjunction.lookingAt()) return List.of();
      offset = conjunction.end();
      if (offset == text.length()) return List.of();
    }
    // Reject the entire conjunction group if any clause is qualified or unsupported.
    return List.of();
  }

  public record InterpretedDecision(
      String sourceElement,
      RelationshipType relationshipType,
      String targetElement,
      RuleDecision decision) {}
}
