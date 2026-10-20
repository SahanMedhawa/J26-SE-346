package akegp.app;

import akegp.analytics.config.AnalyticsDatabase;
import akegp.analytics.ingestion.*;
import akegp.analytics.repository.JdbcSnapshotRepository;
import akegp.rules.service.RuleExportService;
import akegp.shared.analytics.AnalysisSnapshot;
import akegp.shared.analytics.AnalyticsIngestion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in server-to-server ingress. Mock scans and graph imports never publish as UPSTREAM. */
@RestController
public class RecordedSnapshotController {
  private final AnalyticsDatabase database;
  private final RuleExportService rules;
  private final Environment environment;
  public RecordedSnapshotController(AnalyticsDatabase database, RuleExportService rules, Environment environment) {
    this.database = database; this.rules = rules; this.environment = environment;
  }
  @PostMapping("/api/analytics/projects/{projectId}/snapshots")
  public AnalyticsIngestion.Receipt ingest(@PathVariable("projectId") UUID project,
      @RequestHeader(value="x-analytics-key", required=false) String key, @RequestBody AnalysisSnapshot input) {
    String configured = environment.getProperty("AKEGP_ANALYTICS_INGESTION_KEY", "");
    if (configured.isBlank() || key == null || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A configured analytics publisher key is required");
    if (!project.equals(input.projectId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot project must match route");
    var exported = rules.export(project);
    if (!ProjectAnalyticsController.repositoryKey(exported.repositoryUrl()).equals(input.repositoryId()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the project's normalized owner/repository identity");
    var catalog = new HashMap<String, akegp.rules.api.ExportedRuleResponse>();
    exported.rules().forEach(r -> catalog.put(r.ruleId(), r));
    if (catalog.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project has no eligible exported rules");
    if (input.violations() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit findings array required");
    for (var finding : input.violations()) {
      var rule = finding == null ? null : catalog.get(finding.ruleKey());
      if (rule == null || !rule.sourceElement().equals(finding.entityKey()) || !rule.sourceElementType().name().equals(finding.entityType())
          || finding.stableUpstreamId() != null)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Findings require an exported rule, mapped source entity and structural identity anchors");
      int expectedSeverity = switch (rule.severity()) { case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1; };
      if (finding.sourceKey() == null || rule.sourceFiles().stream().noneMatch(path -> finding.sourceKey().startsWith(path + "#"))
          || finding.targetKey() == null || rule.targetFiles().stream().noneMatch(path -> finding.targetKey().startsWith(path + "#"))
          || !rule.relationshipType().name().equals(finding.relation()) || finding.severity() != expectedSeverity)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finding anchors, relationship and severity must match the current rule export");
    }
    try {
      return new SnapshotIngestionService(new JdbcSnapshotRepository(database.dataSource()),
          new AdapterSource("conformance-publisher-v1", AdapterSource.Provenance.UPSTREAM, "", false),
          p -> { if (!p.equals(project)) throw new SecurityException(); }).ingest(input);
    } catch (IngestionException e) {
      throw new ResponseStatusException(e.code() == IngestionException.Code.INVALID ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT,
          "Snapshot rejected: " + e.code());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analytics storage unavailable");
    }
  }
}
