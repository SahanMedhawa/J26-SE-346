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
import akegp.shared.api.ConformanceGraphScanService;
import org.springframework.stereotype.Component;
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

  public boolean hasScan(String repositoryId) {
    return repository.findByRepositoryId(repositoryId).isPresent();
  }

  public CodeGraph find(String scanId) {
    return repository.find(scanId);
  }
}

@Component
class PullRequestCodeGraphScanner implements ConformanceGraphScanService {
  private final CodeGraphService graphs;

  PullRequestCodeGraphScanner(CodeGraphService graphs) { this.graphs = graphs; }

  @Override
  public void scanPullRequest(String projectId, String repositoryUrl, int prNumber, String commitSha) {
    String scanKey = projectId + ":pr-" + prNumber + ":" + commitSha;
    if (graphs.hasScan(scanKey)) return;
    String pinnedUrl = repositoryUrl.replaceAll("/+$", "") + "/tree/" + commitSha;
    try {
      graphs.importRepository(scanKey, pinnedUrl);
    } catch (IOException e) {
      throw new IllegalStateException("Could not build graph for PR #" + prNumber + " at " + commitSha, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Graph scan interrupted for PR #" + prNumber, e);
    }
  }
}
