package akegp.enforcement.repository;

import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.PipelineRun;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Default store: nothing to set up, but data is lost on restart. Use the JDBC store for anything you want to keep. */
public class InMemoryEnforcementRepository implements EnforcementRepository {
  /** Nothing to set up; exists so Spring can call init() on either store. */
  public void init() {}

  private final List<PipelineRun> runs = new ArrayList<>();
  private final List<AuditEntry> audit = new ArrayList<>();
  private long nextRunId = 1, nextAuditId = 1;

  @Override
  public synchronized PipelineRun createRun(PipelineRun run) {
    PipelineRun stored = run.copy();
    stored.setId(nextRunId++);
    stored.setCreatedAt(Instant.now());
    runs.add(stored);
    return stored.copy();
  }

  @Override
  public synchronized void updateRun(PipelineRun run) {
    for (int i = 0; i < runs.size(); i++) {
      if (runs.get(i).getId() == run.getId()) { runs.set(i, run.copy()); return; }
    }
    throw new IllegalStateException("run " + run.getId() + " not found");
  }

  @Override
  public synchronized Optional<PipelineRun> latestRun(String repo, int prNumber) {
    for (int i = runs.size() - 1; i >= 0; i--) {
      PipelineRun r = runs.get(i);
      if (r.getRepo().equals(repo) && r.getPrNumber() == prNumber) return Optional.of(r.copy());
    }
    return Optional.empty();
  }

  @Override
  public synchronized List<PipelineRun> listRuns(int limit) {
    List<PipelineRun> out = new ArrayList<>();
    for (int i = runs.size() - 1; i >= 0 && out.size() < limit; i--) out.add(runs.get(i).copy());
    return out;
  }

  @Override
  public synchronized AuditEntry log(String type, String repo, int prNumber, String detail) {
    AuditEntry e = new AuditEntry(nextAuditId++, Instant.now(), type, repo, prNumber, detail);
    audit.add(e);
    return e;
  }

  @Override
  public synchronized List<AuditEntry> auditLog() { return Collections.unmodifiableList(new ArrayList<>(audit)); }

  @Override
  public synchronized void reset() { runs.clear(); audit.clear(); nextRunId = 1; nextAuditId = 1; }
}