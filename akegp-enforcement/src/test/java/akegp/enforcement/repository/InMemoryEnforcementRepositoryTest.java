package akegp.enforcement.repository;

import static akegp.enforcement.Fixtures.event;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akegp.enforcement.domain.ChangeType;
import akegp.enforcement.domain.PipelineRun;
import akegp.enforcement.domain.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEnforcementRepositoryTest {

  private static PipelineRun run(int pr) { return PipelineRun.from(event(pr, "src/A.java"), ChangeType.SOURCE_CODE, "p"); }

  @Test void assignsIdsAndCreationTime() {
    var repo = new InMemoryEnforcementRepository();
    PipelineRun a = repo.createRun(run(1)), b = repo.createRun(run(2));
    assertEquals(1L, a.getId());
    assertEquals(2L, b.getId());
    assertTrue(a.getCreatedAt() != null);
  }

  @Test void storedRunCannotBeChangedByHoldingOnToIt() {
    var repo = new InMemoryEnforcementRepository();
    PipelineRun a = repo.createRun(run(1));
    a.setState(RunState.REJECTED);                              // not saved with updateRun
    assertEquals(RunState.RECEIVED, repo.latestRun("o/r", 1).orElseThrow().getState());
    repo.updateRun(a);
    assertEquals(RunState.REJECTED, repo.latestRun("o/r", 1).orElseThrow().getState());
  }

  @Test void latestRunAndLatestPerPr() {
    var repo = new InMemoryEnforcementRepository();
    repo.createRun(run(1));
    PipelineRun second = repo.createRun(run(1));
    repo.createRun(run(2));
    assertEquals(second.getId(), repo.latestRun("o/r", 1).orElseThrow().getId());
    assertTrue(repo.latestRun("o/r", 99).isEmpty());
    List<PipelineRun> latest = repo.latestPerPr();
    assertEquals(2, latest.size());
    assertEquals(3L, latest.get(0).getId());                    // newest first
  }

  @Test void resetClearsEverything() {
    var repo = new InMemoryEnforcementRepository();
    repo.createRun(run(1));
    repo.log("x", "o/r", 1, "d");
    repo.reset();
    assertEquals(0, repo.listRuns(10).size());
    assertEquals(0, repo.auditLog().size());
    assertEquals(1L, repo.createRun(run(1)).getId());
  }
}
