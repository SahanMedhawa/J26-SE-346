package akegp.app;

import akegp.analytics.config.AnalyticsDatabase;
import akegp.analytics.ingestion.*;
import akegp.analytics.service.ProjectReadAuthorizer;
import akegp.conformance.service.CodeGraphService;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.rules.github.GitHubRepository;
import akegp.rules.service.ProjectRegistrationService;
import akegp.rules.service.RuleExportService;
import java.security.Principal;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Application composition joins module interfaces; analytics never reads sibling database tables. */
@RestController
@RequestMapping("/api/analytics")
public class ProjectAnalyticsController {
  private final ProjectRegistrationService projects;
  private final RuleExportService exports;
  private final CodeGraphService graphs;
  private final EnforcementRepository enforcement;
  private final AnalyticsDatabase database;
  private final ProjectReadAuthorizer access;
  private final Environment environment;
  public ProjectAnalyticsController(ProjectRegistrationService projects, RuleExportService exports,
      CodeGraphService graphs, EnforcementRepository enforcement, AnalyticsDatabase database,
      ProjectReadAuthorizer access, Environment environment) {
    this.projects = projects; this.exports = exports; this.graphs = graphs; this.enforcement = enforcement;
    this.database = database; this.access = access; this.environment = environment;
  }
  @GetMapping("/repository")
  public Map<String, Object> repository(Principal principal, @RequestParam("url") String url) {
    String key = repositoryKey(url);
    var matches = projects.findAll().stream().filter(p -> repositoryKey(p.repositoryUrl()).equals(key)).toList();
    if (matches.size() > 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Multiple projects use this repository; choose from the project library");
    if (matches.isEmpty()) return Map.of("registered", false, "repository", key,
        "message", "Register this repository to start collecting architecture history. A URL alone does not provide historical conformance findings.");
    var project = matches.getFirst();
    access.requireRead(principal, project.id());
    return Map.of("registered", true, "project", project, "repository", key);
  }
  @GetMapping("/projects/{projectId}/context")
  public Map<String, Object> context(Principal principal, @PathVariable("projectId") UUID projectId) {
    access.requireRead(principal, projectId);
    var project = projects.findById(projectId);
    var exported = exports.export(projectId);
    String key = repositoryKey(project.repositoryUrl());
    var output = new LinkedHashMap<String, Object>();
    var warnings = new ArrayList<String>();
    output.put("project", project); output.put("ruleExport", exported);
    output.put("demoEnabled", demoEnabled());
    if (!exported.rules().isEmpty()) {
      var data = dataset(exported);
      output.put("demoDataset", data.source().dataset());
      output.put("metadataFingerprint", data.metadataFingerprint());
    }
    try {
      output.put("scans", graphs.listScans().stream().filter(s -> s.repositoryId().equals(projectId.toString())
          || repositoryKey(s.repositoryUrl()).equals(key)).toList());
    } catch (RuntimeException e) { output.put("scans", List.of()); warnings.add("Repository scan history is temporarily unavailable."); }
    try {
      var rows = new ArrayList<Map<String, Object>>();
      for (var run : enforcement.listRuns(5000)) {
        if (!projectId.toString().equals(run.getProjectId()) && !key.equals(run.getRepo().toLowerCase(Locale.ROOT))) continue;
        var row = new LinkedHashMap<String, Object>();
        row.put("runId", run.getId()); row.put("at", run.getCreatedAt()); row.put("commit", run.getCommitSha());
        row.put("prNumber", run.getPrNumber()); row.put("state", run.getState().wire());
        row.put("classification", run.getClassification().label()); row.put("violations", run.getViolations().size());
        row.put("projectMappingMatches", projectId.toString().equals(run.getProjectId())); row.put("decision", run.getDecision());
        rows.add(row);
      }
      output.put("governance", rows);
      if (rows.stream().anyMatch(r -> Boolean.FALSE.equals(r.get("projectMappingMatches"))))
        warnings.add("Some pipeline records match the repository URL but carry a legacy project ID. They are context only, not full analytics snapshots.");
    } catch (RuntimeException e) { output.put("governance", List.of()); warnings.add("Pipeline history is temporarily unavailable."); }
    warnings.add("Repository graph scans and documentation-only pipeline passes are not complete conformance snapshots.");
    output.put("warnings", warnings);
    return output;
  }
  @PostMapping("/projects/{projectId}/demo-history")
  public Map<String, Object> importDemo(Principal principal, @PathVariable("projectId") UUID projectId) {
    access.requireRead(principal, projectId);
    if (!demoEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Demo import requires explicitly enabled development access and demo mode");
    var data = dataset(exports.export(projectId));
    var normalizer = new SnapshotNormalizer();
    var receipts = new akegp.analytics.repository.JdbcDemoDatasetImporter(database.dataSource()).append(
        data.snapshots().stream().map(snapshot -> normalizer.normalize(snapshot, data.source())).toList());
    int replays = (int) receipts.stream().filter(r -> r.replay()).count();
    return Map.of("dataset", data.source().dataset(), "runs", data.snapshots().size(), "replays", replays,
        "metadataFingerprint", data.metadataFingerprint(), "label", "Synthetic history using current exported rules and file mappings. Dates, commits, findings, LOC and gate outcomes are constructed.");
  }
  @GetMapping("/projects/{projectId}/demo-manifest")
  public Map<String, Object> manifest(Principal principal, @PathVariable("projectId") UUID projectId) {
    access.requireRead(principal, projectId);
    var exported = exports.export(projectId);
    var data = dataset(exported);
    return Map.of("generator", ProjectDemoHistory.VERSION, "projectId", projectId, "dataset", data.source().dataset(),
        "metadataFingerprint", data.metadataFingerprint(), "rules", exported.rules(), "snapshots", data.snapshots(),
        "provenance", "DEMO", "limitations", "Current exported metadata; synthetic historical observations, dates, commits, LOC and outcomes. Not a replay of repository commits.");
  }
  private boolean demoEnabled() {
    return environment.getProperty("akegp.analytics.development-access", Boolean.class, false)
        && environment.getProperty("akegp.analytics.demo-enabled", Boolean.class, false);
  }
  private ProjectDemoHistory.Dataset dataset(akegp.rules.api.RuleExportResponse exported) {
    return ProjectDemoHistory.generate(exported.projectId(), repositoryKey(exported.repositoryUrl()), exported.rules().stream().map(r ->
        new ProjectDemoHistory.Rule(r.ruleId(), r.adrId(), r.sourceElement(), r.sourceElementType().name(),
            r.relationshipType().name(), r.targetElement(), r.decision().name(), switch(r.severity()) {
              case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1;
            }, r.sourceFiles(), r.targetFiles())).toList());
  }
  static String repositoryKey(String url) {
    var parsed = GitHubRepository.parse(url);
    return (parsed.owner() + "/" + parsed.name()).toLowerCase(Locale.ROOT);
  }
  @ExceptionHandler(SecurityException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
  Map<String, String> denied() { return Map.of("message", "Analytics access denied for this project."); }
  @ExceptionHandler(akegp.rules.service.RepositoryBrowsingException.class)
  org.springframework.http.ResponseEntity<Map<String, String>> invalidRepository(akegp.rules.service.RepositoryBrowsingException e) {
    return org.springframework.http.ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
  }
  @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, String> invalid(IllegalArgumentException e) { return Map.of("message", e.getMessage()); }
  @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  Map<String, String> unavailable() { return Map.of("message", "Analytics storage unavailable. Check database configuration and migrations."); }
  @ExceptionHandler(IngestionException.class) @ResponseStatus(HttpStatus.CONFLICT)
  Map<String, String> conflict(IngestionException e) { return Map.of("message", "Demo import could not complete: " + e.code() + ". Retry an unchanged import to resume safely."); }
}
