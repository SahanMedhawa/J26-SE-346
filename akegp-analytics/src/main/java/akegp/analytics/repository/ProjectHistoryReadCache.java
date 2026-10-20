package akegp.analytics.repository;

import akegp.analytics.domain.AnalyticsRun;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import java.util.function.Supplier;

/** Reuses immutable snapshots only while the project's stored run IDs are unchanged. */
public final class ProjectHistoryReadCache implements AnalyticsReadRepository {
  private final Supplier<DataSource> database;
  private final AnalyticsReadRepository delegate;
  private final Map<UUID, Entry> projects = new ConcurrentHashMap<>();
  private static final class Entry {
    Set<UUID> revision;
    List<AnalyticsRun> runs;
  }
  public ProjectHistoryReadCache(DataSource database, AnalyticsReadRepository delegate) {
    this(() -> database, delegate);
  }
  public ProjectHistoryReadCache(Supplier<DataSource> database, AnalyticsReadRepository delegate) {
    this.database = database; this.delegate = delegate;
  }
  @Override public List<AnalyticsRun> readProject(UUID project) {
    Set<UUID> revision = revision(project);
    if (!projects.containsKey(project) && projects.size() >= 32)
      projects.keySet().stream().findFirst().ifPresent(projects::remove);
    Entry entry = projects.computeIfAbsent(project, ignored -> new Entry());
    synchronized (entry) {
      if (revision.equals(entry.revision)) return entry.runs;
      List<AnalyticsRun> runs = List.copyOf(delegate.readProject(project));
      entry.runs = runs;
      entry.revision = Set.copyOf(runs.stream().map(AnalyticsRun::id).toList());
      return runs;
    }
  }
  private Set<UUID> revision(UUID project) {
    try (var c = database.get().getConnection(); var q = c.prepareStatement("""
        SELECT r.id FROM analytics_private.runs r
        JOIN analytics_private.streams s USING(stream_key) WHERE s.project_id=?
        """)) {
      q.setObject(1, project);
      try (var rs = q.executeQuery()) {
        Set<UUID> ids = new HashSet<>();
        while (rs.next()) ids.add(rs.getObject(1, UUID.class));
        return ids;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Analytics revision read failed (SQL state " + e.getSQLState() + ")");
    }
  }
}
