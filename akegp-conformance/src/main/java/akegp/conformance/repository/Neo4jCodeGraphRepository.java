package akegp.conformance.repository;

import akegp.conformance.domain.CodeGraph;
import akegp.conformance.domain.ConformanceScan;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Repository;

@Repository
public class Neo4jCodeGraphRepository {
  private final Driver driver;

  public Neo4jCodeGraphRepository(Driver driver) {
    this.driver = driver;
  }

  public ConformanceScan save(String repositoryId, String repositoryUrl, CodeGraph graph) {
    String scanId = UUID.randomUUID().toString();
    Instant scannedAt = Instant.now();
    try (Session session = driver.session()) {
      session.executeWrite(transaction -> {
        transaction.run("CREATE (s:ConformanceScan {scanId: $scanId, repositoryId: $repositoryId, repositoryUrl: $repositoryUrl, scannedAt: $scannedAt, fileCount: $fileCount, dependencyCount: $dependencyCount})",
            Values.parameters("scanId", scanId, "repositoryId", repositoryId, "repositoryUrl", repositoryUrl,
                "scannedAt", scannedAt.toString(), "fileCount", graph.files().size(), "dependencyCount", graph.dependencies().size())).consume();
        List<Map<String, Object>> files = graph.files().stream()
            .map(file -> Map.<String, Object>of("path", file.path(), "language", file.language(), "kind", file.kind()))
            .toList();
        transaction.run("UNWIND $files AS file CREATE (f:CodeFile {scanId: $scanId, path: file.path, language: file.language, kind: file.kind})",
            Values.parameters("scanId", scanId, "files", files)).consume();
        List<Map<String, Object>> dependencies = graph.dependencies().stream()
            .map(edge -> Map.<String, Object>of("source", edge.source(), "target", edge.target()))
            .toList();
        transaction.run("UNWIND $dependencies AS edge MATCH (source:CodeFile {scanId: $scanId, path: edge.source}) MATCH (target:CodeFile {scanId: $scanId, path: edge.target}) CREATE (source)-[:DEPENDS_ON]->(target)",
            Values.parameters("scanId", scanId, "dependencies", dependencies)).consume();
        return null;
      });
    }
    return new ConformanceScan(scanId, repositoryId, repositoryUrl, scannedAt, graph.files().size(), graph.dependencies().size());
  }

  public List<ConformanceScan> listScans() {
    try (Session session = driver.session()) {
      return session.executeRead(transaction -> transaction.run("MATCH (s:ConformanceScan) RETURN s.scanId AS scanId, s.repositoryId AS repositoryId, s.repositoryUrl AS repositoryUrl, s.scannedAt AS scannedAt, s.fileCount AS fileCount, s.dependencyCount AS dependencyCount ORDER BY s.scannedAt DESC")
          .list(this::toScan));
    }
  }

  public CodeGraph find(String scanId) {
    try (Session session = driver.session()) {
      return session.executeRead(transaction -> {
        List<CodeGraph.CodeFile> files = transaction.run("MATCH (f:CodeFile {scanId: $scanId}) RETURN f.path AS path, f.language AS language, coalesce(f.kind, 'Internal') AS kind ORDER BY path",
                Values.parameters("scanId", scanId))
            .list(record -> new CodeGraph.CodeFile(record.get("path").asString(), record.get("language").asString(), record.get("kind").asString()));
        List<CodeGraph.Dependency> dependencies = transaction.run("MATCH (source:CodeFile {scanId: $scanId})-[:DEPENDS_ON]->(target:CodeFile {scanId: $scanId}) RETURN source.path AS source, target.path AS target ORDER BY source, target",
                Values.parameters("scanId", scanId))
            .list(record -> new CodeGraph.Dependency(record.get("source").asString(), record.get("target").asString()));
        return new CodeGraph(files, dependencies);
      });
    }
  }

  private ConformanceScan toScan(org.neo4j.driver.Record record) {
    return new ConformanceScan(record.get("scanId").asString(), record.get("repositoryId").asString(),
        record.get("repositoryUrl").asString(), Instant.parse(record.get("scannedAt").asString()),
        record.get("fileCount").asInt(), record.get("dependencyCount").asInt());
  }
}