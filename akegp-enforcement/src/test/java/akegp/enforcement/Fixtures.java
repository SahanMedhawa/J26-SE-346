package akegp.enforcement;

import akegp.enforcement.conformance.ConformanceScanner;
import akegp.enforcement.conformance.MockConformanceScanner;
import akegp.enforcement.conformance.ScanReport;
import akegp.enforcement.conformance.ScanRequest;
import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.github.GithubStatusClient;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.policy.ConfiguredProjectLinkResolver;
import akegp.enforcement.policy.ProjectLinkResolver;
import akegp.enforcement.repository.EnforcementRepository;
import akegp.enforcement.repository.InMemoryEnforcementRepository;
import akegp.enforcement.review.ReviewService;
import akegp.enforcement.service.ChangeClassifier;
import akegp.enforcement.service.EnforcementService;
import akegp.enforcement.service.RunStatusPublisher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Shared test helpers: fake GitHub, scriptable Component 2, and a ready-wired module. */
public final class Fixtures {
  private Fixtures() {}

  public record SentStatus(String repo, String sha, StatusState state, String description) {}

  public static final class RecordingStatusClient implements GithubStatusClient {
    public final List<SentStatus> sent = new ArrayList<>();
    public RuntimeException failWith;

    @Override
    public void setStatus(String repo, String sha, StatusState state, String description) {
      if (failWith != null) throw failWith;
      sent.add(new SentStatus(repo, sha, state, description));
    }

    public SentStatus last() { return sent.get(sent.size() - 1); }
  }

  public static final class ScriptedScanner implements ConformanceScanner {
    public final List<ScanRequest> seen = new ArrayList<>();
    public Function<ScanRequest, ScanReport> behaviour = new MockConformanceScanner()::scan;

    @Override
    public ScanReport scan(ScanRequest request) {
      seen.add(request);
      return behaviour.apply(request);
    }
  }

  /** A wired module: in-memory store, recording GitHub, scriptable scanner. Repo "o/r" is linked to "proj-1". */
  public static final class Kit {
    public final EnforcementRepository repository = new InMemoryEnforcementRepository();
    public final RecordingStatusClient github = new RecordingStatusClient();
    public final ScriptedScanner scanner = new ScriptedScanner();
    public final EnforcementService enforcement;
    public final ReviewService review;

    public Kit(ProjectLinkResolver links) {
      RunStatusPublisher status = new RunStatusPublisher(github, repository);
      enforcement = new EnforcementService(repository, new ChangeClassifier(), links, scanner, status);
      review = new ReviewService(repository, scanner, status);
    }

    public static Kit linked() { return new Kit(new ConfiguredProjectLinkResolver(Map.of("o/r", "proj-1"), null)); }

    public static Kit unlinked() { return new Kit(new ConfiguredProjectLinkResolver(Map.of(), null)); }
  }

  public static PipelineEvent event(int pr, String... files) {
    List<FileChange> changes = Arrays.stream(files)
        .map(f -> new FileChange(f, "modified", null, 1, 1, false, null, null, null)).toList();
    return new PipelineEvent("o/r", "https://github.com/o/r", pr, "abc1234", "def5678", List.of(files), changes);
  }
}
