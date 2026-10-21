package akegp.enforcement.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One pipeline run for one pull request commit. The "current state" of a PR is its latest run. */
public class PipelineRun {
  private long id;
  private Instant createdAt;
  private String repo;
  private String repoUrl;
  private int prNumber;
  private String commitSha;
  private String baseSha;
  private String projectId;
  private ChangeType classification;
  private RunState state = RunState.RECEIVED;
  private HoldReason holdReason;
  private List<String> changedFiles = new ArrayList<>();
  private List<FileChange> changes = new ArrayList<>();
  private List<Violation> violations = new ArrayList<>();
  private Decision decision;

  public static PipelineRun from(PipelineEvent e, ChangeType type, String projectId) {
    PipelineRun r = new PipelineRun();
    r.repo = e.repo(); r.repoUrl = e.repoUrl(); r.prNumber = e.prNumber();
    r.commitSha = e.commitSha(); r.baseSha = e.baseSha();
    r.changedFiles = new ArrayList<>(e.changedFiles());
    r.changes = new ArrayList<>(e.changes());
    r.classification = type; r.projectId = projectId;
    return r;
  }

  /** Independent copy, so a store cannot be changed by holding on to the object it returned. */
  public PipelineRun copy() {
    PipelineRun c = new PipelineRun();
    c.id = id; c.createdAt = createdAt; c.repo = repo; c.repoUrl = repoUrl; c.prNumber = prNumber;
    c.commitSha = commitSha; c.baseSha = baseSha; c.projectId = projectId; c.classification = classification;
    c.state = state; c.holdReason = holdReason; c.decision = decision;
    c.changedFiles = new ArrayList<>(changedFiles);
    c.changes = new ArrayList<>(changes);
    c.violations = new ArrayList<>(violations);
    return c;
  }

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public String getRepo() { return repo; }
  public void setRepo(String repo) { this.repo = repo; }
  public String getRepoUrl() { return repoUrl; }
  public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
  public int getPrNumber() { return prNumber; }
  public void setPrNumber(int prNumber) { this.prNumber = prNumber; }
  public String getCommitSha() { return commitSha; }
  public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
  public String getBaseSha() { return baseSha; }
  public void setBaseSha(String baseSha) { this.baseSha = baseSha; }
  public String getProjectId() { return projectId; }
  public void setProjectId(String projectId) { this.projectId = projectId; }
  public ChangeType getClassification() { return classification; }
  public void setClassification(ChangeType classification) { this.classification = classification; }
  public RunState getState() { return state; }
  public void setState(RunState state) { this.state = state; }
  public HoldReason getHoldReason() { return holdReason; }
  public void setHoldReason(HoldReason holdReason) { this.holdReason = holdReason; }
  public List<String> getChangedFiles() { return changedFiles; }
  public void setChangedFiles(List<String> changedFiles) { this.changedFiles = new ArrayList<>(changedFiles); }
  public List<FileChange> getChanges() { return changes; }
  public void setChanges(List<FileChange> changes) { this.changes = new ArrayList<>(changes); }
  public List<Violation> getViolations() { return violations; }
  public void setViolations(List<Violation> violations) { this.violations = new ArrayList<>(violations); }
  public Decision getDecision() { return decision; }
  public void setDecision(Decision decision) { this.decision = decision; }
}
