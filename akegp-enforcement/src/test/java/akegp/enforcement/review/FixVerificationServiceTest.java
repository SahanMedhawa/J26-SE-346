package akegp.enforcement.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.Fixtures.Kit;
import akegp.enforcement.domain.FileChange;
import akegp.enforcement.domain.HoldReason;
import akegp.enforcement.domain.LineChange;
import akegp.enforcement.domain.PipelineEvent;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import akegp.enforcement.github.PullRequestSnapshot;
import akegp.enforcement.github.PullRequestSource;
import akegp.enforcement.github.StatusState;
import akegp.enforcement.service.EnforcementException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FixVerificationServiceTest {
  private static final String CONTROLLER = "src/main/java/a/controller/AccountController.java";
  private Kit kit;
  private PullRequestSnapshot onGithub;
  private RuntimeException githubFails;
  private FixVerificationService service;
  private PipelineRun held;

  private static PullRequestSnapshot snapshot(String head, String importLine) {
    var change = new FileChange(CONTROLLER, "modified", null, 1, 0, false, List.of(new LineChange(3, importLine)), List.of(), null);
    return new PullRequestSnapshot("https://github.com/o/r", head, "base0", List.of(change));
  }

  @BeforeEach
  void setUp() {
    kit = Kit.linked();
    var bad = snapshot("aaaaaaa1", "import a.repository.AccountRepository;");
    PipelineEvent first = new PipelineEvent("o/r", "https://github.com/o/r", 1, bad.headSha(), "base0",
        List.of(CONTROLLER), bad.changes());
    held = kit.enforcement.handle(first);
    assertEquals(RunState.HELD, held.getState());          // the controller imports a repository: R-014
    onGithub = bad;
    PullRequestSource source = (repo, pr) -> { if (githubFails != null) throw githubFails; return onGithub; };
    service = new FixVerificationService(kit.repository, source, kit.enforcement);
  }

  @Test void noCommitSinceTheScanGivesANoNewCommitErrorAndStaysHeld() {
    var e = assertThrows(EnforcementException.class, () -> service.verify("o/r", 1, "kaushalya"));
    assertEquals(409, e.status());
    assertTrue(e.getMessage().startsWith("No new commit has been pushed since the scan"));
    PipelineRun latest = kit.repository.latestRun("o/r", 1).orElseThrow();
    assertEquals(held.getId(), latest.getId());
    assertEquals(RunState.HELD, latest.getState());
    assertEquals(StatusState.PENDING, kit.github.last().state());
  }

  @Test void aWrongFixThatThePipelineAlreadyScannedIsReportedAsNotWorkingWithoutANewRun() {
    onGithub = snapshot("ccccccc3", "import a.repository.CardRepository;");
    PipelineRun scannedByPipeline = kit.enforcement.handle(new PipelineEvent("o/r", "https://github.com/o/r", 1,
        "ccccccc3", "base0", List.of(CONTROLLER), onGithub.changes()));
    assertEquals(RunState.HELD, scannedByPipeline.getState());
    int runsBefore = kit.repository.listRuns(100).size();

    PipelineRun result = service.verify("o/r", 1, "kaushalya");

    assertEquals(RunState.HELD, result.getState());
    assertEquals("ccccccc3", result.getCommitSha());
    assertFalse(result.getViolations().isEmpty());
    assertEquals(runsBefore, kit.repository.listRuns(100).size());
    assertEquals(StatusState.PENDING, kit.github.last().state());
  }

  @Test void aHeldRunWithoutViolationsIsScannedAgainOnTheSameCommit() {
    // e.g. the scanner was unavailable: the same commit may be re-scanned
    var working = kit.scanner.behaviour;
    kit.scanner.behaviour = request -> { throw new IllegalStateException("scanner down"); };
    PipelineRun unavailable = kit.enforcement.handle(new PipelineEvent("o/r", "https://github.com/o/r", 2,
        "fffffff6", "base0", List.of(CONTROLLER), snapshot("fffffff6", "import a.service.AccountService;").changes()));
    assertEquals(HoldReason.SCAN_UNAVAILABLE, unavailable.getHoldReason());
    kit.scanner.behaviour = working;
    onGithub = snapshot("fffffff6", "import a.service.AccountService;");
    assertEquals(RunState.PASSED, service.verify("o/r", 2, "kaushalya").getState());
  }

  @Test void aRealFixPassesTheScanAndReleasesThePr() {
    onGithub = snapshot("bbbbbbb2", "import a.service.AccountService;");
    PipelineRun result = service.verify("o/r", 1, "kaushalya");
    assertEquals(RunState.PASSED, result.getState());
    assertTrue(result.getViolations().isEmpty());
    assertEquals("bbbbbbb2", result.getCommitSha());
    assertEquals(StatusState.SUCCESS, kit.github.last().state());
    assertEquals("bbbbbbb2", kit.github.last().sha());
    assertEquals(result.getId(), kit.repository.latestRun("o/r", 1).orElseThrow().getId());
  }

  @Test void aFixThatDidNotWorkIsHeldAgainWithTheRemainingViolations() {
    onGithub = snapshot("ccccccc3", "import a.repository.CardRepository;");
    PipelineRun result = service.verify("o/r", 1, "kaushalya");
    assertEquals(RunState.HELD, result.getState());
    assertEquals(HoldReason.VIOLATIONS, result.getHoldReason());
    assertFalse(result.getViolations().isEmpty());
    assertEquals(StatusState.PENDING, kit.github.last().state());
  }

  @Test void theScanUsesTheCodeFromGithubNotTheStoredCode() {
    onGithub = snapshot("ddddddd4", "import a.service.AccountService;");
    service.verify("o/r", 1, "kaushalya");
    var sent = kit.scanner.seen.get(kit.scanner.seen.size() - 1);
    assertEquals("ddddddd4", sent.commitSha());
    assertEquals("import a.service.AccountService;", sent.changes().get(0).addedLines().get(0).text());
  }

  @Test void githubTroubleLeavesThePrHeldAndExplainsWhy() {
    githubFails = new EnforcementException(502, "GitHub could not be reached");
    var e = assertThrows(EnforcementException.class, () -> service.verify("o/r", 1, "kaushalya"));
    assertEquals(502, e.status());
    assertEquals(RunState.HELD, kit.repository.latestRun("o/r", 1).orElseThrow().getState());
  }

  @Test void onlyAHeldPrCanBeVerifiedAndAReviewerIsRequired() {
    assertEquals(400, assertThrows(EnforcementException.class, () -> service.verify("o/r", 1, " ")).status());
    assertEquals(404, assertThrows(EnforcementException.class, () -> service.verify("o/r", 99, "k")).status());
    kit.review.decide("o/r", 1, "reject", "k", null, null);
    assertEquals(409, assertThrows(EnforcementException.class, () -> service.verify("o/r", 1, "k")).status());
  }

  @Test void theDecisionIsInTheAuditTrail() {
    onGithub = snapshot("eeeeeee5", "import a.service.AccountService;");
    service.verify("o/r", 1, "kaushalya");
    var entry = kit.repository.auditLog().stream().filter(a -> a.type().equals("decision")).findFirst().orElseThrow();
    assertTrue(entry.detail().contains("action=apply_fix"));
    assertTrue(entry.detail().contains("reviewer=kaushalya"));
    assertTrue(entry.detail().contains("resultingState=passed"));
  }

  @Test void aMissingCommitIsAlsoLogged() {
    assertThrows(EnforcementException.class, () -> service.verify("o/r", 1, "kaushalya"));
    assertTrue(kit.repository.auditLog().stream().anyMatch(a -> a.type().equals("fix_not_found")));
  }
}
