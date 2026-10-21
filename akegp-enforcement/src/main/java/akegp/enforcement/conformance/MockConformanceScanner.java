package akegp.enforcement.conformance;

import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.Violation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Stand-in for Component 2 until it is connected. It applies a set of simple rules (see RuleCatalog) for the
 * architecture styles of the project, to the ADDED LINES of the changed files (for example "a controller must not
 * import the repository layer"). It does not build a dependency graph and it does not read Component 1's rules; the
 * real scanner replaces it through the same ConformanceScanner port.
 *
 * If the pipeline did not send the added lines (INCLUDE_LINES is off, or no "changes" at all), the code cannot be read,
 * so a controller file is flagged by its path alone (only the rule marked flagWhenUnreadable does this, once per file).
 * That is the cautious answer, and the violation message says so. The other rules cannot be checked without the code.
 *
 * Note: FileChange turns a missing line list into an empty one, so "no lines" cannot be told from "lines sent, none
 * added" by looking at the list alone. {@link #lineDetailMissing} uses the counts and the status to decide.
 */
public class MockConformanceScanner implements ConformanceScanner {
  private final List<ArchitectureRule> rules;
  private final Function<String, Set<String>> stylesOfProject;

  /** Both architecture styles for every project, until the style is read from Component 1. */
  public MockConformanceScanner() { this(RuleCatalog.all()); }

  public MockConformanceScanner(List<ArchitectureRule> rules) { this(rules, projectId -> RuleCatalog.ALL_STYLES); }

  /** @param stylesOfProject the architecture styles a project was registered with (LAYERED, MICROSERVICES) */
  public MockConformanceScanner(List<ArchitectureRule> rules, Function<String, Set<String>> stylesOfProject) {
    this.rules = List.copyOf(rules);
    this.stylesOfProject = stylesOfProject;
  }

  @Override
  public ScanReport scan(ScanRequest request) {
    Map<String, FileChange> byPath = new HashMap<>();
    if (request.changes() != null) for (FileChange c : request.changes()) byPath.put(c.path(), c);

    Set<String> styles = stylesOfProject.apply(request.projectId());
    if (styles == null || styles.isEmpty()) styles = RuleCatalog.ALL_STYLES;

    List<Violation> violations = new ArrayList<>();
    for (String file : request.changedFiles()) {
      boolean flaggedByPath = false; // at most one "could not read the code" violation per file
      for (ArchitectureRule rule : rules) {
        if (!styles.contains(rule.style()) || !rule.appliesTo(file)) continue;
        FileChange change = byPath.get(file);
        if (change == null || lineDetailMissing(change)) {
          if (rule.flagWhenUnreadable() && !flaggedByPath) {
            violations.add(new Violation(rule.ruleId(), rule.severity(), file, 0,
                rule.message() + " (line detail not sent, flagged by file path)", "new"));
            flaggedByPath = true;
          }
          continue;
        }
        for (LineChange line : change.addedLines()) {
          if (rule.isViolatedBy(line.text())) {
            violations.add(new Violation(rule.ruleId(), rule.severity(), file, line.line(),
                rule.message() + ": " + line.text().trim(), "new"));
          }
        }
      }
    }
    return new ScanReport(violations);
  }

  /**
   * True when this file may have gained code that we cannot see because the pipeline sent no lines for it.
   * Cases that are NOT missing detail: deleted or binary files, a pure deletion (nothing was added, so no import can
   * have been introduced) and a pure rename or copy (the content did not change).
   */
  static boolean lineDetailMissing(FileChange c) {
    if (c.binary() || "deleted".equals(c.status())) return false;
    if (!c.addedLines().isEmpty()) return false;                        // lines were sent and there are added lines
    if (c.additions() == 0 && c.deletions() > 0) return false;          // only removed code
    boolean moved = "renamed".equals(c.status()) || "copied".equals(c.status());
    if (moved && c.additions() == 0 && c.deletions() == 0) return false; // no content change
    return true;                                                         // added/modified text, but no added lines
  }
}