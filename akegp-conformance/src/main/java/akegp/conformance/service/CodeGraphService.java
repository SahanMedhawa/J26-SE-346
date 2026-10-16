package akegp.conformance.service;

import akegp.conformance.domain.CodeGraph;
import akegp.conformance.extraction.RepositoryGraphExtractor;
import akegp.conformance.github.GitHubRepositorySource;
import akegp.conformance.repository.Neo4jCodeGraphRepository;
import java.io.IOException;
import java.util.Set;
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

  public CodeGraph importRepository(String repositoryId, String repositoryUrl) throws IOException, InterruptedException {
    return importRepository(repositoryId, repositoryUrl, null);
  }

  public CodeGraph importRepository(String repositoryId, String repositoryUrl, Set<String> languages) throws IOException, InterruptedException {
    try (GitHubRepositorySource.RepositoryWorkspace workspace = source.download(repositoryUrl)) {
      CodeGraph graph = languages == null ? extractor.extract(workspace.path()) : extractor.extract(workspace.path(), languages);
      repository.replace(repositoryId, graph);
      return graph;
    }
  }

  public CodeGraph find(String repositoryId) {
    return repository.find(repositoryId);
  }
}
