package akegp.analytics.ingestion;

import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalysisSnapshot.*;
import java.time.Instant;
import java.util.*;

/** Reproducible scenarios using exported metadata. Every observation, date, LOC and commit is synthetic. */
public final class ProjectDemoHistory {
  public static final String VERSION = "project-history-v2";
  public record Rule(String ruleId, String adrId, String source, String sourceType,
      String relation, String target, String decision, int severity,
      List<String> sourceFiles, List<String> targetFiles) {
    public Rule { sourceFiles = List.copyOf(sourceFiles); targetFiles = List.copyOf(targetFiles); }
  }
  public record Dataset(AdapterSource source, String metadataFingerprint, List<AnalysisSnapshot> snapshots) {}
  private ProjectDemoHistory() {}

  public static Dataset generate(UUID project, String repository, List<Rule> input) {
    if (input.isEmpty()) throw new IllegalArgumentException("Export at least one confirmed, mapped rule before creating demo history");
    var rules = input.stream().sorted(Comparator.comparing(Rule::ruleId)).toList();
    for (var r : rules) if (r.sourceFiles().isEmpty() || r.targetFiles().isEmpty()
        || r.severity() < 1 || r.severity() > 3) throw new IllegalArgumentException("Demo rules require both mapping sides and severity 1..3");
    String fingerprint = SnapshotNormalizer.hash(rules.toString());
    var adapter = new AdapterSource("project-demo", AdapterSource.Provenance.DEMO,
        VERSION + "-" + fingerprint.substring(0, 16), false);
    var scope = rules.stream().map(r -> SnapshotNormalizer.scopeEntity(r.sourceType(), r.source())).distinct().sorted().toList();
    var result = new ArrayList<AnalysisSnapshot>();
    for (int branch = 0; branch < 2; branch++) {
      int count = branch == 0 ? 84 : 12;
      for (int run = 1; run <= count; run++) {
        final int n = run;
        var status = branch == 0 && run == 24 ? AnalysisStatus.FAILED
            : branch == 0 && run == 48 ? AnalysisStatus.PARTIAL : AnalysisStatus.COMPLETE;
        var findings = new ArrayList<Finding>();
        int ruleIndex = -1;
        for (var rule : rules) {
          ruleIndex++;
          var files = rule.sourceFiles().stream().sorted().toList();
          for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
            String file = files.get(fileIndex);
            for (int site = 0; site < 2; site++) {
              // Named lifecycle intervals, not independent statistics. Each rule has a different remediation phase.
              int scenarioRun = branch == 0 ? n : 72 + n;
              boolean active = active(ruleIndex, fileIndex, site, scenarioRun);
              if (scenarioRun == 63 || scenarioRun == 64) active = false;
              if (!active || status == AnalysisStatus.FAILED || status == AnalysisStatus.PARTIAL && site == 1) continue;
            String kind = rule.decision().equals("REQUIRED") ? "MISSING_REQUIRED_RELATION" : "FORBIDDEN_RELATION";
            findings.add(new Finding(null, rule.ruleId(), rule.ruleId() + " · " + rule.source() + " → " + rule.target(),
                rule.sourceType(), rule.source(), rule.source(), kind,
                file + "#demo-site-" + site, rule.targetFiles().getFirst() + "#" + rule.target(), rule.relation(),
                rule.severity(), file + ":" + (18 + site * 23 + n % 7),
                kind.toLowerCase().replace('_', ' ') + ": " + rule.source() + " → " + rule.target() + "; " + rule.adrId()));
          }
        }
        }
        String branchRef = branch == 0 ? "refs/heads/main" : "refs/heads/demo/appointment-refactor";
        result.add(new AnalysisSnapshot(1, project, repository, branchRef,
            "demo-" + (branch == 0 ? "main" : "refactor") + "-" + String.format(Locale.ROOT, "%03d", run), run,
            Instant.parse("2026-07-28T09:00:00Z").plusSeconds((branch == 0 ? run - 1L : run + 63L) * 86400),
            "synthetic-" + SnapshotNormalizer.hash(branchRef + ":" + run).substring(0, 12),
            fingerprint + (branch == 0 && run >= 57 ? "-scope-review-2" : "-scope-review-1"),
            "synthetic-detector-v1", "export-severity-high3-medium2-low1-v1", VERSION, scope, status, ResultMode.SNAPSHOT,
            status == AnalysisStatus.FAILED ? "FAILED" : "SUCCESS",
            status != AnalysisStatus.COMPLETE ? "UNAVAILABLE" : findings.isEmpty() ? "PASSED" : run % 13 == 0 ? "EXCEPTION_APPROVED" : "CHANGES_REQUESTED",
            new ScopedLoc(5200 + run * 31L + branch * 170, scope), findings));
      }
    }
    return new Dataset(adapter, fingerprint, List.copyOf(result));
  }
  /** Scenario 1 reproduces the model's {A,B,C} → {A,C,D,E} transition at runs 83/84. */
  private static boolean active(int rule, int file, int site, int run) {
    return switch (rule % 4) {
      case 0 -> switch (file * 2 + site) {
        case 0 -> run <= 62 || run >= 65; // A: durable boundary violation
        case 1 -> between(run, 72, 83); // B: resolved in the latest run
        case 2 -> between(run, 76, 84); // C: persistent
        case 3, 4 -> run == 84; // D/E: introduced by the latest change
        default -> between(run, 5 + file, 21 + file) || between(run, 45, 55);
      };
      case 1 -> site == 0 ? between(run, 6, 33) || between(run, 65, 81) : between(run, 83, 84);
      case 2 -> site == 0 ? between(run, 58 + file, 74 + file % 5)
          : between(run, 11 + file, 20 + file) || between(run, 69, 73) || file == 0 && run >= 82;
      default -> site == 0 ? run >= 79 + file : run >= 80 + file; // Emerging admin hotspot
    };
  }
  private static boolean between(int run, int start, int end) { return run >= start && run <= end; }

}
