package akegp.rules.service;

import akegp.rules.api.RepositoryFilesResponse;
import akegp.rules.github.GitHubRepository;
import akegp.rules.github.GitHubRepositoryClient;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RepositoryBrowsingService {
  private final ProjectRegistrationService projects;
  private final GitHubRepositoryClient github;

  public RepositoryBrowsingService(ProjectRegistrationService projects, GitHubRepositoryClient github) {
    this.projects = projects;
    this.github = github;
  }

  public RepositoryFilesResponse files(UUID projectId) {
    var project = projects.findById(projectId);
    var tree = github.files(GitHubRepository.parse(project.repositoryUrl()));
    return new RepositoryFilesResponse(project.id(), project.repositoryUrl(), tree.branch(), tree.files());
  }
}
