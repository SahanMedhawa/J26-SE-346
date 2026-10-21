package akegp.enforcement.repository;

import akegp.enforcement.domain.AuditEntry;
import akegp.enforcement.domain.PipelineRun;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Storage owned by the enforcement module. Other modules never read these tables; they go through the services. */
public interface EnforcementRepository {

  /** Stores a new run and returns it with its id and creation time filled in. */
  PipelineRun createRun(PipelineRun run);

  void updateRun(PipelineRun run);

  Optional<PipelineRun> latestRun(String repo, int prNumber);

  /** Newest first. */
  List<PipelineRun> listRuns(int limit);

  AuditEntry log(String type, String repo, int prNumber, String detail);

  List<AuditEntry> auditLog();

  /** Test helper: removes everything. */
  void reset();

  /** The latest run of every pull request (what the review queue shows). */
  default List<PipelineRun> latestPerPr() {
    Set<String> seen = new HashSet<>();
    List<PipelineRun> out = new ArrayList<>();
    for (PipelineRun r : listRuns(500)) if (seen.add(r.getRepo() + "#" + r.getPrNumber())) out.add(r);
    return out;
  }

  /** One run by its id (used by the shared EnforcementDecisionService). */
  default Optional<PipelineRun> findRun(long id) {
    return listRuns(5000).stream().filter(r -> r.getId() == id).findFirst();
  }
}