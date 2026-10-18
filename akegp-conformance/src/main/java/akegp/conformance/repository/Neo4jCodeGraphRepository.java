package akegp.conformance.repository;

import akegp.conformance.domain.CodeGraph;
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

  public void replace(String repositoryId, CodeGraph graph) {
    try (Session session = driver.session()) {
      session.executeWrite(transaction -> {
        transaction.run("MATCH (f:CodeFile {repositoryId: $repositoryId}) DETACH DELETE f",
            Values.parameters("repositoryId", repositoryId)).consume();
        List<Map<String, Object>> files = graph.files().stream()
            .map(file -> Map.<String, Object>of("path", file.path(), "language", file.language(), "kind", file.kind()))
            .toList();
        transaction.run("UNWIND $files AS file MERGE (f:CodeFile {repositoryId: $repositoryId, path: file.path}) SET f.language = file.language, f.kind = file.kind",
            Values.parameters("repositoryId", repositoryId, "files", files)).consume();
        List<Map<String, Object>> dependencies = graph.dependencies().stream()
            .map(edge -> Map.<String, Object>of("source", edge.source(), "target", edge.target()))
            .toList();
        transaction.run("UNWIND $dependencies AS edge MATCH (source:CodeFile {repositoryId: $repositoryId, path: edge.source}) MATCH (target:CodeFile {repositoryId: $repositoryId, path: edge.target}) MERGE (source)-[:DEPENDS_ON]->(target)",
            Values.parameters("repositoryId", repositoryId, "dependencies", dependencies)).consume();
        return null;
      });
    }
  }

  public CodeGraph find(String repositoryId) {
    try (Session session = driver.session()) {
      return session.executeRead(transaction -> {
        List<CodeGraph.CodeFile> files = transaction.run("MATCH (f:CodeFile {repositoryId: $repositoryId}) RETURN f.path AS path, f.language AS language, coalesce(f.kind, 'Internal') AS kind ORDER BY path",
                Values.parameters("repositoryId", repositoryId))
            .list(record -> new CodeGraph.CodeFile(record.get("path").asString(), record.get("language").asString(), record.get("kind").asString()));
        List<CodeGraph.Dependency> dependencies = transaction.run("MATCH (source:CodeFile {repositoryId: $repositoryId})-[:DEPENDS_ON]->(target:CodeFile {repositoryId: $repositoryId}) RETURN source.path AS source, target.path AS target ORDER BY source, target",
                Values.parameters("repositoryId", repositoryId))
            .list(record -> new CodeGraph.Dependency(record.get("source").asString(), record.get("target").asString()));
        return new CodeGraph(files, dependencies);
      });
    }
  }
}