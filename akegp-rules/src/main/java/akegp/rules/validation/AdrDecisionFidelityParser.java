package akegp.rules.validation;

import akegp.rules.domain.RelationshipType;
import akegp.rules.domain.RuleDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes explicit ADR decision statements for PP1:
 * {@code <Source> must not directly access <Target>}.
 *
 * <p>Interpreted as relationship {@link RelationshipType#DIRECT_DATABASE_ACCESS} and
 * decision {@link RuleDecision#FORBIDDEN}. This is not general natural-language understanding.
 */
public final class AdrDecisionFidelityParser {
  // Keep element names bounded: prose, alternatives and qualifiers are not identifiers.
  private static final String ELEMENT = "[\\p{L}_$][\\p{L}\\p{N}_$-]*(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$-]*)*";
  private static final String SOURCE_SEPARATOR = "(?:\\s*,\\s*(?:and\\s+)?|\\s+and\\s+)";
  private static final Pattern PATTERN = Pattern.compile(
      "^\\s*(" + ELEMENT + "(?:" + SOURCE_SEPARATOR + ELEMENT + ")*)"
          + "\\s+must\\s+not\\s+directly\\s+access\\s+(" + ELEMENT + ")\\s*\\.?\\s*$",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern SOURCES = Pattern.compile(SOURCE_SEPARATOR, Pattern.CASE_INSENSITIVE);

  private AdrDecisionFidelityParser() {}

  public static List<InterpretedDecision> parse(String decisionText) {
    if (decisionText == null || decisionText.isBlank()) {
      return List.of();
    }
    List<InterpretedDecision> decisions = new ArrayList<>();
    // A line wrap is whitespace, not a sentence boundary: it may introduce a condition.
    for (String statement : decisionText.strip().split("(?<=[.!?])\\s+|;")) {
      Matcher matcher = PATTERN.matcher(statement);
      if (!matcher.matches()) {
        continue;
      }
      for (String source : SOURCES.split(matcher.group(1))) {
        decisions.add(new InterpretedDecision(
            source, RelationshipType.DIRECT_DATABASE_ACCESS, matcher.group(2), RuleDecision.FORBIDDEN));
      }
    }
    return List.copyOf(decisions);
  }

  public record InterpretedDecision(
      String sourceElement,
      RelationshipType relationshipType,
      String targetElement,
      RuleDecision decision) {}
}
