package akegp.conformance.service;

import akegp.conformance.domain.CodeGraph;
import akegp.conformance.domain.ConformanceScan;
import akegp.conformance.domain.ConformanceScanResult;
import akegp.conformance.extraction.RepositoryGraphExtractor;
import akegp.conformance.github.GitHubRepositorySource;
import akegp.conformance.repository.Neo4jCodeGraphRepository;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CodeGraphService {
  private final RepositoryGraphExtractor extractor;
  private final GitHubRepositorySource source;
  private final Neo4jCodeGraphRepository repository;

  public CodeGraphService(RepositoryGraphExtractor extractor, GitHubRepositorySource source, Neo4jCodeGraphRepository repository) {
    this.extractor = extractor;
    this.source = source;
    this.repository = repository;
  }

  public ConformanceScanResult importRepository(String repositoryId, String repositoryUrl) throws IOException, InterruptedException {
    return importRepository(repositoryId, repositoryUrl, null);
  }

  public ConformanceScanResult importRepository(String repositoryId, String repositoryUrl, Set<String> languages) throws IOException, InterruptedException {
    try (GitHubRepositorySource.RepositoryWorkspace workspace = source.download(repositoryUrl)) {
      CodeGraph graph = languages == null ? extractor.extract(workspace.path()) : extractor.extract(workspace.path(), languages);
      return new ConformanceScanResult(repository.save(repositoryId, repositoryUrl, graph), graph);
    }
  }

  public List<ConformanceScan> listScans() {
    return repository.listScans();
  }

  public CodeGraph find(String scanId) {
    return repository.find(scanId);
  }
}
