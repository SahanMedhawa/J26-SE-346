package akegp.enforcement.api;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import akegp.enforcement.Fixtures.Kit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunViewTest {
  @SuppressWarnings("unchecked")
  private static Map<String, Object> decisionOf(Map<String, Object> view) { return (Map<String, Object>) view.get("decision"); }

  @Test void noDecisionYet() {
    Kit kit = Kit.linked();
    var run = kit.enforcement.handle(event(1, "src/OrderController.java"));
    assertNull(RunView.of(run).get("decision"));
  }

  @Test void approvedExceptionShowsJustificationAndComment() {
    Kit kit = Kit.linked();
    kit.enforcement.handle(event(1, "src/OrderController.java"));
    var run = kit.review.decide("o/r", 1, "approve_exception", "alice", "known-technical-constraint", "legacy module");
    Map<String, Object> d = decisionOf(RunView.of(run));
    assertEquals("approve_exception", d.get("action"));
    assertEquals("alice", d.get("reviewer"));
    assertEquals("known-technical-constraint", d.get("justification"));
    assertEquals("legacy module", d.get("comment"));
  }

  @Test void rejectWithoutCommentOrJustificationHasNullsNotTheTextNull() {
    Kit kit = Kit.linked();
    kit.enforcement.handle(event(1, "src/OrderController.java"));
    var run = kit.review.decide("o/r", 1, "reject", "bob", null, null);
    Map<String, Object> d = decisionOf(RunView.of(run));
    assertEquals("reject", d.get("action"));
    assertNull(d.get("justification"));
    assertNull(d.get("comment"));
  }

  @Test void historyListsEveryScanOfOnePullRequestNewestFirst() {
    Kit kit = Kit.linked();
    var first = kit.enforcement.handle(event(1, "src/OrderController.java"));   // held
    kit.enforcement.handle(event(2, "README.md"));                              // another PR
    var second = kit.enforcement.handle(event(1, "src/OrderController.java"));  // PR 1 scanned again
    var history = RunView.history(kit.repository.listRuns(100), "o/r", 1);
    assertEquals(2, history.size());
    assertEquals(second.getId(), history.get(0).get("runId"));
    assertEquals(first.getId(), history.get(1).get("runId"));
    assertEquals(false, ((java.util.List<?>) history.get(1).get("violations")).isEmpty());
    assertEquals(first.getCreatedAt().toString(), history.get(1).get("at"));
    assertEquals(0, RunView.history(kit.repository.listRuns(100), "o/r", 99).size());
  }

  @Test void showsTheChangedFileNamesToExplainTheResult() {
    Kit kit = Kit.linked();
    var run = kit.enforcement.handle(event(1, "README.md", "docs/guide.md"));
    assertEquals(java.util.List.of("README.md", "docs/guide.md"), RunView.of(run).get("changedFiles"));
    var many = kit.enforcement.handle(event(2, java.util.stream.IntStream.range(0, 15).mapToObj(i -> "f" + i + ".md").toArray(String[]::new)));
    assertEquals(10, ((java.util.List<?>) RunView.of(many).get("changedFiles")).size());
    assertEquals(15, RunView.of(many).get("files"));
  }
}
